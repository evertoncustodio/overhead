package io.github.evertoncustodio.overhead.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import java.io.File

/** Small helpers around raw OpenGL ES calls. All of them must run on the GL thread. */
object GlUtils {

    /** Compiles the two shader files from assets and links them into a program. */
    fun createProgram(assets: AssetManager, vertexPath: String, fragmentPath: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, assets.readText(vertexPath))
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, assets.readText(fragmentPath))

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        // The program keeps its own reference; the shader objects are no longer needed.
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            error("Could not link $vertexPath + $fragmentPath:\n$log")
        }
        return program
    }

    /** Loads an image from assets into a mipmapped texture that wraps horizontally. */
    fun loadTexture(assets: AssetManager, path: String): Int {
        val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it) }
            ?: error("Could not decode $path")
        return loadTexture(bitmap)
    }

    /** Loads an image file, or returns 0 (no texture) if it's missing or unreadable. */
    fun loadTextureOrNone(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return 0
        return loadTexture(bitmap)
    }

    fun deleteTexture(texture: Int) {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    /** Uploads a bitmap into a mipmapped texture that wraps horizontally, then frees the bitmap. */
    private fun loadTexture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        // Longitude wraps around; latitude stops at the poles.
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Could not compile shader:\n$log")
        }
        return shader
    }

    private fun AssetManager.readText(path: String): String =
        open(path).bufferedReader().use { it.readText() }
}
