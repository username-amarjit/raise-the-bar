package com.raisethebar.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class RoundingTest {
    @Test
    fun `rounds half up like JavaScript Math round`() {
        assertEquals(8, roundHalfUp(7.5))
        assertEquals(3, roundHalfUp(2.5))
        assertEquals(-2, roundHalfUp(-2.5))
        assertEquals(7, roundHalfUp(7.49))
        assertEquals(9, roundHalfUp(8.999999999999996))
        assertEquals(0, roundHalfUp(0.49999999999999994))
        assertEquals(8, roundHalfUp(7.500000000000002))
    }

    @Test
    fun `rounds to the nearest step`() {
        assertEquals(120, roundToNearest(119.0, 5))
        assertEquals(2500, roundToNearest(2520.0, 100))
        assertEquals(2200, roundToNearest(2170.0, 100))
        assertEquals(2200, roundToNearest(2150.0, 100))
    }
}
