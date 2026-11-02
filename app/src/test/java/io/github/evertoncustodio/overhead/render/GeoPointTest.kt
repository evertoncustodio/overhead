package io.github.evertoncustodio.overhead.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoPointTest {

    @Test
    fun formatsHemispheres() {
        assertEquals("23.6° S, 46.6° W", GeoPoint(-23.56, -46.64).format())
        assertEquals("35.7° N, 139.7° E", GeoPoint(35.68, 139.69).format())
    }

    @Test
    fun parsesTypedCoordinates() {
        assertEquals(GeoPoint(-23.5, -46.6), GeoPoint.parse("-23.5, -46.6"))
        assertEquals(GeoPoint(35.0, 139.0), GeoPoint.parse(" 35 139 "))
        assertEquals(GeoPoint(10.0, 20.0), GeoPoint.parse("10;20"))
    }

    @Test
    fun rejectsTextAndOutOfRangeCoordinates() {
        assertNull(GeoPoint.parse("Lisbon"))
        assertNull(GeoPoint.parse("95, 10"))
        assertNull(GeoPoint.parse("10, 190"))
    }

    @Test
    fun centerPointFacesTheCamera() {
        // The matrix's third column is where the view's +Z (toward the camera) lands on Earth,
        // which must be the center point itself.
        val point = GeoPoint(-23.5, -46.6)
        val matrix = point.viewToEarthMatrix()
        val expected = point.toUnitVector()
        for (i in 0..2) assertEquals(expected[i], matrix[6 + i], 1e-6f)
    }
}
