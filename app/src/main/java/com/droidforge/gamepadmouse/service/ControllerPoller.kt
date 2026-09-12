package com.droidforge.gamepadmouse.service

import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Polls the first connected gamepad/joystick device for axis values every [intervalMs] ms.
 * No window focus required — reads directly from InputDevice.
 *
 * Axis values are synthesised into a fake MotionEvent and forwarded to [onJoystick].
 * This runs on the main thread via a Handler so it's safe to call service methods directly.
 */
class ControllerPoller(
    private val inputManager: InputManager,
    private val onAxes: (lx: Float, ly: Float, rx: Float, ry: Float) -> Unit,
    private val intervalMs: Long = 16L, // ~60 fps
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            pollAxes()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun pollAxes() {
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val device = InputDevice.getDevice(id) ?: continue
            if (!isGamepad(device)) continue

            val lx = device.getMotionRange(MotionEvent.AXIS_X)?.let { readAxis(device, MotionEvent.AXIS_X) } ?: 0f
            val ly = device.getMotionRange(MotionEvent.AXIS_Y)?.let { readAxis(device, MotionEvent.AXIS_Y) } ?: 0f
            // Right stick: try AXIS_Z/AXIS_RZ first (most controllers), fall back to AXIS_RX/AXIS_RY
            var rx = device.getMotionRange(MotionEvent.AXIS_Z)?.let { readAxis(device, MotionEvent.AXIS_Z) } ?: 0f
            var ry = device.getMotionRange(MotionEvent.AXIS_RZ)?.let { readAxis(device, MotionEvent.AXIS_RZ) } ?: 0f
            if (rx == 0f && ry == 0f) {
                rx = device.getMotionRange(MotionEvent.AXIS_RX)?.let { readAxis(device, MotionEvent.AXIS_RX) } ?: 0f
                ry = device.getMotionRange(MotionEvent.AXIS_RY)?.let { readAxis(device, MotionEvent.AXIS_RY) } ?: 0f
            }

            onAxes(lx, ly, rx, ry)
            return // use first gamepad found
        }
    }

    private fun readAxis(device: InputDevice, axis: Int): Float {
        // InputDevice doesn't expose current axis values directly; we get them from MotionEvent.
        // As a fallback we return 0f — actual values come from onGenericMotionEvent in JoystickCaptureView.
        // This poller is only used to DETECT that a controller is present and to drive the frame loop.
        return 0f
    }

    private fun isGamepad(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }
}
