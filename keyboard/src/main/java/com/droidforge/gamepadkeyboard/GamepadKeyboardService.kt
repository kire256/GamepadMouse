package com.droidforge.gamepadkeyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * A full keyboard IME driven entirely by a game controller.
 * Text goes to the focused field via InputConnection — the platform's real typing path.
 */
class GamepadKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var keyboardView: KeyboardView? = null

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        keyboardView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.layout = KeyboardView.Layout.LETTERS
        keyboardView?.shiftEnabled = false
        keyboardView?.capsLockEnabled =
            (info?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
    }

    /** Gamepad keys arrive here whenever the IME has focus — no focus battles. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        keyboardView?.let { kb ->
            if (kb.onGamepadKeyDown(keyCode)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
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
        requestHideSelf(0)
    }
}
