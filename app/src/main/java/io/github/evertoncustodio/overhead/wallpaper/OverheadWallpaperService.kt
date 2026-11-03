package io.github.evertoncustodio.overhead.wallpaper

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import io.github.evertoncustodio.overhead.clouds.CloudStore
import io.github.evertoncustodio.overhead.clouds.CloudUpdateJob
import io.github.evertoncustodio.overhead.location.DeviceLocation
import io.github.evertoncustodio.overhead.location.LocationStore
import io.github.evertoncustodio.overhead.render.GlobeRenderer

/**
 * The live wallpaper. Android creates one [Engine] per place the wallpaper is shown
 * (home screen, lock screen, the preview in the picker).
 *
 * Each engine draws with OpenGL ES through a [GLSurfaceView] that is never added to a
 * window: it only borrows the engine's surface and gives us a ready-made GL thread.
 */
class OverheadWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = OverheadEngine()

    private inner class OverheadEngine : Engine() {

        private lateinit var glView: WallpaperGLSurfaceView
        private lateinit var renderer: GlobeRenderer
        private lateinit var store: LocationStore
        private lateinit var clouds: CloudStore
        private lateinit var zoom: ZoomStore
        private lateinit var sky: SkyStore
        private val handler = Handler(Looper.getMainLooper())

        // Globe size for the screen showing (the home and lock sizes are chosen in the settings,
        // and the unlock animation moves between them), and from the launcher (some launchers
        // zoom the wallpaper out when opening the app drawer or recents).
        private var screenZoom = 1f
        private var launcherZoom = 0f
        private var unlockAnimation: ValueAnimator? = null

        // Kept in a field: SharedPreferences only holds listeners weakly.
        private val locationChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            renderer.center = store.current().point
            renderer.showMarker = store.showMarker
            glView.requestRender()
        }

        // A new cloud map was saved: upload it on the GL thread, then redraw.
        private val cloudsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != CloudStore.MAP_VERSION_KEY) return@OnSharedPreferenceChangeListener
            glView.queueEvent { renderer.reloadClouds() }
            glView.requestRender()
        }

        // A new size was chosen in the settings: show it on whichever screen is showing now.
        private val zoomChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (unlockAnimation?.isRunning != true) setScreenZoom(currentScreenZoom())
        }

        private val skyChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            renderer.keepMilkyWayInView = sky.keepMilkyWayInView
            glView.requestRender()
        }

        // The sun moves a quarter of a degree per minute, so one frame a minute
        // keeps the day/night line current at almost no battery cost.
        private val redrawEveryMinute = object : Runnable {
            override fun run() {
                glView.requestRender()
                handler.postDelayed(this, REDRAW_INTERVAL_MILLIS)
            }
        }

        private val screenEvents = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    // Switch to the lock screen size while the screen is off...
                    Intent.ACTION_SCREEN_OFF -> {
                        unlockAnimation?.cancel()
                        setScreenZoom(zoom.lock)
                    }
                    // ...and fly to the home screen size when the phone is unlocked.
                    Intent.ACTION_USER_PRESENT -> animateUnlock()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            store = LocationStore(this@OverheadWallpaperService)
            clouds = CloudStore(this@OverheadWallpaperService)
            zoom = ZoomStore(this@OverheadWallpaperService)
            sky = SkyStore(this@OverheadWallpaperService)
            renderer = GlobeRenderer(assets, clouds.file).apply {
                center = store.current().point
                showMarker = store.showMarker
                keepMilkyWayInView = sky.keepMilkyWayInView
            }
            store.registerListener(locationChanged)
            clouds.registerListener(cloudsChanged)
            zoom.registerListener(zoomChanged)
            sky.registerListener(skyChanged)
            CloudUpdateJob.schedulePeriodic(this@OverheadWallpaperService)

            glView = WallpaperGLSurfaceView(this@OverheadWallpaperService).apply {
                setEGLContextClientVersion(3)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                // Only draw when asked to (see redrawEveryMinute), not continuously.
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }

            // The preview in the wallpaper picker doesn't need to react to locking.
            if (!isPreview) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                // Must be exported: USER_PRESENT comes from System UI, which counts as another app.
                // Both actions are protected broadcasts, so only the system can send them.
                ContextCompat.registerReceiver(
                    this@OverheadWallpaperService, screenEvents, filter, ContextCompat.RECEIVER_EXPORTED,
                )
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            handler.removeCallbacks(redrawEveryMinute)
            if (visible) {
                refreshLocation()
                if (clouds.isStale()) CloudUpdateJob.requestNow(this@OverheadWallpaperService)
                // Match the lock state in case a broadcast was missed (e.g. no lock screen at all).
                if (unlockAnimation?.isRunning != true) setScreenZoom(currentScreenZoom())
                glView.onResume()
                redrawEveryMinute.run()
            } else {
                glView.onPause()
            }
        }

        /** Home screen swipes: turn the globe a little, as if the camera drifted sideways. */
        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            // A step of 0 or 1 means the launcher has a single page (or doesn't report pages).
            val hasPages = xOffsetStep > 0f && xOffsetStep < 1f
            renderer.panDegrees = if (hasPages) (xOffset - 0.5) * 2 * MAX_PAN_DEGREES else 0.0
            glView.requestRender()
        }

        /** Called on Android 11+ when the launcher zooms the wallpaper (0 = normal, 1 = fully out). */
        override fun onZoomChanged(zoom: Float) {
            launcherZoom = zoom
            applyZoom()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(redrawEveryMinute)
            unlockAnimation?.cancel()
            store.unregisterListener(locationChanged)
            clouds.unregisterListener(cloudsChanged)
            zoom.unregisterListener(zoomChanged)
            sky.unregisterListener(skyChanged)
            if (!isPreview) unregisterReceiver(screenEvents)
            glView.release()
        }

        private fun animateUnlock() {
            unlockAnimation?.cancel()
            // With the same size on both screens there's nothing to animate.
            val home = zoom.home
            if (home == screenZoom) return
            unlockAnimation = ValueAnimator.ofFloat(screenZoom, home).apply {
                duration = UNLOCK_ANIMATION_MILLIS
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener { setScreenZoom(it.animatedValue as Float) }
                // Lighter frames while moving; the final still frame is drawn at full resolution.
                doOnStart { renderer.reducedResolution = true }
                doOnEnd {
                    renderer.reducedResolution = false
                    glView.requestRender()
                }
                start()
            }
        }

        private fun setScreenZoom(value: Float) {
            screenZoom = value
            applyZoom()
        }

        private fun currentScreenZoom(): Float = if (isLocked()) zoom.lock else zoom.home

        private fun applyZoom() {
            renderer.zoom = screenZoom * (1f - LAUNCHER_ZOOM_OUT * launcherZoom)
            glView.requestRender()
        }

        private fun isLocked(): Boolean =
            getSystemService(KeyguardManager::class.java).isKeyguardLocked

        /**
         * Picks up the phone's latest known location, if Android lets the wallpaper see it.
         * When it doesn't, the location saved by the settings screen is used instead.
         */
        private fun refreshLocation() {
            if (store.mode != LocationStore.Mode.DEVICE) return
            DeviceLocation.lastKnown(this@OverheadWallpaperService)?.let(store::updateDeviceLocation)
        }

        /** A GLSurfaceView that draws into the wallpaper's surface instead of its own. */
        private inner class WallpaperGLSurfaceView(context: Context) : GLSurfaceView(context) {

            override fun getHolder(): SurfaceHolder = surfaceHolder

            /** Stops the GL thread; normally the window system does this on detach. */
            fun release() = super.onDetachedFromWindow()
        }
    }

    private companion object {
        const val REDRAW_INTERVAL_MILLIS = 60_000L

        // How far the globe turns across all home screen pages, each way from the middle.
        const val MAX_PAN_DEGREES = 8.0

        const val UNLOCK_ANIMATION_MILLIS = 1200L

        // How much smaller the globe gets when the launcher fully zooms the wallpaper out.
        const val LAUNCHER_ZOOM_OUT = 0.08f
    }
}
