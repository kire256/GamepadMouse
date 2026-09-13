package com.droidforge.gamepadmouse.input

import android.view.KeyEvent

enum class KeyboardCommand {
    NONE,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACKSPACE,
    SPACE,
    SHIFT,
    PREVIOUS_LAYOUT,
    NEXT_LAYOUT,
    ENTER,
    EXIT,
}

object KeyboardInputRouter {
    fun commandFor(keyCode: Int): KeyboardCommand = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> KeyboardCommand.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyboardCommand.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyboardCommand.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyboardCommand.RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> KeyboardCommand.SELECT
        KeyEvent.KEYCODE_BUTTON_B -> KeyboardCommand.BACKSPACE
        KeyEvent.KEYCODE_BUTTON_X -> KeyboardCommand.SPACE
        KeyEvent.KEYCODE_BUTTON_Y -> KeyboardCommand.SHIFT
        KeyEvent.KEYCODE_BUTTON_L1 -> KeyboardCommand.PREVIOUS_LAYOUT
        KeyEvent.KEYCODE_BUTTON_R1 -> KeyboardCommand.NEXT_LAYOUT
        KeyEvent.KEYCODE_BUTTON_START -> KeyboardCommand.ENTER
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> KeyboardCommand.EXIT
        else -> KeyboardCommand.NONE
    }
}
