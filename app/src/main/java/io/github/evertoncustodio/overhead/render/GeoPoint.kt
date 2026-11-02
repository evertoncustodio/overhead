package io.github.evertoncustodio.overhead.render

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A place on Earth, in degrees. Latitude is positive north, longitude positive east. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    /**
     * The direction from Earth's center to this point, in Earth-fixed coordinates
     * (+Y is the north pole, +Z is latitude 0 / longitude 0).
     */
    fun toUnitVector(): FloatArray {
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        return floatArrayOf(
            (cos(lat) * sin(lon)).toFloat(),
            sin(lat).toFloat(),
            (cos(lat) * cos(lon)).toFloat(),
        )
    }

    /**
     * The rotation that turns the globe so this point faces the camera with north up,
     * as a column-major 3x3 matrix ready for glUniformMatrix3fv.
     *
     * It maps view space (camera looking down -Z) to Earth-fixed coordinates, where
     * +Y is the north pole and +Z is latitude 0 / longitude 0. It is the product
     * rotateY(longitude) * rotateX(-latitude).
     */
    fun viewToEarthMatrix(): FloatArray {
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        val sinLat = sin(lat).toFloat()
        val cosLat = cos(lat).toFloat()
        val sinLon = sin(lon).toFloat()
        val cosLon = cos(lon).toFloat()

        return floatArrayOf(
            // column 0: where the view's +X (screen right) points on Earth
            cosLon, 0f, -sinLon,
            // column 1: where the view's +Y (screen up) points on Earth
            -sinLon * sinLat, cosLat, -cosLon * sinLat,
            // column 2: where the view's +Z (toward the camera) points on Earth
            sinLon * cosLat, sinLat, cosLon * cosLat,
        )
    }

    /** Human-readable form, e.g. "23.6° S, 46.6° W". */
    fun format(): String {
        val ns = if (latitude < 0) "S" else "N"
        val ew = if (longitude < 0) "W" else "E"
        return String.format(Locale.US, "%.1f° %s, %.1f° %s", abs(latitude), ns, abs(longitude), ew)
    }

    companion object {
        private val COORDINATES = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""")

        /** Reads coordinates typed as "latitude, longitude", e.g. "-23.5, -46.6". */
        fun parse(text: String): GeoPoint? {
            val match = COORDINATES.matchEntire(text) ?: return null
            val latitude = match.groupValues[1].toDouble()
            val longitude = match.groupValues[2].toDouble()
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return GeoPoint(latitude, longitude)
        }
    }
}
