package com.droidforge.gamepadmouse.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BindingDefaultsTest {
    @Test
    fun missingKeyboardDefaultsAreMergedWithoutReplacingCustomBindings() {
        val custom = ButtonBinding(setOf(KeyEvent.KEYCODE_BUTTON_Y), MouseAction.HOME, setOf(BindingMode.MOUSE))

        val merged = DefaultBindings.withKeyboardDefaults(listOf(custom))

        assertTrue(custom in merged)
        assertTrue(merged.any {
            it.action == MouseAction.KEYBOARD_PRESS &&
                it.keyCodes == setOf(KeyEvent.KEYCODE_BUTTON_A) &&
                it.modes == setOf(BindingMode.KEYBOARD)
        })
        assertTrue(merged.any {
            it.action == MouseAction.KEYBOARD_HIDE &&
                it.keyCodes == setOf(KeyEvent.KEYCODE_BUTTON_B) &&
                it.modes == setOf(BindingMode.KEYBOARD)
        })
        assertTrue(merged.any {
            it.action == MouseAction.KEYBOARD_BACK &&
                it.keyCodes == setOf(KeyEvent.KEYCODE_BUTTON_X) &&
                it.modes == setOf(BindingMode.KEYBOARD)
        })
    }

    @Test
    fun existingKeyboardActionsAreNotDuplicated() {
        val existing = listOf(
            ButtonBinding(setOf(KeyEvent.KEYCODE_BUTTON_X), MouseAction.KEYBOARD_PRESS, setOf(BindingMode.KEYBOARD)),
            ButtonBinding(setOf(KeyEvent.KEYCODE_BUTTON_Y), MouseAction.KEYBOARD_HIDE, setOf(BindingMode.KEYBOARD)),
        )

        val merged = DefaultBindings.withKeyboardDefaults(existing)

        assertEquals(1, merged.count { it.action == MouseAction.KEYBOARD_PRESS })
        assertEquals(1, merged.count { it.action == MouseAction.KEYBOARD_HIDE })
    }
}