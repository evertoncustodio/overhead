package io.github.evertoncustodio.overhead.clouds

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import io.github.evertoncustodio.overhead.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the latest global cloud map from Live Cloud Maps (clouds.matteason.co.uk),
 * which builds one from EUMETSAT weather satellite data every 3 hours.
 *
 * Blocking: call it from a background thread.
 */
class CloudDownloader(private val context: Context) {

    enum class Result { UPDATED, UNCHANGED, FAILED }

    private val store = CloudStore(context)

    fun download(): Result {
        // On mobile data, fetch the smaller map: about 120 KB instead of 420 KB.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val width = if (connectivity.isActiveNetworkMetered) SMALL_WIDTH else LARGE_WIDTH
        val now = System.currentTimeMillis()

        val connection = URL(urlFor(width)).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "Overhead/${BuildConfig.VERSION_NAME} (Android live wallpaper)")
            // Only ask "has it changed?" when we already have a map at least this size.
            if (store.width >= width) store.etag?.let { connection.setRequestProperty("If-None-Match", it) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    store.markUnchanged(now)
                    Result.UNCHANGED
                }
                HttpURLConnection.HTTP_OK -> {
                    val temp = store.tempFile()
                    val received = connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val expected = connection.contentLengthLong
                    if ((expected >= 0 && received != expected) || !isValidMap(temp.path)) {
                        temp.delete()
                        return Result.FAILED
                    }
                    store.replace(temp, connection.getHeaderField("ETag"), width, now)
                    Result.UPDATED
                }
                else -> Result.FAILED
            }
        } catch (_: IOException) {
            Result.FAILED
        } finally {
            connection.disconnect()
        }
    }

    /** Checks the file is a readable image of the expected 2:1 shape before using it. */
    private fun isValidMap(path: String): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outWidth > 0 && bounds.outWidth == bounds.outHeight * 2
    }

    private fun urlFor(width: Int) = "https://clouds.matteason.co.uk/images/${width}x${width / 2}/clouds.jpg"

    private companion object {
        const val SMALL_WIDTH = 1024
        const val LARGE_WIDTH = 2048
        const val TIMEOUT_MILLIS = 30_000
    }
}
