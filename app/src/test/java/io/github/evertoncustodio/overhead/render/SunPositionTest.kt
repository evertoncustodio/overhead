package io.github.evertoncustodio.overhead.render

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SunPositionTest {

    private fun sunAt(utc: String) = SunPosition.subsolarPoint(Instant.parse(utc).toEpochMilli())

    @Test
    fun juneSolsticeSunIsOverTheTropicOfCancer() {
        assertEquals(23.44, sunAt("2024-06-20T20:51:00Z").latitude, 0.05)
    }

    @Test
    fun decemberSolsticeSunIsOverTheTropicOfCapricorn() {
        assertEquals(-23.44, sunAt("2024-12-21T09:20:00Z").latitude, 0.05)
    }

    @Test
    fun marchEquinoxSunIsOverTheEquator() {
        assertEquals(0.0, sunAt("2024-03-20T03:06:00Z").latitude, 0.05)
    }

    @Test
    fun sunCrossesGreenwichAtSolarNoon() {
        // On 3 November the equation of time is about +16.4 minutes, so the sun
        // is over Greenwich at 11:43:35 UTC, and 4.1 degrees west of it at 12:00.
        assertEquals(0.0, sunAt("2024-11-03T11:43:35Z").longitude, 0.1)
        assertEquals(-4.1, sunAt("2024-11-03T12:00:00Z").longitude, 0.1)
    }

    @Test
    fun sunIsOppositeGreenwichAtMidnight() {
        // Around midnight UTC the sun is near longitude 180 (either sign).
        assertEquals(180.0, kotlin.math.abs(sunAt("2024-11-03T00:00:00Z").longitude), 5.0)
    }
}
