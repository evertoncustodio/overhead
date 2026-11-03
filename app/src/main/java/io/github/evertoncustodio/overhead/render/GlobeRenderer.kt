package io.github.evertoncustodio.overhead.render

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.io.File
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the globe. Runs on the GLSurfaceView's own thread: Android calls
 * [onSurfaceCreated] when a GL context is ready, [onSurfaceChanged] when the
 * size changes, and [onDrawFrame] whenever a new frame is requested.
 */
class GlobeRenderer(
    private val assets: AssetManager,
    /** The downloaded cloud map; the globe is drawn without clouds while it doesn't exist. */
    private val cloudFile: File,
) : GLSurfaceView.Renderer {

    /** The place shown at the center of the globe. */
    @Volatile
    var center: GeoPoint = DEFAULT_CENTER

    /** Extra turn of the globe in degrees of longitude, e.g. while swiping between home screens. */
    @Volatile
    var panDegrees: Double = 0.0

    /** Whether to mark the centered place with a dot. */
    @Volatile
    var showMarker: Boolean = true

    /**
     * Turn the sky so the Milky Way always runs behind the globe, instead of showing the real
     * sky, where it is only in view part of the day.
     */
    @Volatile
    var keepMilkyWayInView: Boolean = false

    /** Globe size relative to normal: below 1 shows it further away. */
    @Volatile
    var zoom: Float = 1f

    /**
     * Draw at reduced resolution and stretch it to the screen. Used during animations, where
     * the slight softness doesn't show, so slower phones can keep up a smooth frame rate.
     */
    @Volatile
    var reducedResolution: Boolean = false

    /** Where the current time comes from; replaceable for testing other times of day. */
    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    private var program = 0
    private var dayTexture = 0
    private var nightTexture = 0
    private var terrain = 0
    private var clouds = 0
    private var sky = 0
    private var width = 1
    private var height = 1

    // Off-screen image for reduced-resolution frames (created when first needed).
    private var offscreenFramebuffer = 0
    private var offscreenColor = 0
    private var offscreenWidth = 0
    private var offscreenHeight = 0

    private var uResolution = 0
    private var uCameraDistance = 0
    private var uGlobeScreenRadius = 0
    private var uViewToEarth = 0
    private var uSunDirection = 0
    private var uDayTexture = 0
    private var uNightTexture = 0
    private var uTerrain = 0
    private var uClouds = 0
    private var uHasClouds = 0
    private var uMarker = 0
    private var uViewToSky = 0
    private var uSky = 0
    private var uSkyLod = 0

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // A new GL context starts empty, so (re)create everything here.
        offscreenFramebuffer = 0
        offscreenColor = 0
        offscreenWidth = 0
        offscreenHeight = 0
        program = GlUtils.createProgram(assets, "shaders/globe.vert", "shaders/globe.frag")
        dayTexture = GlUtils.loadTexture(assets, "textures/earth_day.jpg")
        nightTexture = GlUtils.loadTexture(assets, "textures/earth_night.jpg")
        terrain = GlUtils.loadTexture(assets, "textures/terrain.png")
        clouds = GlUtils.loadTextureOrNone(cloudFile)
        sky = GlUtils.loadCubeMap(assets, SKY_FACES.map { "textures/sky_$it.jpg" })

        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
        uCameraDistance = GLES30.glGetUniformLocation(program, "uCameraDistance")
        uGlobeScreenRadius = GLES30.glGetUniformLocation(program, "uGlobeScreenRadius")
        uViewToEarth = GLES30.glGetUniformLocation(program, "uViewToEarth")
        uSunDirection = GLES30.glGetUniformLocation(program, "uSunDirection")
        uDayTexture = GLES30.glGetUniformLocation(program, "uDayTexture")
        uNightTexture = GLES30.glGetUniformLocation(program, "uNightTexture")
        uTerrain = GLES30.glGetUniformLocation(program, "uTerrain")
        uClouds = GLES30.glGetUniformLocation(program, "uClouds")
        uHasClouds = GLES30.glGetUniformLocation(program, "uHasClouds")
        uMarker = GLES30.glGetUniformLocation(program, "uMarker")
        uViewToSky = GLES30.glGetUniformLocation(program, "uViewToSky")
        uSky = GLES30.glGetUniformLocation(program, "uSky")
        uSkyLod = GLES30.glGetUniformLocation(program, "uSkyLod")
    }

    /** Replaces the cloud texture with the newest file. Must run on the GL thread (queueEvent). */
    fun reloadClouds() {
        val fresh = GlUtils.loadTextureOrNone(cloudFile)
        if (fresh == 0) return  // keep showing the old clouds rather than none
        GlUtils.deleteTexture(clouds)
        clouds = fresh
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun onDrawFrame(unused: GL10?) {
        if (!reducedResolution) {
            GLES30.glViewport(0, 0, width, height)
            drawGlobe(width, height)
            return
        }

        // Draw into the smaller off-screen image, then stretch it onto the screen.
        val smallWidth = (width * REDUCED_SCALE).toInt()
        val smallHeight = (height * REDUCED_SCALE).toInt()
        prepareOffscreen(smallWidth, smallHeight)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, offscreenFramebuffer)
        GLES30.glViewport(0, 0, smallWidth, smallHeight)
        drawGlobe(smallWidth, smallHeight)

        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(
            0, 0, smallWidth, smallHeight, 0, 0, width, height,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR,
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /** Makes sure the off-screen image exists at the given size. */
    private fun prepareOffscreen(width: Int, height: Int) {
        if (offscreenFramebuffer != 0 && width == offscreenWidth && height == offscreenHeight) return

        if (offscreenFramebuffer == 0) {
            val ids = IntArray(1)
            GLES30.glGenFramebuffers(1, ids, 0)
            offscreenFramebuffer = ids[0]
            GLES30.glGenRenderbuffers(1, ids, 0)
            offscreenColor = ids[0]
        }
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, offscreenColor)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, width, height)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, offscreenFramebuffer)
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, offscreenColor,
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        offscreenWidth = width
        offscreenHeight = height
    }

    /** Draws the globe into whatever is bound, which is [width] by [height] pixels. */
    private fun drawGlobe(width: Int, height: Int) {
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dayTexture)
        GLES30.glUniform1i(uDayTexture, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, nightTexture)
        GLES30.glUniform1i(uNightTexture, 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, terrain)
        GLES30.glUniform1i(uTerrain, 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, clouds)
        GLES30.glUniform1i(uClouds, 3)
        GLES30.glUniform1f(uHasClouds, if (clouds != 0) 1f else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, sky)
        GLES30.glUniform1i(uSky, 4)

        GLES30.glUniform2f(uResolution, width.toFloat(), height.toFloat())
        GLES30.glUniform1f(uCameraDistance, CAMERA_DISTANCE)
        val globeScreenRadius = GLOBE_SCREEN_RADIUS * zoom
        GLES30.glUniform1f(uGlobeScreenRadius, globeScreenRadius)
        val center = center  // read the volatile once, so the globe and the dot agree
        val shown = center.copy(longitude = center.longitude + panDegrees)
        val viewToEarth = shown.viewToEarthMatrix()
        GLES30.glUniformMatrix3fv(uViewToEarth, 1, false, viewToEarth, 0)
        val now = clock()
        val viewToSky = if (keepMilkyWayInView) {
            Sky.milkyWayViewToSky(viewToEarth, center.viewToEarthMatrix())
        } else {
            Sky.realViewToSky(viewToEarth, now)
        }
        GLES30.glUniformMatrix3fv(uViewToSky, 1, false, viewToSky, 0)
        GLES30.glUniform1f(uSkyLod, skyMipLevel(globeScreenRadius, width, height))
        val (markerX, markerY, markerVisibility) = Marker.screenPosition(center, shown, CAMERA_DISTANCE, globeScreenRadius)
        // The dot shrinks with the globe on the lock screen, but less, so it stays easy to see.
        val markerRadius = MARKER_RADIUS * sqrt(zoom)
        GLES30.glUniform4f(uMarker, markerX, markerY, markerRadius, if (showMarker) markerVisibility else 0f)
        GLES30.glUniform3fv(uSunDirection, 1, SunPosition.subsolarPoint(now).toUnitVector(), 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /**
     * The sky's mipmap level. Each pixel spans about the same angle of sky everywhere on the
     * screen, so one level per frame is enough, worked out here instead of for every pixel.
     */
    private fun skyMipLevel(globeScreenRadius: Float, width: Int, height: Int): Float {
        // The same field of view and pixel spread as the shader works out.
        val tanHalfFov = 1f / sqrt(CAMERA_DISTANCE * CAMERA_DISTANCE - 1f) / globeScreenRadius
        val pixelAngle = tanHalfFov / (0.5f * min(width, height))
        // At a face's center a cube map has (face size / 2) texels per radian.
        return max(log2(pixelAngle * SKY_FACE_SIZE / 2f), 0f)
    }

    private companion object {
        // Only shown until the wallpaper sets the saved location.
        val DEFAULT_CENTER = GeoPoint(latitude = 15.0, longitude = -30.0)

        // About 19,000 km above the surface: the whole planet fits, with some perspective.
        const val CAMERA_DISTANCE = 4f

        // The globe spans 90% of the screen width (on a portrait phone).
        const val GLOBE_SCREEN_RADIUS = 0.9f

        // Resolution of reduced frames, per side: 75% is about 56% of the pixels.
        const val REDUCED_SCALE = 0.75f

        // Radius of the dot marking the centered place, in screen units (half the shorter
        // side is 1): about 6 pixels on a 1080-pixel-wide phone.
        const val MARKER_RADIUS = 0.012f

        // The sky's cube map faces, in OpenGL's order, and their size in pixels.
        val SKY_FACES = listOf("px", "nx", "py", "ny", "pz", "nz")
        const val SKY_FACE_SIZE = 1024
    }
}
