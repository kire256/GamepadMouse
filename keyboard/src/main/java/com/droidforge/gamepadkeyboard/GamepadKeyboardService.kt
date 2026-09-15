package com.droidforge.gamepadkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * A full keyboard IME driven entirely by a game controller.
 * Text goes to the focused field via InputConnection — the platform's real typing path.
 */
class GamepadKeyboardService : InputMethodService(), KeyboardView.Listener {

    private companion object {
        const val TAG = "GPKeyboard"

        /** Tells the GamepadMouse app when this IME's surface is on-screen (it then
         *  stands its cursor down — sticks belong to key navigation while we're up). */
        const val ACTION_IME_STATE = "com.droidforge.gamepadkeyboard.IME_STATE"
        const val EXTRA_SHOWN = "shown"

        /** Receiver app's package. The broadcast must be EXPLICITLY targeted —
         *  implicit custom broadcasts are dropped by Android 8+ and never reach
         *  the app's manifest receiver. (Keep in sync with the app's applicationId;
         *  release builds drop the .debug suffix — align when publishing.) */
        const val MOUSE_APP_PACKAGE = "com.droidforge.gamepadmouse.debug"
    }

    private var keyboardView: KeyboardView? = null
    private var lastAxisDump = ""

    // True while we deliberately hid the IME — isInputViewShown() lags/reads true
    // on some Samsung builds after requestHideSelf(), letting keys "ghost type".
    @Volatile
    private var dismissed = false

    // Hat-switch / left-stick edge tracking for selection movement
    private var lastHatX = 0f
    private var lastHatY = 0f
    private var lastStickX = 0f
    private var lastStickY = 0f

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        // Fixed height, bottom-docked — like a stock keyboard. Both the layout params
        // AND the view's own onMeasure assert the height; some devices stretch the
        // IME view to fill the screen otherwise.
        val dm = resources.displayMetrics
        val heightPx = minOf((260 * dm.density).toInt(), (dm.heightPixels * 0.5f).toInt())
        view.setDesiredHeightPx(heightPx)
        view.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx,
        )
        keyboardView = view
        return view
    }

    /** Never take over the whole screen in landscape (stock-keyboard behavior). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * Controllers report the d-pad as hat-axis MOTION (not key events) on many
     * devices — route those into selection movement with edge detection.
     * Left stick also navigates.
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        event ?: return super.onGenericMotionEvent(event)
        // Ignore everything while hidden (the IME window still receives input
        // after requestHideSelf — without this guard keys "ghost type").
        if (dismissed || !isInputViewShown) return super.onGenericMotionEvent(event)
        val kb = keyboardView ?: return super.onGenericMotionEvent(event)

        // Raw axis dump (deduped) — diagnoses which axes the controller actually
        // drives. Some DS4 builds deliver d-pad LEFT/RIGHT on an unexpected axis.
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val sx = event.getAxisValue(MotionEvent.AXIS_X)
        val sy = event.getAxisValue(MotionEvent.AXIS_Y)
        val dump = "hatX=$hx hatY=$hy stickX=$sx stickY=$sy"
        if ((kotlin.math.abs(hx) > 0.3f || kotlin.math.abs(hy) > 0.3f ||
            kotlin.math.abs(sx) > 0.5f || kotlin.math.abs(sy) > 0.5f) && dump != lastAxisDump
        ) {
            Log.d(TAG, dump)
            lastAxisDump = dump
        }
        if (hx == 0f && hy == 0f) lastAxisDump = ""

        var handled = false
        if (hx <= -0.5f && lastHatX > -0.5f) { Log.d(TAG, "hat LEFT"); kb.moveSelection(0, -1); handled = true }
        if (hx >= 0.5f && lastHatX < 0.5f) { Log.d(TAG, "hat RIGHT"); kb.moveSelection(0, 1); handled = true }
        if (hy <= -0.5f && lastHatY > -0.5f) { Log.d(TAG, "hat UP"); kb.moveSelection(-1, 0); handled = true }
        if (hy >= 0.5f && lastHatY < 0.5f) { Log.d(TAG, "hat DOWN"); kb.moveSelection(1, 0); handled = true }
        lastHatX = hx; lastHatY = hy

        if (sx <= -0.5f && lastStickX > -0.5f) { Log.d(TAG, "stick LEFT"); kb.moveSelection(0, -1); handled = true }
        if (sx >= 0.5f && lastStickX < 0.5f) { Log.d(TAG, "stick RIGHT"); kb.moveSelection(0, 1); handled = true }
        if (sy <= -0.5f && lastStickY > -0.5f) { Log.d(TAG, "stick UP"); kb.moveSelection(-1, 0); handled = true }
        if (sy >= 0.5f && lastStickY < 0.5f) { Log.d(TAG, "stick DOWN"); kb.moveSelection(1, 0); handled = true }
        lastStickX = sx; lastStickY = sy

        return handled || super.onGenericMotionEvent(event)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        dismissed = false
        broadcastImeState(true)
        keyboardView?.layout = KeyboardView.Layout.LETTERS
        keyboardView?.shiftEnabled = false
        keyboardView?.capsLockEnabled = false
        keyboardView?.autoCap =
            (info?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
    }

    /** Gamepad keys arrive here whenever the IME has focus — no focus battles. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Hidden → let the system have everything (prevents ghost typing after B-hide).
        if (dismissed || !isInputViewShown) {
            Log.d(TAG, "key while hidden: ${KeyEvent.keyCodeToString(keyCode)}")
            return super.onKeyDown(keyCode, event)
        }
        Log.d(TAG, "key: ${KeyEvent.keyCodeToString(keyCode)}")
        keyboardView?.let { kb ->
            if (kb.onGamepadKeyDown(keyCode)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (dismissed || !isInputViewShown) return super.onKeyUp(keyCode, event)
        keyboardView?.let { kb ->
            if (kb.onGamepadKeyUp(keyCode)) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ---- KeyboardView.Listener ----

    override fun onKey(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    override fun onEnter() {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0
        val ic = currentInputConnection
        if (ic != null && action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            ic?.commitText("\n", 1)
        }
    }

    override fun onHide() {
        dismissed = true
        broadcastImeState(false)
        requestHideSelf(0)
    }

    override fun onDestroy() {
        broadcastImeState(false)
        super.onDestroy()
    }

    private fun broadcastImeState(shown: Boolean) {
        val intent = android.content.Intent(ACTION_IME_STATE)
            .setPackage(MOUSE_APP_PACKAGE)  // explicit: implicit custom broadcasts die on API 26+
            .putExtra(EXTRA_SHOWN, shown)
        runCatching { sendBroadcast(intent) }
        Log.d(TAG, "broadcast ime state shown=$shown")
    }
}
