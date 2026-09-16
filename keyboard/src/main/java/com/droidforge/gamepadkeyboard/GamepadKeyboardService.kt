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

        return handled || super.onGenericMotionEvent(event)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        dismissed = false
        broadcastImeState(true)
        keyboardView?.page = KeyboardView.pageForInputType(info?.inputType ?: 0)
        keyboardView?.arrowsVisible = prefs.arrowsVisible
        keyboardView?.language = LanguagePack.fromCode(prefs.languageCode)
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

    // ---- suggestions ----------------------------------------------------------

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
        // Word = trailing run of letters (stop at space/punct — no composition state)
        val sb = StringBuilder()
        for (ch in before.reversed()) {
            if (ch.isLetter()) sb.append(ch) else break
        }
        return if (sb.isEmpty()) null else sb.reversed().toString()
    }

    private fun syncSuggestions() {
        val kb = keyboardView ?: return
        if (!prefs.suggestionsEnabled || currentWord() == null && suggester.previousWord == null) {
            kb.setSuggestions(emptyList())
            return
        }
        val frag = currentWord()
        kb.setSuggestions(suggester.stripCandidates(frag ?: ""))
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
     *  top suggestion instead (stock-keyboard behavior). */
    private fun finishWordAndSpace() {
        // Pinyin mode: space commits the top hanzi candidate (standard IME behavior)
        val composing = keyboardView?.pinyinBuffer
        if (!composing.isNullOrEmpty()) {
            val hanzi = pinyinCandidates()?.firstOrNull() ?: composing  // raw letters if no match
            commitPinyin(hanzi)
            return
        }
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
            currentInputConnection?.deleteSurroundingText(word.length, 0)
            currentInputConnection?.commitText("$replacement ", 1)
            learner.record(replacement, boost = 2)  // accepted correction = strong signal
        } else {
            currentInputConnection?.commitText(" ", 1)
            word?.let { learner.record(it) }
        }
        suggester.previousWord = replacement ?: word
        mainHandler.postDelayed(suggestionSync, 40)
    }

    // ---- KeyboardView.Listener ------------------------------------------------

    override fun onKey(text: String) {
        currentInputConnection?.commitText(text, 1)
        audio.tap()
        mainHandler.removeCallbacks(suggestionSync)
        mainHandler.postDelayed(suggestionSync, 40)
    }

    override fun onSpace() = finishWordAndSpace()

    override fun onBackspace() {
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
        // Enter = newline, always. (performEditorAction could close the editor —
        // e.g. IME_ACTION_DONE dismisses focus — which read as "keyboard closes".)
        currentWord()?.let {
            suggester.previousWord = it
            learner.record(it)
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
