package com.droidforge.gamepadmouse.input

import org.junit.Assert.assertEquals
import org.junit.Test

class HatNavigationDetectorTest {
    @Test
    fun directionFiresOnceUntilHatReturnsToCenter() {
        val detector = HatNavigationDetector()

        assertEquals(KeyboardCommand.RIGHT, detector.update(1f, 0f))
        assertEquals(KeyboardCommand.NONE, detector.update(1f, 0f))
        assertEquals(KeyboardCommand.NONE, detector.update(0f, 0f))
        assertEquals(KeyboardCommand.RIGHT, detector.update(1f, 0f))
    }

    @Test
    fun verticalDirectionMapsToUpAndDown() {
        val detector = HatNavigationDetector()

        assertEquals(KeyboardCommand.UP, detector.update(0f, -1f))
        detector.update(0f, 0f)
        assertEquals(KeyboardCommand.DOWN, detector.update(0f, 1f))
    }
}
