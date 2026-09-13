package com.droidforge.gamepadmouse.input

object ModeTransitions {
    fun keyboardActionTarget(currentMode: ServiceMode): ServiceMode =
        if (currentMode == ServiceMode.KEYBOARD) ServiceMode.MOUSE else ServiceMode.KEYBOARD
}
