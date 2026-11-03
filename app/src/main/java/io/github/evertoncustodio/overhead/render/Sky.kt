package io.github.evertoncustodio.overhead.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How the sky behind the globe (the Milky Way and the stars) is turned.
 *
 * The sky map uses equatorial coordinates laid out like Earth-fixed ones: +Y is the north
 * celestial pole, +Z is right ascension 0h and +X is 6h. A direction's right ascension and
 * declination are the longitude and latitude of [GeoPoint.toUnitVector].
 *
 * Matrices are 3x3, column-major, ready for glUniformMatrix3fv.
 */
object Sky {

    /**
     * The real sky: Earth turns under the stars once a sidereal day, so a place at longitude λ
     * looks up at right ascension λ + sidereal time. Behind the globe is the sky overhead on the
     * other side of the planet, so the Milky Way shows there only part of the day.
     */
    fun realViewToSky(viewToEarth: FloatArray, epochMillis: Long): FloatArray =
        multiply(rotateY(Math.toRadians(SunPosition.siderealTime(epochMillis))), viewToEarth)

    /**
     * The sky turned so the Milky Way always runs behind the globe, with its bright center
     * above the planet. It stays put relative to [centerViewToEarth] (the globe without the home
     * screen swipe), and turns with the swipe in [viewToEarth] like the real sky would.
     */
    fun milkyWayViewToSky(viewToEarth: FloatArray, centerViewToEarth: FloatArray): FloatArray =
        multiply(MILKY_WAY_IN_VIEW, multiply(transpose(centerViewToEarth), viewToEarth))

    /** Rotation about the north pole that moves longitudes east by [radians]. */
    private fun rotateY(radians: Double): FloatArray {
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return floatArrayOf(c, 0f, -s, 0f, 1f, 0f, s, 0f, c)
    }

    internal fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(9)
        for (column in 0..2) for (row in 0..2) {
            result[column * 3 + row] = (0..2).sumOf { (a[it * 3 + row] * b[column * 3 + it]).toDouble() }.toFloat()
        }
        return result
    }

    internal fun transpose(m: FloatArray): FloatArray =
        floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])

    /** A matrix whose columns are the three given vectors. */
    private fun columns(x: FloatArray, y: FloatArray, z: FloatArray): FloatArray =
        floatArrayOf(x[0], x[1], x[2], y[0], y[1], y[2], z[0], z[1], z[2])

    private fun cross(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    // The galaxy's center (in Sagittarius) and its north pole, in sky coordinates.
    internal val GALACTIC_CENTER = GeoPoint(latitude = -28.936, longitude = 266.405).toUnitVector()
    private val GALACTIC_NORTH_POLE = GeoPoint(latitude = 27.128, longitude = 192.859).toUnitVector()

    // Where the galaxy's center appears in the view: this far above the screen's center, past
    // the top of the globe, with the band tilted this much from vertical. The globe covers the
    // middle of the screen, so a band running up and down shows above and below it.
    internal const val CENTER_ABOVE_DEGREES = 21.0
    private const val TILT_DEGREES = 20.0

    /**
     * Maps view directions to the sky so the galaxy's center sits [CENTER_ABOVE_DEGREES] up
     * and the galactic plane runs along the screen's long side, tilted by [TILT_DEGREES].
     */
    private val MILKY_WAY_IN_VIEW: FloatArray = run {
        val tilt = Math.toRadians(TILT_DEGREES)
        val up = floatArrayOf(-sin(tilt).toFloat(), cos(tilt).toFloat(), 0f)
        val right = floatArrayOf(cos(tilt).toFloat(), sin(tilt).toFloat(), 0f)
        val above = Math.toRadians(CENTER_ABOVE_DEGREES)
        // In the view (the camera looks down -Z): toward the galaxy's center, along the band
        // from there, and across it.
        val center = floatArrayOf(up[0] * sin(above).toFloat(), up[1] * sin(above).toFloat(), -cos(above).toFloat())
        val along = floatArrayOf(up[0] * cos(above).toFloat(), up[1] * cos(above).toFloat(), sin(above).toFloat())
        val viewBasis = columns(center, along, right)
        // The same three directions in the galaxy: its center, galactic longitude 90°, its pole.
        // The published coordinates are rounded, so make them exactly perpendicular. Both sets
        // are right-handed, so this is a pure rotation.
        val alongGalaxy = normalize(cross(GALACTIC_NORTH_POLE, GALACTIC_CENTER))
        val pole = cross(GALACTIC_CENTER, alongGalaxy)
        val skyBasis = columns(GALACTIC_CENTER, alongGalaxy, pole)
        multiply(skyBasis, transpose(viewBasis))
    }

    private fun normalize(v: FloatArray): FloatArray {
        val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / length, v[1] / length, v[2] / length)
    }
}
