package com.droidforge.gamepadkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Gamepad-navigable on-screen keyboard styled after the STEAM DECK virtual keyboard:
 * beveled olive-style keycaps (per-skin palettes), cream legends, full-width side
 * keys, badged ⌫(X) / ⏎(Start) / ⇧(Y), top suggestion strip, bottom bar with
 * page-cycle · space · ◀ ▶ · hide, and hint strip showing state + version.
 *
 * Pages: LETTERS, NUMBERS, SYMBOLS, EMOJI, FN (F-keys, arrows, clipboard, nav).
 *
 * Gamepad mapping (Erik's spec, v3):
 *  - D-pad / left stick: move selection (hold = repeat); up from top row = suggestion strip
 *  - A: press selected key / commit selected suggestion   B: close keyboard
 *  - X: backspace   Y: shift   LB/RB: cycle pages   Start: Enter   Select: close
 *  - S (keyboard key): commit top suggestion
 *
 * Touch works too: tapping a key presses it directly.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(text: String)
        fun onSpace()
        fun onBackspace()
        fun onEnter()
        fun onHide()
        fun onSuggestionPick(word: String)
        fun onEditorKey(keyCode: Int)
        fun onSelectAll()
        fun onCopy()
        fun onCut()
        fun onPaste()
        fun onMicInput()
        fun onOpenOptions()
    }

    var listener: Listener? = null

    enum class Page(val tabLabel: String) {
        LETTERS("ABC"), NUMBERS("0-9"), SYMBOLS("@#:"), EMOJI(":-)"), FN("Fn")
    }

    companion object {
        // Action key labels (not text)
        const val KEY_SHIFT = "\u21E7"        // ⇧
        const val KEY_CAPS = "Caps"
        const val KEY_TAB = "Tab"
        const val KEY_BACKSPACE = "\u232B"    // ⌫
        const val KEY_ENTER = "\u23CE"        // ⏎
        const val KEY_SPACE = " "
        const val KEY_PAGES = "\u21C4"        // ⇄ cycle pages
        const val KEY_LEFT = "\u25C0"         // ◀
        const val KEY_RIGHT = "\u25B6"        // ▶
        const val KEY_HIDE = "\u2328"         // ⌨ hide
        const val KEY_MIC = "\uD83C\uDFA4"    // 🎤 voice input
        const val KEY_SUGGEST = "S"
        const val KEY_CUT = "Cut"
        const val KEY_COPY = "Copy"
        const val KEY_PASTE = "Paste"
        const val KEY_SELALL = "All"
        const val KEY_DEL = "\u2326"          // ⌦ forward delete
        const val KEY_HOME = "\u21F1"         // ⇱
        const val KEY_END = "\u21F2"          // ⇲
        const val KEY_PGUP = "\u21DE"         // ⇞
        const val KEY_PGDOWN = "\u21DF"       // ⇟
        const val KEY_EMOJI_PAGE = "\uD83D\uDE04"  // 😀 page key
        const val KEY_FN_PAGE = "Fn"
        const val KEY_OPTIONS = "\u2699"      // ⚙

        /** Shown in the hint strip so on-device builds are always identifiable. */
        const val DISPLAY_VERSION = "v0.2.7"
        private const val TAG = "GPKeyboard"
        private val REPEAT_DELAY_MS = 400L
        private val REPEAT_RATE_MS = 60L
        private const val FLASH_MS = 160L

        val EMOJI_ROWS: List<List<String>> = listOf(
            listOf("😀","😂","🙂","😉","😍","😘","😜","🤔","😐","😴","😎","🤩","🥳"),
            listOf("😢","😭","😤","😡","🤯","😱","🥶","🥵","🤒","🤕","🤧","🥺","😏"),
            listOf("👍","👎","👏","🙌","🤝","✌️","🤞","🤙","💪","🙏","👋","🫡","🤌"),
            listOf("❤️","💙","💚","💜","🖤","🔥","✨","⭐","🎉","🎮","🍕","☕","💀"),
        )

        /** Numeric-only field classes auto-open the numeric pad page. */
        fun pageForInputType(inputType: Int): Page {
            val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
            return when (cls) {
                android.text.InputType.TYPE_CLASS_NUMBER,
                android.text.InputType.TYPE_CLASS_PHONE,
                android.text.InputType.TYPE_CLASS_DATETIME,
                -> Page.NUMBERS
                else -> Page.LETTERS
            }
        }
    }

    // ---- palette (from Skin) --------------------------------------------------
    var skin: Skin = Skin.STEAM_DECK
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint().apply { color = skin.bg }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ---- model ----------------------------------------------------------------

    /** One key: label + grid width weight (1 = normal cell) + optional button badge. */
    data class Key(
        val label: String,
        val weight: Float = 1f,
        val isAction: Boolean = false,
        val badge: String? = null,
    )

    private fun backspaceKey(w: Float = 1.5f) = Key(KEY_BACKSPACE, w, true, badge = "X")
    private fun enterKey(w: Float = 1.7f) = Key(KEY_ENTER, w, true, badge = "S")
    private fun shiftKey(w: Float = 1.7f) = Key(KEY_SHIFT, w, true, badge = "Y")
    private fun emojiKey(e: String) = Key(e)
    private fun fKey(n: Int) = Key("F$n", 1f, true)

    /** Bottom bar, Deck-style: pages · wide space · ◀ ▶ · mic · hide · options. */
    private fun barRow(): List<Key> = listOf(
        Key(KEY_PAGES, 1.4f, true),
        Key(KEY_SPACE, 5.2f, true),
        Key(KEY_LEFT, 1.1f, true),
        Key(KEY_RIGHT, 1.1f, true),
        Key(KEY_MIC, 1.3f, true),
        Key(KEY_HIDE, 1.3f, true),
        Key(KEY_OPTIONS, 1.1f, true),
    )

    internal val letterRows = listOf(
        listOf(Key("`"),Key("1"),Key("2"),Key("3"),Key("4"),Key("5"),Key("6"),Key("7"),Key("8"),Key("9"),Key("0"),Key("-"),Key("="), backspaceKey()),
        listOf(Key(KEY_TAB,1.6f,true),Key("q"),Key("w"),Key("e"),Key("r"),Key("t"),Key("y"),Key("u"),Key("i"),Key("o"),Key("p"),Key("["),Key("]"),Key("\\")),
        listOf(Key(KEY_CAPS,1.7f,true),Key("a"),Key("s"),Key("d"),Key("f"),Key("g"),Key("h"),Key("j"),Key("k"),Key("l"),Key(";"),Key("'"), enterKey()),
        listOf(shiftKey(),Key("z"),Key("x"),Key("c"),Key("v"),Key("b"),Key("n"),Key("m"),Key(","),Key("."),Key("/"), shiftKey()),
        barRow(),
    )

    /** Dedicated numeric pad: calculator-style 789 top, big digits, math + separators.
     *  Auto-selected when a field's inputType is number/phone/datetime. */
    private val numberRows = listOf(
        listOf(Key("7"), Key("8"), Key("9"), backspaceKey(1.6f)),
        listOf(Key("4"), Key("5"), Key("6"), Key("+", 1.2f), Key("-", 1.2f)),
        listOf(Key("1"), Key("2"), Key("3"), Key("×", 1.2f), Key("÷", 1.2f)),
        listOf(Key(",", 1.2f), Key("0", 2.2f), Key(".", 1.2f), Key(":", 1.2f), enterKey(1.6f)),
        barRow(),
    )

    private val symbolRows = listOf(
        listOf(Key("€"),Key("£"),Key("¥"),Key("¢"),Key("°"),Key("•"),Key("¡"),Key("¿"),Key("«"),Key("»"),Key("§"), backspaceKey()),
        listOf(Key(KEY_TAB,1.6f,true),Key("à"),Key("á"),Key("é"),Key("è"),Key("í"),Key("ó"),Key("ú"),Key("ü"),Key("ñ"),Key("ç"),Key("ß")),
        listOf(Key(KEY_EMOJI_PAGE,1.7f,true),Key("ā"),Key("ē"),Key("ī"),Key("ō"),Key("ū"),Key("â"),Key("ê"),Key("î"),Key("ô"),Key("û"), enterKey()),
        listOf(shiftKey(),Key("Ã"),Key("Æ"),Key("Ø"),Key("Å"),Key("Œ"),Key("Þ"),Key("Ð"),Key("Ý"),Key("Λ"),Key("Ω"), shiftKey()),
        barRow(),
    )

    private val emojiRows: List<List<Key>> = EMOJI_ROWS.map { row -> row.map { emojiKey(it) } } +
        listOf(listOf(Key(KEY_FN_PAGE, 2f, true), Key(KEY_SPACE, 8f, true), Key(KEY_ENTER, 2f, true, badge = "S")))

    private val fnRows = listOf(
        listOf(Key("\u241B",1f,true)) + (1..12).map { fKey(it) } + backspaceKey(1f),
        listOf(
            Key(KEY_SELALL,1.5f,true), Key(KEY_CUT,1.4f,true), Key(KEY_COPY,1.5f,true), Key(KEY_PASTE,1.6f,true),
            Key(KEY_HOME,1.3f,true), Key(KEY_END,1.3f,true), Key(KEY_PGUP,1.3f,true), Key(KEY_PGDOWN,1.3f,true),
            Key("←",1.3f,true), Key("↓",1.3f,true), Key("↑",1.3f,true), Key("→",1.3f,true),
            Key(KEY_DEL,1.4f,true),
        ),
        barRow(),
    )

    var page: Page = Page.LETTERS
        set(value) { field = value; normalizeSelection(); invalidate() }
    var shiftEnabled = false
        set(value) { field = value; invalidate() }
    var capsLockEnabled = false
        set(value) { field = value; invalidate() }
    /** One-shot auto-capitalize from the field's CAP_SENTENCES flag (first letter only). */
    var autoCap = false
        set(value) { field = value; invalidate() }

    /** Haptics on/off (user preference; applied by the service). */
    var hapticsEnabled = true

    /** True while the service is listening to speech — mic key pulses amber. */
    var micListening = false
        set(value) { field = value; invalidate() }

    // ---- selection state ------------------------------------------------------

    private var selectedRow = 1
    private var selectedCol = 0
    /** Highlight marks the GAMEPAD cursor; touch taps press keys but leave no highlight. */
    private var highlightVisible = true

    /** Current suggestion candidates (set by the service). Row -1 selects these. */
    private var suggestions: List<String> = emptyList()

    private var desiredHeightPx = -1

    // Key-repeat while a d-pad direction is held
    private var repeatRunnable: Runnable? = null
    private var repeatDirection: Pair<Int, Int>? = null

    // Press feedback: key under the finger (live) + brief pulse on activation
    private var pressedRow = -1
    private var pressedCol = -1
    private var flashRow = -1
    private var flashCol = -1
    private var flashRunnable: Runnable? = null

    // Touch hold-to-repeat (like physical keyboards: hold ⌫ / a letter → repeats)
    private var holdRunnable: Runnable? = null
    private var didRepeat = false

    fun setDesiredHeightPx(px: Int) {
        desiredHeightPx = px
        requestLayout()
    }

    private fun stripHeightPx(): Float =
        if (suggestions.isEmpty()) 0f else 44f * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = if (desiredHeightPx > 0) {
            // Suggestion strip ADDS height instead of stealing it from the key rows
            resolveSize(desiredHeightPx + stripHeightPx().toInt(), heightMeasureSpec)
        } else getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    // ---- grid model -----------------------------------------------------------

    /** Show the ◀ ▶ cursor keys on the bottom bar (user preference). */
    var arrowsVisible = true
        set(value) {
            field = value
            normalizeSelection()
            invalidate()
        }

    private fun grid(): List<List<Key>> {
        val base = when (page) {
            Page.LETTERS -> letterRows
            Page.NUMBERS -> numberRows
            Page.SYMBOLS -> symbolRows
            Page.EMOJI -> emojiRows
            Page.FN -> fnRows
        }
        if (arrowsVisible) return base
        return base.map { row -> row.filter { it.label != KEY_LEFT && it.label != KEY_RIGHT } }
    }

    private fun normalizeSelection() {
        val g = grid()
        selectedRow = selectedRow.coerceIn(0, g.lastIndex)
        selectedCol = selectedCol.coerceIn(0, g[selectedRow].lastIndex)
    }

    /** Service pushes fresh candidates; selection may sit on the strip (row -1). */
    fun setSuggestions(list: List<String>) {
        val had = suggestions.isNotEmpty()
        suggestions = list
        if (list.isEmpty() && selectedRow == -1) { selectedRow = 0; selectedCol = 0 }
        if (had != list.isNotEmpty()) requestLayout()  // strip appears/disappears → re-measure
        invalidate()
    }

    fun moveSelection(dRow: Int, dCol: Int) {
        val g = grid()
        if (dRow != 0) {
            if (selectedRow == -1) {
                if (dRow > 0) { selectedRow = 0; selectedCol = selectedCol.coerceIn(0, g[0].lastIndex) }
            } else if (selectedRow == 0 && dRow < 0 && suggestions.isNotEmpty()) {
                selectedRow = -1
                selectedCol = selectedCol.coerceAtMost(suggestions.lastIndex)
            } else {
                selectedRow = (selectedRow + dRow).coerceIn(0, g.lastIndex)
                selectedCol = selectedCol.coerceIn(0, g[selectedRow].lastIndex)
            }
        }
        if (dCol != 0) {
            if (selectedRow == -1) {
                selectedCol = (selectedCol + dCol).coerceIn(0, suggestions.lastIndex)
            } else {
                selectedCol = (selectedCol + dCol).coerceIn(0, g[selectedRow].lastIndex)
            }
        }
        highlightVisible = true
        invalidate()
    }

    fun selectedKey(): String = grid()[selectedRow][selectedCol].label

    fun pressSelectedKey() {
        if (selectedRow == -1) {
            val word = suggestions.getOrNull(selectedCol) ?: suggestions.firstOrNull() ?: return
            flashSuggestion(selectedCol)
            listener?.onSuggestionPick(word)
            return
        }
        flashSelectedKey()
        pressKey(selectedKey())
    }

    /** S keyboard key: commit the top completion (service resolves the word). */
    fun pressTopSuggestion() {
        val word = suggestions.firstOrNull() ?: return
        flashSuggestion(0)
        listener?.onSuggestionPick(word)
    }

    // ---- press feedback -------------------------------------------------------

    private fun flashCell(r: Int, c: Int) {
        flashRunnable?.let { removeCallbacks(it) }
        flashRow = r; flashCol = c
        if (hapticsEnabled) hapticTick()
        invalidate()
        val clear = Runnable { flashRow = -1; flashCol = -1; invalidate() }
        flashRunnable = clear
        postDelayed(clear, FLASH_MS)
    }

    private var flashSug = -1

    private fun flashSuggestion(index: Int) {
        flashRunnable?.let { removeCallbacks(it) }
        flashRow = -2; flashCol = -1; flashSug = index
        if (hapticsEnabled) hapticTick()
        invalidate()
        val clear = Runnable { flashRow = -1; flashSug = -1; invalidate() }
        flashRunnable = clear
        postDelayed(clear, FLASH_MS)
    }

    private fun flashSelectedKey() = flashCell(selectedRow, selectedCol)

    private fun flashKeyByLabel(label: String) {
        val g = grid()
        for ((r, row) in g.withIndex()) {
            val c = row.indexOfFirst { it.label == label }
            if (c >= 0) { flashCell(r, c); return }
        }
    }

    /** Keys that sensibly repeat when held on touch. */
    private fun keyRepeats(label: String): Boolean =
        label == KEY_BACKSPACE || label == "←" || label == "→" || label == "↑" || label == "↓" ||
            (label.length == 1 && label != KEY_SPACE)

    private fun cancelHold() {
        holdRunnable?.let { removeCallbacks(it) }
        holdRunnable = null
        didRepeat = false
    }

    /** Schedule press-repeat for the pressed cell (no-op for non-repeating keys). */
    private fun startHoldIfRepeatable(r: Int, c: Int) {
        cancelHold()
        val label = grid().getOrNull(r)?.getOrNull(c)?.label ?: return
        if (!keyRepeats(label)) return
        val run = object : Runnable {
            override fun run() {
                if (pressedRow != r || pressedCol != c) return  // finger moved away
                pressKey(label)
                if (hapticsEnabled) hapticTick()
                didRepeat = true
                postDelayed(this, REPEAT_RATE_MS)
            }
        }
        holdRunnable = run
        postDelayed(run, REPEAT_DELAY_MS)
    }

    private fun hapticTick() {
        runCatching {
            performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
    }

    // ---- key dispatch ---------------------------------------------------------

    fun pressKey(label: String) {
        when (label) {
            KEY_SHIFT -> shiftEnabled = !shiftEnabled
            KEY_CAPS -> capsLockEnabled = !capsLockEnabled
            KEY_TAB -> listener?.onKey("\t")
            KEY_BACKSPACE -> listener?.onBackspace()
            KEY_ENTER -> { listener?.onEnter(); consumeOneShotShift() }
            KEY_SPACE -> { listener?.onSpace(); consumeOneShotShift() }
            KEY_PAGES -> cyclePage(1)
            KEY_EMOJI_PAGE -> page = Page.EMOJI
            KEY_FN_PAGE -> page = Page.FN
            KEY_LEFT -> listener?.onEditorKey(KeyEvent.KEYCODE_DPAD_LEFT)
            KEY_RIGHT -> listener?.onEditorKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            KEY_HIDE -> listener?.onHide()
            KEY_MIC -> listener?.onMicInput()
            KEY_OPTIONS -> listener?.onOpenOptions()
            KEY_SUGGEST -> pressTopSuggestion()
            KEY_SELALL -> listener?.onSelectAll()
            KEY_CUT -> listener?.onCut()
            KEY_COPY -> listener?.onCopy()
            KEY_PASTE -> listener?.onPaste()
            KEY_DEL -> listener?.onEditorKey(KeyEvent.KEYCODE_FORWARD_DEL)
            KEY_HOME -> listener?.onEditorKey(KeyEvent.KEYCODE_MOVE_HOME)
            KEY_END -> listener?.onEditorKey(KeyEvent.KEYCODE_MOVE_END)
            KEY_PGUP -> listener?.onEditorKey(KeyEvent.KEYCODE_PAGE_UP)
            KEY_PGDOWN -> listener?.onEditorKey(KeyEvent.KEYCODE_PAGE_DOWN)
            "←" -> listener?.onEditorKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> listener?.onEditorKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "↑" -> listener?.onEditorKey(KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> listener?.onEditorKey(KeyEvent.KEYCODE_DPAD_DOWN)
            Page.LETTERS.tabLabel, Page.NUMBERS.tabLabel, Page.SYMBOLS.tabLabel,
            Page.EMOJI.tabLabel, Page.FN.tabLabel -> Unit
            else -> {
                val editorCode = editorKeyCodeFor(label)
                if (editorCode != null) {
                    listener?.onEditorKey(editorCode)
                } else {
                    // Case model: capsLock = sustained upper; shift = one-shot upper;
                    // autoCap = one-shot upper seeded from the field's sentence caps.
                    val upper = capsLockEnabled || shiftEnabled || autoCap
                    listener?.onKey(if (label.length == 1 && label[0].isLetter() && upper) label.uppercase() else label)
                    consumeOneShotShift()
                }
            }
        }
        invalidate()
    }

    /** F1..F12 labels route as editor keys. */
    private fun editorKeyCodeFor(label: String): Int? {
        if (label.length in 2..3 && label[0] == 'F' && label.drop(1).all { it.isDigit() }) {
            val n = label.drop(1).toIntOrNull() ?: return null
            if (n in 1..12) return KeyEvent.KEYCODE_F1 + (n - 1)
        }
        return null
    }

    private fun consumeOneShotShift() {
        if (shiftEnabled) shiftEnabled = false
        autoCap = false
    }

    // ---- gamepad --------------------------------------------------------------

    /** Gamepad key routing. Returns true when consumed. */
    fun onGamepadKeyDown(keyCode: Int): Boolean {
        Log.d(TAG, "onGamepadKeyDown ${KeyEvent.keyCodeToString(keyCode)}")
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { startRepeat(-1, 0); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { startRepeat(1, 0); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { startRepeat(0, -1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { startRepeat(0, 1); true }
            // Erik's spec (v3): A = type, B = close (matches Android convention)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> { pressSelectedKey(); true }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { listener?.onHide(); true }
            KeyEvent.KEYCODE_BUTTON_X -> { flashKeyByLabel(KEY_BACKSPACE); listener?.onBackspace(); true }
            KeyEvent.KEYCODE_BUTTON_Y -> { flashKeyByLabel(KEY_SHIFT); shiftEnabled = !shiftEnabled; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { cyclePage(-1); true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { cyclePage(1); true }
            KeyEvent.KEYCODE_BUTTON_START -> { flashKeyByLabel(KEY_ENTER); listener?.onEnter(); true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { listener?.onHide(); true }
            else -> false
        }
    }

    fun onGamepadKeyUp(keyCode: Int): Boolean {
        if (keyCode in intArrayOf(
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        ) { stopRepeat(); return true }
        return keyCode in intArrayOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK)
    }

    private fun cyclePage(dir: Int) {
        val order = Page.entries
        page = order[(order.indexOf(page) + dir + order.size) % order.size]
    }

    private fun startRepeat(dRow: Int, dCol: Int) {
        stopRepeat()  // first — it clears repeatDirection
        moveSelection(dRow, dCol)
        repeatDirection = dRow to dCol
        val r = object : Runnable {
            override fun run() {
                val dir = repeatDirection ?: return
                moveSelection(dir.first, dir.second)
                postDelayed(this, REPEAT_RATE_MS)
            }
        }
        repeatRunnable = r
        postDelayed(r, REPEAT_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { removeCallbacks(it) }
        repeatRunnable = null
        repeatDirection = null
    }

    /** Service-facing: hat/stick edge → start stepping (and keep stepping while held). */
    fun startDirectionalRepeat(dRow: Int, dCol: Int) = startRepeat(dRow, dCol)

    /** Service-facing: hat/stick returned to center → stop stepping. */
    fun stopDirectionalRepeat() = stopRepeat()

    override fun onDetachedFromWindow() {
        stopRepeat()
        cancelHold()
        flashRunnable?.let { removeCallbacks(it) }
        flashRunnable = null
        super.onDetachedFromWindow()
    }

    // ---- touch ----------------------------------------------------------------

    private var cellRects: List<List<RectF>> = emptyList()
    private var stripRects: List<RectF> = emptyList()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Live pressed state: the key under the finger is drawn depressed.
                // Sliding across keys moves the depression; nothing commits on slide.
                val hit = hitStrip(event.x, event.y)
                if (hit >= 0) {
                    if (pressedRow != -2 || pressedCol != hit) {
                        pressedRow = -2; pressedCol = hit
                        cancelHold()
                        invalidate()
                    }
                } else {
                    val (r, c) = hitCell(event.x, event.y)
                    if (r != pressedRow || c != pressedCol) {
                        pressedRow = r; pressedCol = c
                        if (event.actionMasked == MotionEvent.ACTION_DOWN && r >= 0) {
                            startHoldIfRepeatable(r, c)
                        } else cancelHold()
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val stripHit = hitStrip(event.x, event.y)
                pressedRow = -1; pressedCol = -1
                cancelHold()
                if (stripHit >= 0) {
                    suggestions.getOrNull(stripHit)?.let {
                        highlightVisible = false
                        flashSuggestion(stripHit)
                        listener?.onSuggestionPick(it)
                        performClick()
                    }
                    invalidate()
                    return true
                }
                val (r, c) = hitCell(event.x, event.y)
                if (r >= 0 && !didRepeat) {
                    // Tap = direct press. Selection/highlight stays a gamepad-only cursor.
                    // (After a hold-repeat, the lift must not fire the key once more.)
                    highlightVisible = false
                    flashCell(r, c)
                    pressKey(grid()[r][c].label)
                    performClick()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedRow = -1; pressedCol = -1
                cancelHold()
                invalidate()
            }
        }
        return true
    }

    /** Cell under (x, y): (row, col), or (-1, -1) when nothing is hit. */
    private fun hitCell(x: Float, y: Float): Pair<Int, Int> {
        for ((r, row) in cellRects.withIndex()) {
            for ((c, rect) in row.withIndex()) {
                if (rect.contains(x, y)) return r to c
            }
        }
        return -1 to -1
    }

    /** Suggestion-strip slot under (x, y), or -1. */
    private fun hitStrip(x: Float, y: Float): Int {
        for ((i, rect) in stripRects.withIndex()) {
            if (rect.contains(x, y)) return i
        }
        return -1
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    // ---- drawing --------------------------------------------------------------

    private fun keyShader(top: Int, bottom: Int, topY: Float, botY: Float): Shader =
        LinearGradient(0f, topY, 0f, botY, top, bottom, Shader.TileMode.CLAMP)

    private fun actionColor(top: Boolean) =
        if (top) skin.actionTop else skin.actionBottom

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        bgPaint.color = skin.bg
        // Opaque background — the IME window is translucent by default; without this
        // the app underneath shows through the gaps between keys.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val stripPx = if (suggestions.isEmpty()) 0f else 44f * density
        val pad = 7f
        val hintStripPx = 16f * density
        val gridTop = pad + stripPx
        val gridH = height - hintStripPx - gridTop
        val g = grid()
        val rowH = gridH / g.size
        val radius = 9f * density

        // --- suggestion strip ---
        stripRects = emptyList()
        if (suggestions.isNotEmpty()) {
            val slotW = (width - 2 * pad) / suggestions.size
            var x = pad
            for ((i, word) in suggestions.withIndex()) {
                val rect = RectF(x + 3, pad + 3, x + slotW - 3, pad + stripPx - 6)
                x += slotW
                stripRects += rect
                keyPaint.shader = keyShader(skin.capTop, skin.capBottom, rect.top, rect.bottom)
                canvas.drawRoundRect(rect, radius, radius, keyPaint)
                val isSel = highlightVisible && selectedRow == -1 && selectedCol == i
                val isFlash = flashRow == -2 && flashSug == i
                val isPressedStrip = pressedRow == -2 && pressedCol == i
                if (isFlash || isPressedStrip) {
                    keyPaint.shader = null; keyPaint.color = if (isPressedStrip) skin.selectFill else skin.flash
                    canvas.drawRoundRect(rect, radius, radius, keyPaint)
                } else if (isSel) {
                    keyPaint.shader = null; keyPaint.color = skin.selectFill
                    canvas.drawRoundRect(rect, radius, radius, keyPaint)
                    canvas.drawRoundRect(rect, radius, radius, selectRingPaint.apply { color = skin.selectRing })
                }
                legendPaint.color = skin.legend
                legendPaint.textSize = 15f * density
                canvas.drawText(
                    word, rect.centerX(),
                    rect.centerY() - (legendPaint.ascent() + legendPaint.descent()) / 2f,
                    legendPaint,
                )
            }
        }

        // --- key grid ---
        val newRects = mutableListOf<List<RectF>>()
        g.forEachIndexed { r, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val cellW = (width - 2 * pad) / totalWeight
            val rowTop = gridTop + r * rowH
            val rowRects = mutableListOf<RectF>()
            var x = pad
            row.forEachIndexed { c, key ->
                val rect = RectF(x + 2, rowTop + 2, x + key.weight * cellW - 2, rowTop + rowH - 2)
                x += key.weight * cellW
                rowRects += rect

                val isPressed = r == pressedRow && c == pressedCol
                val isFlash = r == flashRow && c == flashCol
                val selected = highlightVisible && r == selectedRow && c == selectedCol

                when {
                    // Listening: mic key pulses amber (invert on a 500ms blink)
                    key.label == KEY_MIC && micListening -> {
                        keyPaint.shader = null
                        val blink = (SystemClock.uptimeMillis() / 250) % 2 == 0L
                        keyPaint.color = if (blink) skin.flash else skin.selectFill
                        canvas.drawRoundRect(rect, radius, radius, keyPaint)
                    }
                    // Depressed keycap: darker inverted bevel, nudged down 1dp
                    isPressed || isFlash -> {
                        keyPaint.shader = keyShader(
                            if (key.isAction) actionColor(false) else skin.capBottom,
                            if (key.isAction) actionColor(true) else skin.capTop,
                            rowTop, rowTop + rowH,
                        )
                        canvas.save()
                        canvas.translate(0f, 1.5f)
                        canvas.drawRoundRect(rect, radius, radius, keyPaint)
                        canvas.restore()
                        if (isFlash) {
                            keyPaint.shader = null
                            keyPaint.color = skin.flash
                            canvas.save()
                            canvas.translate(0f, 1.5f)
                            canvas.drawRoundRect(rect, radius, radius, keyPaint)
                            canvas.restore()
                        }
                    }
                    else -> {
                        keyPaint.shader = keyShader(
                            if (key.isAction) actionColor(true) else skin.capTop,
                            if (key.isAction) actionColor(false) else skin.capBottom,
                            rowTop, rowTop + rowH,
                        )
                        canvas.drawRoundRect(rect, radius, radius, keyPaint)
                    }
                }
                if (selected || modifierActive(key)) {
                    keyPaint.shader = null
                    keyPaint.color = skin.selectFill
                    canvas.drawRoundRect(rect, radius, radius, keyPaint)
                    canvas.drawRoundRect(rect, radius, radius, selectRingPaint.apply { color = skin.selectRing })
                }

                // Legend
                val disp = displayLabel(key)
                legendPaint.color = skin.legend
                legendPaint.textSize = when {
                    key.label.length > 3 -> 13f * density
                    key.label.length > 1 -> 16f * density
                    else -> minOf(22f * density, rowH * 0.42f)
                }
                val legendX = if (key.badge != null && rect.width() > 46f * density)
                    rect.centerX() - 7f * density else rect.centerX()
                canvas.drawText(disp, legendX, rect.centerY() - (legendPaint.ascent() + legendPaint.descent()) / 2f, legendPaint)

                // Gamepad badge (colored dot + letter), Deck-style
                key.badge?.let { badge ->
                    if (rect.width() > 40f * density && rect.height() > 26f * density) {
                        val br = 6.5f * density
                        val bcx = rect.right - br - 5f * density
                        val bcy = rect.centerY()
                        badgePaint.color = skin.badge
                        canvas.drawCircle(bcx, bcy, br, badgePaint)
                        badgeTextPaint.color = skin.bg
                        badgeTextPaint.textSize = 9f * density
                        canvas.drawText(badge, bcx, bcy - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f, badgeTextPaint)
                    }
                }
            }
            newRects += rowRects
        }
        cellRects = newRects

        // --- hint strip ---
        dimPaint.color = skin.dim
        dimPaint.textSize = 10f * density
        val state = when {
            page != Page.LETTERS -> page.tabLabel
            capsLockEnabled -> "CAPS"
            shiftEnabled -> "Shift"
            autoCap -> "Abc"
            else -> "abc"
        }
        canvas.drawText(
            "$state \u00b7 $DISPLAY_VERSION \u00b7 A type \u00b7 B close \u00b7 X \u232b \u00b7 Y shift \u00b7 LB/RB pages \u00b7 S complete",
            10f, height - 4f * density, dimPaint,
        )

        // Drive the mic-listening blink
        if (micListening) postInvalidateDelayed(250)
    }

    /** Caps/Shift light up (selected style) while they latch case. */
    private fun modifierActive(key: Key): Boolean = when (key.label) {
        KEY_CAPS -> capsLockEnabled
        KEY_SHIFT -> shiftEnabled || autoCap
        else -> false
    }

    private fun displayLabel(key: Key): String = when {
        key.label == KEY_SPACE -> when (page) {
            Page.LETTERS -> "abc"
            Page.NUMBERS -> "0-9"
            Page.SYMBOLS -> "@#:"
            Page.EMOJI -> "\uD83D\uDE00"
            Page.FN -> "space"
        }
        editorKeyCodeFor(key.label) != null -> key.label
        key.label.length == 1 && key.label[0].isLetter() &&
            (capsLockEnabled || shiftEnabled || autoCap) -> key.label.uppercase()
        else -> key.label
    }
}
