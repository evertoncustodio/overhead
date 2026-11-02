package io.github.evertoncustodio.overhead.clouds

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File

/**
 * Keeps the most recent cloud map on disk, plus when it was downloaded. Shared by the
 * download job and the wallpaper (same process, so the wallpaper hears about new maps).
 */
class CloudStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The current cloud map, a greyscale equirectangular JPEG. May not exist yet. */
    val file = File(context.applicationContext.filesDir, "clouds/clouds.jpg")

    /** When the current map was downloaded, in epoch millis, or 0 if there is none. */
    val updatedAt: Long
        get() = if (file.exists()) prefs.getLong(KEY_UPDATED_AT, 0L) else 0L

    /** The server's version tag for the current map, used to skip unchanged downloads. */
    val etag: String?
        get() = if (file.exists()) prefs.getString(KEY_ETAG, null) else null

    /** Map size in pixels across (1024 or 2048), so a Wi-Fi download can upgrade a small one. */
    val width: Int
        get() = if (file.exists()) prefs.getInt(KEY_WIDTH, 0) else 0

    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - updatedAt > STALE_AFTER_MILLIS

    /** Moves a fully downloaded map into place, replacing the old one in a single step. */
    fun replace(downloaded: File, etag: String?, width: Int, downloadedAt: Long) {
        file.parentFile?.mkdirs()
        check(downloaded.renameTo(file)) { "Could not move $downloaded to $file" }
        prefs.edit {
            putLong(KEY_UPDATED_AT, downloadedAt)
            putString(KEY_ETAG, etag)
            putInt(KEY_WIDTH, width)
            putLong(MAP_VERSION_KEY, prefs.getLong(MAP_VERSION_KEY, 0L) + 1)
        }
    }

    /** The server said the map hasn't changed: it is still current as of [checkedAt]. */
    fun markUnchanged(checkedAt: Long) {
        prefs.edit { putLong(KEY_UPDATED_AT, checkedAt) }
    }

    /** A scratch file next to the real one, so a half-finished download never replaces it. */
    fun tempFile(): File = File(file.parentFile, "clouds.jpg.part").also { it.parentFile?.mkdirs() }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        /** Preference key that changes only when a new map file is saved; listen for it to reload. */
        const val MAP_VERSION_KEY = "version"

        /** New maps are published every 3 hours. */
        const val UPDATE_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L

        // A little longer than the update interval, so a slightly late job doesn't count as stale.
        private const val STALE_AFTER_MILLIS = UPDATE_INTERVAL_MILLIS + 30 * 60 * 1000L

        private const val PREFS_NAME = "clouds"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_ETAG = "etag"
        private const val KEY_WIDTH = "width"
    }
}
