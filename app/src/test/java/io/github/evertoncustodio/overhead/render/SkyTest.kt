package io.github.evertoncustodio.overhead.render

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SkyTest {

    private val saoPaulo = GeoPoint(latitude = -23.5, longitude = -46.6)
    private val someMoment = 1_793_750_400_000L  // 3 November 2026, 00:00 UTC

    @Test
    fun `real sky puts the zenith at the place's latitude and local sidereal time`() {
        val viewToSky = Sky.realViewToSky(saoPaulo.viewToEarthMatrix(), someMoment)
        // The view's +Z points from the planet's center through the centered place, straight up.
        val zenith = transform(viewToSky, floatArrayOf(0f, 0f, 1f))
        val expected = GeoPoint(
            latitude = saoPaulo.latitude,
            longitude = saoPaulo.longitude + SunPosition.siderealTime(someMoment),
        ).toUnitVector()
        assertArrayEquals(expected, zenith, 1e-4f)
    }

    @Test
    fun `real sky turns once a sidereal day, not a solar day`() {
        val siderealDayMillis = 86_164_091L
        val now = Sky.realViewToSky(saoPaulo.viewToEarthMatrix(), someMoment)
        val nextDay = Sky.realViewToSky(saoPaulo.viewToEarthMatrix(), someMoment + siderealDayMillis)
        assertArrayEquals(now, nextDay, 1e-4f)
    }

    @Test
    fun `milky way option puts the galaxy's center just above the globe`() {
        val viewToSky = Sky.milkyWayViewToSky(saoPaulo.viewToEarthMatrix(), saoPaulo.viewToEarthMatrix())
        val above = Math.toRadians(Sky.CENTER_ABOVE_DEGREES)
        val tilt = Math.toRadians(20.0)
        val direction = floatArrayOf(
            (-sin(tilt) * sin(above)).toFloat(),
            (cos(tilt) * sin(above)).toFloat(),
            (-cos(above)).toFloat(),
        )
        assertArrayEquals(Sky.GALACTIC_CENTER, transform(viewToSky, direction), 2e-3f)
    }

    @Test
    fun `both skies are pure rotations`() {
        val panned = saoPaulo.copy(longitude = saoPaulo.longitude + 8.0)
        val matrices = listOf(
            Sky.realViewToSky(panned.viewToEarthMatrix(), someMoment),
            Sky.milkyWayViewToSky(panned.viewToEarthMatrix(), saoPaulo.viewToEarthMatrix()),
        )
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        for (m in matrices) assertArrayEquals(identity, Sky.multiply(Sky.transpose(m), m), 1e-4f)
    }

    /** Multiplies a column-major 3x3 matrix by a vector. */
    private fun transform(m: FloatArray, v: FloatArray): FloatArray =
        FloatArray(3) { row -> m[row] * v[0] + m[3 + row] * v[1] + m[6 + row] * v[2] }
}
