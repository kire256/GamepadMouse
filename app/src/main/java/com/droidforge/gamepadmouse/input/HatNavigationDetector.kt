package com.droidforge.gamepadmouse.input

class HatNavigationDetector(private val threshold: Float = 0.5f) {
    private var previousX = 0
    private var previousY = 0

    fun update(x: Float, y: Float): KeyboardCommand {
        val currentX = when {
            x > threshold -> 1
            x < -threshold -> -1
            else -> 0
        }
        val currentY = when {
            y > threshold -> 1
            y < -threshold -> -1
            else -> 0
        }
        val command = when {
            currentX == 1 && previousX != 1 -> KeyboardCommand.RIGHT
            currentX == -1 && previousX != -1 -> KeyboardCommand.LEFT
            currentY == 1 && previousY != 1 -> KeyboardCommand.DOWN
            currentY == -1 && previousY != -1 -> KeyboardCommand.UP
            else -> KeyboardCommand.NONE
        }
        previousX = currentX
        previousY = currentY
        return command
    }
}
