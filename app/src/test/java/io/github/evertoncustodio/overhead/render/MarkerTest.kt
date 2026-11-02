package io.github.evertoncustodio.overhead.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.hypot

class MarkerTest {

    private val place = GeoPoint(-23.5, -46.6)

    @Test
    fun centeredPlaceIsInTheMiddle() {
        val (x, y, visibility) = Marker.screenPosition(place, place, 4f, 0.9f).toList()
        assertEquals(0f, x, 1e-5f)
        assertEquals(0f, y, 1e-5f)
        assertEquals(1f, visibility, 1e-5f)
    }

    @Test
    fun panningEastMovesThePlaceLeft() {
        // Looking at a point east of the place, the place appears to its west, on the left.
        val shown = place.copy(longitude = place.longitude + 8.0)
        val (x, _, visibility) = Marker.screenPosition(place, shown, 4f, 0.9f).toList()
        assertTrue(x < 0f)
        assertEquals(1f, visibility, 1e-5f)
    }

    @Test
    fun placeOnTheEdgeLandsOnTheGlobesEdge() {
        // From 4 radii away, the visible edge is acos(1/4) from the point facing the camera.
        val edgeDegrees = Math.toDegrees(acos(1.0 / 4.0))
        val onEdge = GeoPoint(latitude = 0.0, longitude = edgeDegrees)
        val (x, y, visibility) = Marker.screenPosition(onEdge, GeoPoint(0.0, 0.0), 4f, 0.9f).toList()
        assertEquals(0.9f, hypot(x, y), 1e-4f)
        assertEquals(0f, visibility, 1e-5f)
    }

    @Test
    fun placeOnTheFarSideIsHidden() {
        val shown = place.copy(longitude = place.longitude + 180.0)
        assertEquals(0f, Marker.screenPosition(place, shown, 4f, 0.9f)[2], 1e-5f)
    }
}
