package io.github.evertoncustodio.overhead.location

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.evertoncustodio.overhead.render.GeoPoint
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Remembers which place the globe is centered on. Shared by the settings screen and the
 * wallpaper (they run in the same process, so changes reach the wallpaper immediately).
 */
class LocationStore(context: Context) {

    enum class Mode { DEVICE, MANUAL }

    /** The place to show, how it was chosen, and an optional human-readable name. */
    data class Place(val point: GeoPoint, val mode: Mode, val name: String?, val isGuess: Boolean)

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val mode: Mode
        get() = Mode.valueOf(prefs.getString(KEY_MODE, Mode.DEVICE.name)!!)

    /** Whether the wallpaper marks the centered place with a dot. On unless turned off. */
    var showMarker: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MARKER, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_MARKER, value) }

    /** The saved place, or a guess from the time zone if nothing was saved yet. */
    fun current(): Place {
        if (!prefs.contains(KEY_LATITUDE)) {
            return Place(guessFromTimeZone(), mode, name = null, isGuess = true)
        }
        val point = GeoPoint(
            latitude = prefs.getFloat(KEY_LATITUDE, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble(),
        )
        return Place(point, mode, prefs.getString(KEY_NAME, null), isGuess = false)
    }

    /**
     * Saves a location reported by the device, then looks up its name in the background.
     * Ignored while a manual place is chosen, and when the phone hasn't really moved.
     */
    fun updateDeviceLocation(point: GeoPoint) {
        if (mode != Mode.DEVICE) return
        val saved = current()
        val rounded = point.rounded()
        if (!saved.isGuess && saved.mode == Mode.DEVICE && saved.point == rounded && saved.name != null) return

        save(rounded, Mode.DEVICE, name = null)
        PlaceNames.nameOf(appContext, rounded) { name ->
            // Only attach the name if nothing else was saved in the meantime.
            if (name != null && current().let { it.mode == Mode.DEVICE && it.point == rounded }) {
                save(rounded, Mode.DEVICE, name)
            }
        }
    }

    /** Shows a place chosen by hand; looks up its name if none is given. */
    fun saveManualPlace(point: GeoPoint, name: String?) {
        val rounded = point.rounded()
        save(rounded, Mode.MANUAL, name)
        if (name == null) {
            PlaceNames.nameOf(appContext, rounded) { found ->
                if (found != null && current().let { it.mode == Mode.MANUAL && it.point == rounded }) {
                    save(rounded, Mode.MANUAL, found)
                }
            }
        }
    }

    /** Goes back to following the device; the old place stays until a new location arrives. */
    fun useDeviceLocation() {
        prefs.edit { putString(KEY_MODE, Mode.DEVICE.name) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun save(point: GeoPoint, mode: Mode, name: String?) {
        prefs.edit {
            putString(KEY_MODE, mode.name)
            putFloat(KEY_LATITUDE, point.latitude.toFloat())
            putFloat(KEY_LONGITUDE, point.longitude.toFloat())
            putString(KEY_NAME, name)
        }
    }

    /**
     * About 11 km of precision: plenty for a globe, and no need to store more. Goes through
     * Float so it compares equal to what [current] reads back from the saved Floats.
     */
    private fun GeoPoint.rounded() = GeoPoint(roundToTenth(latitude), roundToTenth(longitude))

    private fun roundToTenth(value: Double): Double = ((value * 10).roundToInt() / 10f).toDouble()

    /**
     * Before any location is known, face the time zone's meridian: each hour of offset
     * from UTC is 15 degrees of longitude. Latitude can't be guessed, so use the tropics,
     * where the globe shows the most land.
     */
    private fun guessFromTimeZone(): GeoPoint {
        val offsetHours = TimeZone.getDefault().rawOffset / 3_600_000.0
        return GeoPoint(latitude = 15.0, longitude = offsetHours * 15.0)
    }

    private companion object {
        const val PREFS_NAME = "location"
        const val KEY_MODE = "mode"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_NAME = "name"
        const val KEY_SHOW_MARKER = "show_marker"
    }
}
