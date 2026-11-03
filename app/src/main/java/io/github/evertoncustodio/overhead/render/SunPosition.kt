package io.github.evertoncustodio.overhead.render

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the sun is directly overhead (the "subsolar point") at a given moment.
 *
 * Uses the low-precision solar formulas from the Astronomical Almanac, accurate to
 * about 0.01 degrees between 1950 and 2050: far finer than a pixel on the globe.
 */
object SunPosition {

    fun subsolarPoint(epochMillis: Long): GeoPoint {
        // Days since 1 January 2000, 12:00 UTC (the J2000 epoch).
        val n = (epochMillis - J2000_EPOCH_MILLIS) / MILLIS_PER_DAY

        // Sun's position along its yearly path (the ecliptic), in degrees.
        val meanLongitude = 280.460 + 0.9856474 * n
        val meanAnomaly = Math.toRadians(357.528 + 0.9856003 * n)
        val eclipticLongitude = Math.toRadians(
            meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly)
        )
        val obliquity = Math.toRadians(23.439 - 0.0000004 * n)

        // Convert to the sky's equatorial coordinates.
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))

        // Earth's rotation angle turns sky coordinates into a longitude.
        val longitude = normalizeDegrees(Math.toDegrees(rightAscension) - siderealTime(epochMillis))

        return GeoPoint(latitude = Math.toDegrees(declination), longitude = longitude)
    }

    /**
     * Earth's rotation angle against the stars (Greenwich mean sidereal time), in degrees, not
     * wrapped: the right ascension overhead at longitude 0.
     */
    fun siderealTime(epochMillis: Long): Double {
        val n = (epochMillis - J2000_EPOCH_MILLIS) / MILLIS_PER_DAY
        return 280.46061837 + 360.98564736629 * n
    }

    /** Wraps an angle into -180..180 degrees. */
    private fun normalizeDegrees(degrees: Double): Double {
        val wrapped = (degrees + 180.0).mod(360.0) - 180.0
        return if (wrapped == -180.0) 180.0 else wrapped
    }

    private const val J2000_EPOCH_MILLIS = 946_728_000_000L
    private const val MILLIS_PER_DAY = 86_400_000.0
}
