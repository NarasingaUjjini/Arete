package com.trailmap.gps.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingMathTest {
    @Test
    fun normalizeWrapsNegativeAndOver360() {
        assertEquals(350.0, HeadingMath.normalize(-10.0), 0.001)
        assertEquals(10.0, HeadingMath.normalize(370.0), 0.001)
        assertEquals(0.0, HeadingMath.normalize(720.0), 0.001)
    }

    @Test
    fun deltaTakesShortArcAcrossNorth() {
        assertEquals(20.0, HeadingMath.delta(350.0, 10.0), 0.001)
        assertEquals(-20.0, HeadingMath.delta(10.0, 350.0), 0.001)
    }

    @Test
    fun smoothDoesNotJumpAcrossNorth() {
        val next = HeadingMath.smooth(350.0, 10.0, 0.5)
        assertTrue(next > 350.0 || next < 20.0)
        assertEquals(0.0, HeadingMath.delta(350.0, next) - 10.0, 0.001)
    }
}
