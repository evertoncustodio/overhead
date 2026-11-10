package io.github.evertoncustodio.overhead.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Which animations the wallpaper plays. Shared by the settings screen and the wallpaper, which
 * reads it each time the screen turns off, so it needs no listener.
 */
class AnimationStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The globe settling in and lighting up as the screen turns on. On by default. */
    var screenOn: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN_ON, value) }

    private companion object {
        const val PREFS_NAME = "animation"
        const val KEY_SCREEN_ON = "screen_on"
    }
}
