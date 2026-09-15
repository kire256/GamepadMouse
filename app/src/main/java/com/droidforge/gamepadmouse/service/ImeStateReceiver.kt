package com.droidforge.gamepadmouse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for the Gamepad Keyboard IME's shown/hidden broadcast (signature-guarded,
 * sent only by our sibling keyboard app). While the keyboard surface is up, the
 * service stands down: sticks must navigate keys, not drag the cursor.
 */
class ImeStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GamepadMouseService.ACTION_IME_STATE) return
        val shown = intent.getBooleanExtra(GamepadMouseService.EXTRA_IME_SHOWN, false)
        GamepadMouseService.setImeOverlayUp(shown)
        android.util.Log.d("GamepadMouse", "receiver: ime shown=$shown")
    }
}
