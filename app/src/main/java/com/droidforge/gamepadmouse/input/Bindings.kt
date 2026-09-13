package com.droidforge.gamepadmouse.input

import android.view.KeyEvent

/** What a gamepad button does while the service is in MOUSE mode. */
enum class MouseAction(val label: String) {
    NONE("Unassigned"),
    TAP("Left click (tap)"),
    LONG_PRESS("Right click (long-press)"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recents"),
    SLOW("Slow cursor (hold)"),
    FAST("Fast cursor (hold)"),
    TOGGLE_MODE("Toggle mouse mode"),
    // System actions
    SCREENSHOT("Screenshot"),
    NOTIFICATIONS("Notifications shade"),
    QUICK_SETTINGS("Quick settings"),
    APP_PICKER("App switcher"),
    POWER_MENU("Power menu"),
    // Media controls
    MEDIA_PLAY_PAUSE("Play / Pause"),
    MEDIA_NEXT("Next track"),
    MEDIA_PREVIOUS("Previous track"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down"),
    VOLUME_MUTE("Mute / Unmute"),
    KEYBOARD_MODE("Keyboard Mode"),
    KEYBOARD_PRESS("Keyboard press"),
    KEYBOARD_BACK("Keyboard backspace"),
    KEYBOARD_MOVE("Keyboard move position"),
    KEYBOARD_HIDE("Keyboard hide"),
}

enum class ServiceMode { GAMEPAD, MOUSE, KEYBOARD }

object DefaultBindings {
    /** Default button → action map for MOUSE mode. Fully reassignable in later phases. */
    val buttons: Map<Int, MouseAction> = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to MouseAction.TAP,
        KeyEvent.KEYCODE_BUTTON_B to MouseAction.BACK,
        KeyEvent.KEYCODE_BUTTON_X to MouseAction.LONG_PRESS,
        KeyEvent.KEYCODE_BUTTON_Y to MouseAction.RECENTS,
        KeyEvent.KEYCODE_BUTTON_L1 to MouseAction.SLOW,
        KeyEvent.KEYCODE_BUTTON_R1 to MouseAction.FAST,
        KeyEvent.KEYCODE_BUTTON_THUMBL to MouseAction.HOME,
    )

    private val keyboardDefaults = listOf(
        // Erik's spec: B = press, A = hide (matches KeyboardInputRouter fallback)
        ButtonBinding(setOf(KeyEvent.KEYCODE_BUTTON_A), MouseAction.KEYBOARD_HIDE, setOf(BindingMode.KEYBOARD), 0L),
        ButtonBinding(setOf(KeyEvent.KEYCODE_BUTTON_X), MouseAction.KEYBOARD_BACK, setOf(BindingMode.KEYBOARD), 0L),
        ButtonBinding(setOf(KeyEvent.KEYCODE_BUTTON_B), MouseAction.KEYBOARD_PRESS, setOf(BindingMode.KEYBOARD), 0L),
    )

    val detailed: List<ButtonBinding> = buttons.map { (keyCode, action) ->
        ButtonBinding(setOf(keyCode), action, setOf(BindingMode.MOUSE), 0L)
    } + keyboardDefaults

    fun withKeyboardDefaults(bindings: List<ButtonBinding>): List<ButtonBinding> {
        val missing = keyboardDefaults.filter { default ->
            bindings.none { it.action == default.action && BindingMode.KEYBOARD in it.modes }
        }
        return bindings + missing
    }

    /** Default chord that flips GAMEPAD ⇄ MOUSE: Start + Select held together. */
    val toggleChord: Set<Int> = setOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)

    /** Every key we are willing to swallow in MOUSE mode so the foreground app never sees it. */
    fun isGamepadKey(keyCode: Int): Boolean =
        KeyEvent.isGamepadButton(keyCode) || keyCode in DPAD_KEYS

    private val DPAD_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
    )
}
