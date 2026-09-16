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
    TOGGLE_KEYBOARD("Toggle keyboard"),
    SNAP_TARGET("Snap to nearest control"),
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
}

enum class ServiceMode { GAMEPAD, MOUSE }

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
        KeyEvent.KEYCODE_BUTTON_THUMBR to MouseAction.SNAP_TARGET,
    )

    val detailed: List<ButtonBinding> = buttons.map { (keyCode, action) ->
        ButtonBinding(setOf(keyCode), action, setOf(BindingMode.MOUSE), 0L)
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
