package com.droidforge.gamepadmouse.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardInputRouterTest {
    @Test
    fun dpadAndFaceButtonsMapToKeyboardCommands() {
        assertEquals(KeyboardCommand.UP, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(KeyboardCommand.SELECT, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(KeyboardCommand.BACKSPACE, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(KeyboardCommand.SPACE, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(KeyboardCommand.SHIFT, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(KeyboardCommand.PREVIOUS_LAYOUT, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(KeyboardCommand.NEXT_LAYOUT, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(KeyboardCommand.ENTER, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(KeyboardCommand.EXIT, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_SELECT))
    }

    @Test
    fun unknownButtonIsIgnored() {
        assertEquals(KeyboardCommand.NONE, KeyboardInputRouter.commandFor(KeyEvent.KEYCODE_BUTTON_THUMBL))
    }
}
