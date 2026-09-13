package com.droidforge.gamepadmouse.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BindingCodecTest {
    @Test
    fun roundTripPreservesChordModesAndHold() {
        val binding = ButtonBinding(
            keyCodes = setOf(96, 99),
            action = MouseAction.KEYBOARD_MODE,
            modes = setOf(BindingMode.GAMEPAD, BindingMode.MOUSE),
            holdDurationMs = 750L,
        )

        val decoded = BindingCodec.decode(BindingCodec.encode(listOf(binding)))

        assertEquals(listOf(binding), decoded)
    }

    @Test
    fun legacyBindingsMigrateToMouseOnlySingleButtonBindings() {
        val decoded = BindingCodec.decode("96:TAP,97:BACK")

        assertEquals(2, decoded.size)
        assertEquals(setOf(96), decoded.first().keyCodes)
        assertEquals(setOf(BindingMode.MOUSE), decoded.first().modes)
        assertEquals(0L, decoded.first().holdDurationMs)
    }

    @Test
    fun matchesOnlyConfiguredModes() {
        val binding = ButtonBinding(setOf(96), MouseAction.HOME, setOf(BindingMode.MOUSE), 0L)

        assertTrue(binding.appliesIn(ServiceMode.MOUSE))
        assertFalse(binding.appliesIn(ServiceMode.GAMEPAD))
        assertFalse(binding.appliesIn(ServiceMode.KEYBOARD))
    }
}
