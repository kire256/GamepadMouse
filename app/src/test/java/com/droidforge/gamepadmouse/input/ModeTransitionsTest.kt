package com.droidforge.gamepadmouse.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ModeTransitionsTest {
    @Test
    fun keyboardActionEntersKeyboardFromMouse() {
        assertEquals(ServiceMode.KEYBOARD, ModeTransitions.keyboardActionTarget(ServiceMode.MOUSE))
    }

    @Test
    fun keyboardActionReturnsToMouseFromKeyboard() {
        assertEquals(ServiceMode.MOUSE, ModeTransitions.keyboardActionTarget(ServiceMode.KEYBOARD))
    }
}
