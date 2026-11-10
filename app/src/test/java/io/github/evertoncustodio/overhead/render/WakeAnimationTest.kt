package io.github.evertoncustodio.overhead.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAnimationTest {

    @Test
    fun settledFrameIsUnchanged() {
        assertEquals(0.0, WakeAnimation.turnDegrees(1f), 1e-9)
        assertEquals(1f, WakeAnimation.scale(1f), 1e-6f)
        assertEquals(1f, WakeAnimation.brightness(1f), 1e-6f)
        assertEquals(1f, WakeAnimation.cityLights(1f), 1e-6f)
    }

    @Test
    fun startsTurnedSmallerDimAndDark() {
        assertTrue(WakeAnimation.turnDegrees(0f) > 0.0)
        assertTrue(WakeAnimation.scale(0f) < 1f)
        assertTrue(WakeAnimation.brightness(0f) < 1f)
        assertEquals(0f, WakeAnimation.cityLights(0f), 1e-6f)
    }

    @Test
    fun movesMostlyAtTheStart() {
        // An ease-out: by halfway, most of the turn is done.
        val start = WakeAnimation.turnDegrees(0f)
        assertTrue(WakeAnimation.turnDegrees(0.5f) < start * 0.25)
    }

    @Test
    fun valuesStayInRange() {
        for (progress in listOf(-0.5f, 0f, 0.3f, 0.7f, 1f, 1.5f)) {
            assertTrue(WakeAnimation.brightness(progress) in 0f..1f)
            assertTrue(WakeAnimation.cityLights(progress) in 0f..1f)
            assertTrue(WakeAnimation.scale(progress) in 0f..1f)
        }
    }
}
