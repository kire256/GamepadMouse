package com.droidforge.gamepadmouse.input

import org.junit.Assert.assertEquals
import org.junit.Test

class BindingMatcherResetTest {
    @Test
    fun resetAllowsModeSwitchBindingToFireWithoutReceivingKeyUp() {
        val matcher = BindingMatcher()
        val binding = ButtonBinding(setOf(96), MouseAction.KEYBOARD_MODE)

        matcher.keyDown(96)
        matcher.markFired(binding)
        matcher.reset()
        matcher.keyDown(96)

        assertEquals(listOf(binding), matcher.matching(listOf(binding), ServiceMode.MOUSE))
    }
}
