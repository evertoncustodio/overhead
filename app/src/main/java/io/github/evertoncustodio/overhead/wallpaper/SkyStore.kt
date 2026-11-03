package io.github.evertoncustodio.overhead.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * How the sky behind the globe is shown. Shared by the settings screen and the wallpaper,
 * which reacts to changes through a preference listener.
 */
class SkyStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Turn the sky so the Milky Way is always behind the globe. Off by default: the real sky,
     * where the Milky Way shows only part of the day.
     */
    var keepMilkyWayInView: Boolean
        get() = prefs.getBoolean(KEY_KEEP_MILKY_WAY_IN_VIEW, false)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_MILKY_WAY_IN_VIEW, value) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val PREFS_NAME = "sky"
        const val KEY_KEEP_MILKY_WAY_IN_VIEW = "keep_milky_way_in_view"
    }
}
