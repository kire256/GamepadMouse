package com.droidforge.gamepadmouse.service

import android.content.Context
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView-based joystick capture.
 * SurfaceView has its own native input channel — independent of View focus system.
 * It receives gamepad MotionEvents as long as the Surface is visible, regardless
 * of which app has foreground focus.
 */
class JoystickCaptureView(
    context: Context,
    private val onJoystick: (MotionEvent) -> Boolean,
) : SurfaceView(context), SurfaceHolder.Callback {

    private var lastReclaimMs = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setZOrderOnTop(true)
        holder.addCallback(this)
        // Transparent surface
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun reclaimFocus() {
        if (!hasFocus()) requestFocus()
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastReclaimMs < 500L) return
        lastReclaimMs = now
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD)
        val up   = KeyEvent(now, now, KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_UNKNOWN, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD)
        dispatchKeyEvent(down)
        dispatchKeyEvent(up)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Log.d("GamepadMouse", "JoyCap windowFocus=$hasWindowFocus")
        if (hasWindowFocus) requestFocus()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            Log.d("GamepadMouse", "JoyCap axes X=${event.getAxisValue(MotionEvent.AXIS_X)} Y=${event.getAxisValue(MotionEvent.AXIS_Y)} Z=${event.getAxisValue(MotionEvent.AXIS_Z)} RZ=${event.getAxisValue(MotionEvent.AXIS_RZ)}")
            return onJoystick(event)
        }
        return super.onGenericMotionEvent(event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        requestFocus()
        Log.d("GamepadMouse", "JoyCap surface created, focus=${hasFocus()}")
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
}
