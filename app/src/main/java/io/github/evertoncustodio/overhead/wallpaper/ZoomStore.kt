package io.github.evertoncustodio.overhead.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * How big the globe is on the home screen and on the lock screen, relative to the original
 * size (where it spans 90% of the screen width). Shared by the settings screen and the
 * wallpaper, which reacts to changes through a preference listener.
 */
class ZoomStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var home: Float
        get() = prefs.getFloat(KEY_HOME, DEFAULT_HOME)
        set(value) = prefs.edit { putFloat(KEY_HOME, value.coerceIn(MIN, MAX)) }

    /** Smaller than [home] by default, so unlocking flies in toward your location. */
    var lock: Float
        get() = prefs.getFloat(KEY_LOCK, DEFAULT_LOCK)
        set(value) = prefs.edit { putFloat(KEY_LOCK, value.coerceIn(MIN, MAX)) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        // Past 2x the 4096-wide Earth images start to look soft.
        const val MIN = 0.6f
        const val MAX = 2f

        private const val DEFAULT_HOME = 1f
        private const val DEFAULT_LOCK = 0.78f

        private const val PREFS_NAME = "zoom"
        private const val KEY_HOME = "home"
        private const val KEY_LOCK = "lock"
    }
}
