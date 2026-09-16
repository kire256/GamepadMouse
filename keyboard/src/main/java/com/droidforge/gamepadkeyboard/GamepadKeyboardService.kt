package com.droidforge.gamepadkeyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest

/**
 * A full keyboard IME driven entirely by a game controller, styled after the
 * Steam Deck keyboard. Text goes to the focused field via InputConnection —
 * the platform's real typing path.
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

        /** Double-space → period recency window (Gboard uses ~300ms). */
        const val DOUBLE_SPACE_WINDOW_MS = 320L

        /** Hold X this long (auto-repeat period included) before deletes escalate to word chunks. */
        const val ESCALATE_AFTER_MS = 900L
    }

    private var keyboardView: KeyboardView? = null
    private lateinit var prefs: KeyboardPrefs
    private lateinit var suggester: Suggester
    private lateinit var learner: WordLearner
    private lateinit var audio: KeyboardAudio
    private lateinit var pinyin: PinyinEngine
    private var lastAxisDump = ""

    // ---- voice input ----
    private var speech: android.speech.SpeechRecognizer? = null
    private var listening = false

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // Suggestion refresh after each key: let the InputConnection settle first.
    private val suggestionSync = object : Runnable {
        override fun run() {
            syncSuggestions()
        }
    }

    // True while we deliberately hid the IME — isInputViewShown() lags/reads true
    // on some Samsung builds after requestHideSelf(), letting keys "ghost type".
    @Volatile
    private var dismissed = false

    // Hat-switch / left-stick edge tracking for selection movement
    private var lastHatX = 0f
    private var lastHatY = 0f
    private var lastStickX = 0f
    private var lastStickY = 0f
    private var lastRightDc = 0
    private var lastRightDr = 0

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPrefs(this)
        learner = WordLearner(this)
        suggester = Suggester(this, learner = learner)
        pinyin = PinyinEngine(this)
        audio = KeyboardAudio(this)
        audio.setPack(runCatching { SoundPack.valueOf(prefs.soundPackName) }.getOrDefault(SoundPack.CLASSIC))
    }

    override fun onDestroy() {
        broadcastImeState(false)
        runCatching { speech?.destroy() }
        speech = null
        audio.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        view.skin = runCatching { Skin.valueOf(prefs.skinName) }.getOrDefault(Skin.STEAM_DECK)
        view.hapticsEnabled = prefs.hapticsEnabled
        // Fixed height, bottom-docked — like a stock keyboard. Both the layout params
        // AND the view's own onMeasure assert the height; some devices stretch the
        // IME view to fill the screen otherwise.
        val dm = resources.displayMetrics
        val heightPx = minOf(
            (prefs.heightDp * dm.density).toInt() + (44 * dm.density).toInt(), // + suggestion strip
            (dm.heightPixels * 0.5f).toInt(),
        )
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
     * Left stick also navigates. Hold = repeat; center = stop.
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        event ?: return super.onGenericMotionEvent(event)
        // Ignore everything while hidden (the IME window still receives input
        // after requestHideSelf — without this guard keys "ghost type").
        if (dismissed || !isInputViewShown) return super.onGenericMotionEvent(event)
        val kb = keyboardView ?: return super.onGenericMotionEvent(event)

        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val sx = event.getAxisValue(MotionEvent.AXIS_X)
        val sy = event.getAxisValue(MotionEvent.AXIS_Y)

        var handled = false
        if (hx <= -0.5f && lastHatX > -0.5f) { kb.startDirectionalRepeat(0, -1); handled = true }
        if (hx >= 0.5f && lastHatX < 0.5f) { kb.startDirectionalRepeat(0, 1); handled = true }
        if (hy <= -0.5f && lastHatY > -0.5f) { kb.startDirectionalRepeat(-1, 0); handled = true }
        if (hy >= 0.5f && lastHatY < 0.5f) { kb.startDirectionalRepeat(1, 0); handled = true }
        // Back to center → stop the held-direction repeat
        if (hx > -0.5f && hx < 0.5f && (lastHatX <= -0.5f || lastHatX >= 0.5f)) kb.stopDirectionalRepeat()
        if (hy > -0.5f && hy < 0.5f && (lastHatY <= -0.5f || lastHatY >= 0.5f)) kb.stopDirectionalRepeat()
        lastHatX = hx; lastHatY = hy

        if (sx <= -0.5f && lastStickX > -0.5f) { kb.startDirectionalRepeat(0, -1); handled = true }
        if (sx >= 0.5f && lastStickX < 0.5f) { kb.startDirectionalRepeat(0, 1); handled = true }
        if (sy <= -0.5f && lastStickY > -0.5f) { kb.startDirectionalRepeat(-1, 0); handled = true }
        if (sy >= 0.5f && lastStickY < 0.5f) { kb.startDirectionalRepeat(1, 0); handled = true }
        if (sx > -0.5f && sx < 0.5f && (lastStickX <= -0.5f || lastStickX >= 0.5f)) kb.stopDirectionalRepeat()
        if (sy > -0.5f && sy < 0.5f && (lastStickY <= -0.5f || lastStickY >= 0.5f)) kb.stopDirectionalRepeat()
        lastStickX = sx; lastStickY = sy

        // Right stick LEVELS → radial offset (held direction highlights; release
        // returns to anchor). dc=-1/0/+1 rows, dr likewise.
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        val rdc = if (rx <= -0.5f) -1 else if (rx >= 0.5f) 1 else 0
        val rdr = if (ry <= -0.5f) -1 else if (ry >= 0.5f) 1 else 0
        if (rdc != lastRightDc || rdr != lastRightDr) {
            kb.setRightStickOffset(rdr, rdc)
            lastRightDc = rdc; lastRightDr = rdr
            handled = handled || (rdc != 0 || rdr != 0)
        }

        return handled || super.onGenericMotionEvent(event)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        keyboardView?.fieldlessMode = false
        super.onStartInputView(info, restarting)
        dismissed = false
        broadcastImeState(true)
        classifyField(info)
        keyboardView?.page = KeyboardView.pageForInputType(info?.inputType ?: 0)
        keyboardView?.arrowsVisible = prefs.arrowsVisible
        keyboardView?.compactMode = prefs.compactMode
        keyboardView?.fieldlessMode = currentInputConnection == null
        keyboardView?.language = LanguagePack.fromCode(prefs.languageCode)
        revertOriginal = null
        revertCorrected = null
        keyboardView?.shiftEnabled = false
        keyboardView?.capsLockEnabled = false
        keyboardView?.autoCap =
            (info?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        suggester.previousWord = null
        mainHandler.post(suggestionSync)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        mainHandler.removeCallbacks(suggestionSync)
        super.onFinishInputView(finishingInput)
    }

    /** Gamepad buttons arrive here whenever the IME has focus — no focus battles. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Hidden → let the system have everything (prevents ghost typing after B-hide).
        if (dismissed || !isInputViewShown) {
            Log.d(TAG, "key while hidden: ${KeyEvent.keyCodeToString(keyCode)}")
            return super.onKeyDown(keyCode, event)
        }
        // X = backspace: the FIRST press is a tap (never escalates); Android's
        // auto-repeat while held marks fromHold=true so escalating can kick in.
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            val fromHold = event?.repeatCount ?: 0 > 0
            keyboardView?.let { kb ->
                kb.flashKeyByLabel(KeyboardView.KEY_BACKSPACE)
            }
            onBackspace(fromHold)
            return true
        }
        // S = commit top suggestion (keyboard-page S key; only when not typing it)
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

    // ---- fieldless mode (keyboard up with NO focused editor, e.g. emulators) ---
    // toggleSoftInput can raise the IME without an InputConnection. Keystrokes then
    // buffer here (shown in the strip); Enter copies the buffer to the clipboard.

    private val fieldlessBuffer = StringBuilder()
    private val fieldless: Boolean get() = currentInputConnection == null && isInputViewShown

    private fun fieldlessKey(text: String) {
        fieldlessBuffer.append(text)
        keyboardView?.setSuggestions(listOf(fieldlessBuffer.toString()))
        audio.tap()
    }

    private fun fieldlessBackspace() {
        if (fieldlessBuffer.isNotEmpty()) fieldlessBuffer.deleteCharAt(fieldlessBuffer.length - 1)
        keyboardView?.setSuggestions(
            if (fieldlessBuffer.isEmpty()) emptyList() else listOf(fieldlessBuffer.toString())
        )
        audio.tap()
    }

    private fun fieldlessEnter() {
        val text = fieldlessBuffer.toString()
        if (text.isEmpty()) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("typed", text))
        android.widget.Toast.makeText(
            this, "Copied: $text", android.widget.Toast.LENGTH_SHORT
        ).show()
        fieldlessBuffer.clear()
        keyboardView?.setSuggestions(emptyList())
        audio.enter()
    }

    // ---- suggestions ----------------------------------------------------------

    /** Undo state for autocorrect: the word as typed vs what replaced it. */
    private var revertOriginal: String? = null
    private var revertCorrected: String? = null

    /** Recency window for double-space → period (only converts a space we just typed). */
    private var lastSpaceCommitAt = 0L

    // Escalating backspace bookkeeping
    private var bsHoldStart = 0L
    private var bsLastAt = 0L
    private var bsEscalated = false

    /** Privacy state, classified per field in onStartInput. */
    private var secureField = false
    private var noLearning = false

    /** Password/privacy classification for the current editor. */
    private fun classifyField(info: android.view.inputmethod.EditorInfo?) {
        val t = info?.inputType ?: 0
        val cls = t and android.text.InputType.TYPE_MASK_CLASS
        val vrn = t and android.text.InputType.TYPE_MASK_VARIATION
        secureField =
            (cls == android.text.InputType.TYPE_CLASS_TEXT && vrn in intArrayOf(
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            )) || (cls == android.text.InputType.TYPE_CLASS_NUMBER &&
                vrn == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        // Apps may also forbid personalized learning generically (IME_FLAG_NO_PERSONALIZED_LEARNING)
        noLearning = secureField ||
            (info?.imeOptions != null &&
                (info.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)
        Log.d(TAG, "field privacy: secure=$secureField noLearning=$noLearning")
    }

    /** Pinyin candidates for the current buffer, or null when not composing. */
    private fun pinyinCandidates(): List<String>? {
        val buf = keyboardView?.pinyinBuffer ?: return null
        if (buf.isEmpty()) return null
        return pinyin.candidates(buf)
    }

    /** Commit hanzi for the given pinyin buffer and clear composing state. */
    private fun commitPinyin(hanzi: String) {
        currentInputConnection?.commitText(hanzi, 1)
        keyboardView?.clearPinyinBuffer()
    }

    private fun currentWord(): String? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(48, 0) ?: return null
        if (before.isEmpty()) return null
        // Word = trailing run of letters/apostrophes (don't, can't) — stops at other
        // punctuation/space. A trailing apostrophe isn't part of the word yet.
        val sb = StringBuilder()
        for (ch in before.reversed()) {
            if (ch.isLetter() || (ch == '\'' && sb.isNotEmpty())) sb.append(ch) else break
        }
        var word = sb.reversed().toString()
        while (word.endsWith("'")) word = word.dropLast(1)
        return if (word.isEmpty()) null else word
    }

    private fun syncSuggestions() {
        val kb = keyboardView ?: return
        // Privacy: no suggestion strip on password / no-learning fields
        if (secureField || noLearning) {
            kb.setSuggestions(emptyList())
            return
        }
        pinyinCandidates()?.let { cands ->
            kb.setSuggestions(cands)
            return
        }
        if (!prefs.suggestionsEnabled || currentWord() == null && suggester.previousWord == null) {
            kb.setSuggestions(emptyList())
            return
        }
        val frag = currentWord()
        if (frag == null) {
            // Sentence starter prediction: field start or right after . ! ? \n
            val before1 = currentInputConnection?.getTextBeforeCursor(1, 0)
            val sentenceStart = before1.isNullOrEmpty() || before1[0] in ".!?\n"
            if (sentenceStart && keyboardView?.page == KeyboardView.Page.LETTERS) {
                kb.setSuggestions(listOf("The", "I", "It"))
                return
            }
            kb.setSuggestions(emptyList())
            return
        }
        kb.setSuggestions(suggester.stripCandidates(frag))
        kb.learnedWords = learner.all().keys.toSet()
    }

    /** Long-press on a learned strip word: remove it from the dictionary. */
    override fun onForgetWord(word: String) {
        learner.forget(word)
        keyboardView?.learnedWords = learner.all().keys.toSet()
        mainHandler.removeCallbacks(suggestionSync)
        mainHandler.postDelayed(suggestionSync, 30)
        Log.i(TAG, "forgot learned word: $word")
    }

    /** Replace the in-flight fragment with [word] + trailing space; learn it. */
    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val frag = currentWord()
        if (frag != null) ic.deleteSurroundingText(frag.length, 0)
        ic.commitText("$word ", 1)
        learner.record(word, boost = 2)  // explicit pick = stronger signal
        suggester.previousWord = word
        keyboardView?.setSuggestions(emptyList())
    }

    /** Finish the current word with a space and learn it (space = confirmation).
     *  Autocorrect: a non-dictionary word with available completions commits the
     *  top suggestion instead (stock-keyboard behavior).
     *  Double-space → ". " when the previous space was ours and recent. */
    private fun finishWordAndSpace() {
        // Pinyin mode: space commits the top hanzi candidate (standard IME behavior)
        val composing = keyboardView?.pinyinBuffer
        if (!composing.isNullOrEmpty()) {
            val hanzi = pinyinCandidates()?.firstOrNull() ?: composing  // raw letters if no match
            commitPinyin(hanzi)
            return
        }
        // Privacy fields: no autocorrect, no double-space period, no learning
        if (noLearning) {
            revertOriginal = null
            revertCorrected = null
            lastSpaceCommitAt = 0L
            currentInputConnection?.commitText(" ", 1)
            return
        }
        // Double-space → period (Gboard-style): previous space must be recent + ours
        val now = android.os.SystemClock.uptimeMillis()
        if (lastSpaceCommitAt > 0 && now - lastSpaceCommitAt <= DOUBLE_SPACE_WINDOW_MS) {
            val before = currentInputConnection?.getTextBeforeCursor(1, 0)
            if (before == " ") {
                currentInputConnection?.deleteSurroundingText(1, 0)
                currentInputConnection?.commitText(". ", 1)
                lastSpaceCommitAt = 0L
                audio.enter()
                return
            }
        }
        lastSpaceCommitAt = 0L
        val word = currentWord()  // capture BEFORE the space lands
        var replacement: String? = null
        if (word != null && prefs.suggestionsEnabled &&
            word.length >= 3 && !suggester.knows(word)
        ) {
            val lower = word.lowercase()
            // 1) prefix completion of what was typed  2) nearest dictionary word
            replacement = suggester.suggest(lower).firstOrNull { it != word }
                ?: suggester.bestCorrection(word)
        }
        if (replacement != null && word != null) {
            // Case-preserving: TEH → THE, Teh → The, teh → the
            val fixed = when {
                word.length > 1 && word[1].isUpperCase() -> replacement.uppercase()
                word[0].isUpperCase() ->
                    replacement.replaceFirstChar { it.uppercase() } + replacement.drop(1)
                else -> replacement
            }
            currentInputConnection?.deleteSurroundingText(word.length, 0)
            currentInputConnection?.commitText("$fixed ", 1)
            learner.record(replacement, boost = 2)  // accepted correction = strong signal
            // Arm undo: next backspace restores the word as typed
            revertOriginal = word
            revertCorrected = fixed
            lastSpaceCommitAt = android.os.SystemClock.uptimeMillis()
        } else {
            revertOriginal = null
            revertCorrected = null
            currentInputConnection?.commitText(" ", 1)
            word?.let { learner.record(it) }
            lastSpaceCommitAt = android.os.SystemClock.uptimeMillis()
        }
        suggester.previousWord = replacement ?: word
        mainHandler.postDelayed(suggestionSync, 40)
    }

    // ---- KeyboardView.Listener ------------------------------------------------

    override fun onKey(text: String) {
        if (fieldless) {
            // Route text into the fieldless buffer; Enter copies it to the clipboard
            text.forEach { fieldlessKey(it.toString()) }
            return
        }
        val ic = currentInputConnection
        // Smart punctuation: -- → —, (c) → ©, (r) → ®, (tm) → ™
        if (ic != null) {
            val before1 = ic.getTextBeforeCursor(1, 0)
            val before3 = ic.getTextBeforeCursor(3, 0)
            when {
                text == "-" && before1 == "-" -> {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText("\u2014", 1)  // —
                    audio.tap()
                    scheduleSuggestionSync()
                    return
                }
                text == ")" && before3 == "(c" -> {
                    ic.deleteSurroundingText(3, 0); ic.commitText("\u00A9", 1)
                    audio.tap(); scheduleSuggestionSync(); return
                }
                text == ")" && before3 == "(r" -> {
                    ic.deleteSurroundingText(3, 0); ic.commitText("\u00AE", 1)
                    audio.tap(); scheduleSuggestionSync(); return
                }
                text == ")" && before3 == "(tm" -> {
                    ic.deleteSurroundingText(3, 0); ic.commitText("\u2122", 1)
                    audio.tap(); scheduleSuggestionSync(); return
                }
            }
        }
        if (text.length == 1 && text[0] in ".!?" && keyboardView?.capsLockEnabled != true) {
            keyboardView?.autoCap = true
        }
        ic?.commitText(text, 1)
        audio.tap()
        scheduleSuggestionSync()
    }

    private fun scheduleSuggestionSync() {
        mainHandler.removeCallbacks(suggestionSync)
        mainHandler.postDelayed(suggestionSync, 40)
    }

    override fun onSpace() {
        if (fieldless) {
            fieldlessKey(" ")
            return
        }
        finishWordAndSpace()
    }

    override fun onBackspace(fromHold: Boolean) {
        // Fieldless mode: backspace edits the buffer (no escalation — nothing to chew)
        if (fieldless) {
            fieldlessBackspace()
            return
        }
        // Undo an autocorrection: one backspace restores the word as typed
        val orig = revertOriginal
        val corr = revertCorrected
        if (orig != null && corr != null) {
            val before = currentInputConnection?.getTextBeforeCursor(corr.length + 1, 0)
            if (before == "$corr ") {
                currentInputConnection?.deleteSurroundingText(corr.length + 1, 0)
                currentInputConnection?.commitText(orig, 1)
                learner.record(orig)  // teaches the dictionary your intended word
                revertOriginal = null
                revertCorrected = null
                suggester.previousWord = orig
                mainHandler.removeCallbacks(suggestionSync)
                mainHandler.postDelayed(suggestionSync, 30)
                return
            }
            // Cursor moved elsewhere — correction is no longer adjacent, drop undo
            revertOriginal = null
            revertCorrected = null
        }
        // Escalating backspace: HOLD-only (tap or repeated taps never escalate).
        // Android auto-repeats X while held (fromHold=true) — after the escalation
        // delay, deletes switch to word chunks until the key is released.
        val now = android.os.SystemClock.uptimeMillis()
        if (!fromHold) {
            bsHoldStart = 0L
            bsEscalated = false
        } else {
            if (bsHoldStart == 0L) bsHoldStart = now
        }
        bsLastAt = now
        val holdLongEnough = fromHold &&
            now - bsHoldStart >= ESCALATE_AFTER_MS &&
            prefs.escalatingBackspace
        if (holdLongEnough) {
            bsEscalated = true
            val ic = currentInputConnection
            val before = ic?.getTextBeforeCursor(12, 0)
            if (ic != null && !before.isNullOrEmpty()) {
                val chunk = if (before.endsWith(" ") || !before.contains(' ')) {
                    minOf(12, before.length)
                } else {
                    before.length - before.lastIndexOf(' ') - 1  // up to word start
                }
                if (chunk > 0) ic.deleteSurroundingText(chunk, 0)
            }
            audio.tap()
            return
        }
        // Composing pinyin? Backspace eats a composer letter before touching text
        if (keyboardView?.popPinyinChar() == true) {
            mainHandler.removeCallbacks(suggestionSync)
            mainHandler.postDelayed(suggestionSync, 30)
            return
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        audio.tap()
        mainHandler.removeCallbacks(suggestionSync)
        mainHandler.postDelayed(suggestionSync, 40)
    }

    override fun onEnter() {
        // Fieldless (no focused editor): Enter copies the buffered text to the
        // clipboard so it can be pasted into any app afterwards.
        if (fieldless) {
            fieldlessEnter()
            return
        }
        // Enter = newline, always. (performEditorAction could close the editor —
        // e.g. IME_ACTION_DONE dismisses focus — which read as "keyboard closes".)
        if (!noLearning) {
            currentWord()?.let {
                suggester.previousWord = it
                learner.record(it)
            }
        }
        currentInputConnection?.commitText("\n", 1)
        audio.enter()
    }

    override fun onHide() {
        dismissed = true
        broadcastImeState(false)
        keyboardView?.stopDirectionalRepeat()
        requestHideSelf(0)
    }

    override fun onSuggestionPick(word: String) {
        // In pinyin mode a strip pick is hanzi for the composed buffer
        if (keyboardView?.pinyinBuffer?.isNotEmpty() == true) {
            commitPinyin(word)
        } else {
            commitSuggestion(word)
            // The pick provided its own capitalization (e.g. sentence starters)
            keyboardView?.autoCap = false
        }
    }

    override fun onEditorKey(keyCode: Int) {
        val ic = currentInputConnection ?: return
        fun key(code: Int) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        when (keyCode) {
            KeyEvent.KEYCODE_FORWARD_DEL -> ic.deleteSurroundingText(0, 1)
            KeyEvent.KEYCODE_MOVE_HOME -> key(KeyEvent.KEYCODE_MOVE_HOME)
            KeyEvent.KEYCODE_MOVE_END -> key(KeyEvent.KEYCODE_MOVE_END)
            KeyEvent.KEYCODE_PAGE_UP -> key(KeyEvent.KEYCODE_PAGE_UP)
            KeyEvent.KEYCODE_PAGE_DOWN -> key(KeyEvent.KEYCODE_PAGE_DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> key(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> key(KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyEvent.KEYCODE_DPAD_UP -> key(KeyEvent.KEYCODE_DPAD_UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> key(KeyEvent.KEYCODE_DPAD_DOWN)
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> key(keyCode)
        }
        audio.tap()
    }

    override fun onSelectAll() {
        val ic = currentInputConnection ?: return
        val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        ic.setSelection(0, et.text?.length ?: 0)
        audio.tap()
    }

    override fun onCopy() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected != null) {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("text", selected))
        }
        audio.tap()
    }

    override fun onCut() {
        onCopy()
        currentInputConnection?.deleteSurroundingText(0, 0)  // no-op guard
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) ic.commitText("", 1)
        audio.tap()
    }

    override fun onPaste() {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)
        if (!clip.isNullOrEmpty()) {
            currentInputConnection?.commitText(clip, 1)
            audio.enter()
        }
    }

    override fun onPinyinChanged(buffer: String) {
        mainHandler.removeCallbacks(suggestionSync)
        mainHandler.postDelayed(suggestionSync, 30)
    }

    override fun onLanguageToggle() {
        val packs = LanguagePack.entries
        val next = packs[(packs.indexOf(LanguagePack.fromCode(prefs.languageCode)) + 1) % packs.size]
        prefs.languageCode = next.code
        keyboardView?.language = next
        keyboardView?.clearPinyinBuffer()
        android.widget.Toast.makeText(
            this, next.label, android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onOpenOptions() {
        val intent = android.content.Intent(this, OptionsActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // Keep the IME open underneath; the options screen floats above it.
    }

    // ---- voice input ----------------------------------------------------------

    private fun micPermissionGranted(): Boolean =
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onMicInput() {
        // Privacy: never listen on password / no-learning fields
        if (noLearning) {
            android.widget.Toast.makeText(
                this, "Voice input is off for private fields", android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (!micPermissionGranted()) {
            // IMEs can't show permission dialogs — Options requests it.
            android.widget.Toast.makeText(
                this, "Grant microphone in Keyboard Options", android.widget.Toast.LENGTH_LONG,
            ).show()
            onOpenOptions()
            return
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            android.widget.Toast.makeText(
                this, "No speech recognition service on this device", android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (listening) {  // mic key toggles listening off
            stopListening()
            return
        }
        val recognizer = speech ?: android.speech.SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(recognitionListener)
            speech = it
        }
        recognizer.startListening(
            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            },
        )
    }

    private fun stopListening() {
        runCatching { speech?.stopListening() }
    }

    private fun setMicUi(listeningNow: Boolean) {
        listening = listeningNow
        keyboardView?.micListening = listeningNow
    }

    private val recognitionListener = object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) = setMicUi(true)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = setMicUi(false)
        override fun onError(error: Int) {
            setMicUi(false)
            val msg = when (error) {
                android.speech.SpeechRecognizer.ERROR_NO_MATCH -> null  // silence: user said nothing
                android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Heard nothing"
                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
                else -> "Voice error $error"
            }
            msg?.let {
                android.widget.Toast.makeText(this@GamepadKeyboardService, it, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            setMicUi(false)
            val text = results
                ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                currentInputConnection?.commitText("$text ", 1)
                audio.enter()
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

    // ---- prefs refresh (options screen may have changed things) ---------------

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        keyboardView?.skin = runCatching { Skin.valueOf(prefs.skinName) }.getOrDefault(Skin.STEAM_DECK)
        keyboardView?.hapticsEnabled = prefs.hapticsEnabled
        audio.setPack(runCatching { SoundPack.valueOf(prefs.soundPackName) }.getOrDefault(SoundPack.CLASSIC))
    }

    // ---- broadcast ------------------------------------------------------------

    private fun broadcastImeState(shown: Boolean) {
        val intent = android.content.Intent(ACTION_IME_STATE)
            .setPackage(MOUSE_APP_PACKAGE)  // explicit: implicit custom broadcasts die on API 26+
            .putExtra(EXTRA_SHOWN, shown)
        runCatching { sendBroadcast(intent) }
        Log.d(TAG, "broadcast ime state shown=$shown")
    }
}
