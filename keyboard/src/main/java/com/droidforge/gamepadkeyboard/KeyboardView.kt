package com.droidforge.gamepadkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Gamepad-navigable on-screen keyboard, styled after the PlayStation console keyboard.
 *
 * Gamepad mapping:
 *  - D-pad / left stick: move key selection
 *  - A: close keyboard (back)      B: press selected key (type)
 *  - X: backspace                  Y: shift
 *  - LB/RB: switch layout          Start: Enter      Select: close
 *
 * Touch works too: tapping a key presses it directly.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(text: String)
        fun onBackspace()
        fun onEnter()
        fun onHide()
    }

    var listener: Listener? = null

    enum class Layout(val tabLabel: String) { LETTERS("ABC"), NUMBERS("123"), SYMBOLS("@#:") }

    companion object {
        const val KEY_SHIFT = "\u21E7"       // ⇧
        const val KEY_BACKSPACE = "\u232B"   // ⌫
        const val KEY_ENTER = "\u21B5"       // ↵
        const val KEY_SPACE = " "
        const val KEY_DONE = "Done"

        /** Shown in the keyboard's bottom bar so on-device builds are always identifiable. */
        const val DISPLAY_VERSION = "v0.1.4"
        private const val TAG = "GPKeyboard"
        private val REPEAT_DELAY_MS = 400L
        private val REPEAT_RATE_MS = 60L
    }

    // PlayStation-style letter grid: numbers+@ / QWERTY+# / home row+"/" / ZXCV with -_?!
    private val letterRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "@"),
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "#"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "\"", "/"),
        listOf("z", "x", "c", "v", "b", "n", "m", ".", "-", "_", "?", "!"),
    )
    private val numberRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "@"),
        listOf("#", "/", "&", "$", "%", "*", "+", "=", "~", "^", "|"),
        listOf(":", ";", "(", ")", "\"", "'", "."),
        listOf("<", ">", "[", "]", "{", "}"),
    )
    private val symbolRows = listOf(
        listOf("€", "£", "¥", "¢", "°", "•", "¡", "¿", "«", "»", "§"),
        listOf("à", "á", "é", "è", "í", "ó", "ú", "ü", "ñ", "ç", "ß"),
        listOf("ā", "ē", "ī", "ō", "ū", "â", "ê", "î", "ô", "û", "ã"),
        listOf("Ã", "Æ", "Ø", "Å", "Œ", "Þ", "Ð", "Ý", "Ø", "Λ", "Ω"),
    )

    var layout: Layout = Layout.LETTERS
        set(value) { field = value; normalizeSelection(); invalidate() }
    var shiftEnabled = false
        set(value) { field = value; invalidate() }
    var capsLockEnabled = false
        set(value) { field = value; invalidate() }
    /** One-shot auto-capitalize from the field's CAP_SENTENCES flag (first letter only). */
    var autoCap = false
        set(value) { field = value; invalidate() }
    var keyboardColor = 0xFF111318.toInt()
        set(value) { field = value; invalidate() }

    private var selectedRow = 1
    private var selectedCol = 0

    // Fixed height the view asserts regardless of the IME window's measuring spec —
    // without this some devices stretch the keyboard to fill the whole screen.
    private var desiredHeightPx = -1

    // Key-repeat while a d-pad direction is held
    private var repeatRunnable: Runnable? = null
    private var repeatDirection: Pair<Int, Int>? = null

    private val bgPaint = Paint().apply { color = keyboardColor }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF23262E.toInt() }
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1E25.toInt() }
    private val activeTabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2C3038.toInt() }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D81F6.toInt() }
    private val actionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2C3038.toInt() }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val tabTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB9C0CB.toInt(); textAlign = Paint.Align.CENTER
    }
    private val statePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA3AF.toInt(); textAlign = Paint.Align.LEFT
    }
    private val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }

    init { setWillNotDraw(false) }

    /** Height in px the keyboard should occupy; call before the view is attached. */
    fun setDesiredHeightPx(px: Int) {
        desiredHeightPx = px
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = if (desiredHeightPx > 0) resolveSize(desiredHeightPx, heightMeasureSpec)
        else getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    // ---- grid model -----------------------------------------------------------

    /** One key: label + grid width weight (1 = normal cell). */
    private data class Key(val label: String, val weight: Float = 1f, val isAction: Boolean = false)

    private fun letterGrid(): List<List<Key>> {
        val grid = letterRows.map { row -> row.map { Key(it) } }.toMutableList()
        val sysRow = mutableListOf(
            Key(KEY_SHIFT, 1.2f, true),
            Key(Layout.LETTERS.tabLabel, 1.2f, true),
            Key(Layout.SYMBOLS.tabLabel, 1.2f, true),
            Key(KEY_SPACE, 3.4f, true),
            Key(KEY_ENTER, 1.4f, true),
            Key(KEY_BACKSPACE, 1.6f, true),
        )
        grid += listOf(sysRow)
        return grid
    }

    private fun symbolGrid(): List<List<Key>> {
        val grid = (if (layout == Layout.NUMBERS) numberRows else symbolRows)
            .map { row -> row.map { Key(it) } }.toMutableList()
        val sysRow = mutableListOf(
            Key(KEY_SHIFT, 1.2f, true),
            Key(Layout.LETTERS.tabLabel, 1.2f, true),
            Key(Layout.SYMBOLS.tabLabel, 1.2f, true),
            Key(KEY_SPACE, 3.4f, true),
            Key(KEY_ENTER, 1.4f, true),
            Key(KEY_BACKSPACE, 1.6f, true),
        )
        grid += listOf(sysRow)
        return grid
    }

    private fun grid(): List<List<Key>> =
        if (layout == Layout.LETTERS) letterGrid() else symbolGrid()

    private fun normalizeSelection() {
        val g = grid()
        selectedRow = selectedRow.coerceIn(0, g.lastIndex)
        selectedCol = selectedCol.coerceIn(0, g[selectedRow].lastIndex)
    }

    fun moveSelection(dRow: Int, dCol: Int) {
        val g = grid()
        selectedRow = (selectedRow + dRow).coerceIn(0, g.lastIndex)
        selectedCol = selectedCol.coerceIn(0, g[selectedRow].lastIndex)
        invalidate()
    }

    fun selectedKey(): String = grid()[selectedRow][selectedCol].label

    fun pressSelectedKey() = pressKey(selectedKey())

    fun pressKey(label: String) {
        when (label) {
            KEY_SHIFT -> shiftEnabled = !shiftEnabled
            KEY_BACKSPACE -> listener?.onBackspace()
            KEY_ENTER -> { listener?.onEnter(); consumeOneShotShift() }
            KEY_DONE -> listener?.onHide()
            KEY_SPACE -> { listener?.onKey(" "); consumeOneShotShift() }
            Layout.LETTERS.tabLabel -> layout = Layout.LETTERS
            Layout.SYMBOLS.tabLabel -> layout = if (layout == Layout.SYMBOLS) Layout.NUMBERS else Layout.SYMBOLS
            else -> {
                // Case model: capsLock = sustained upper; shift = one-shot upper;
                // autoCap = one-shot upper seeded from the field's sentence caps.
                val upper = capsLockEnabled || shiftEnabled || autoCap
                listener?.onKey(if (label.length == 1 && label[0].isLetter() && upper) label.uppercase() else label)
                consumeOneShotShift()
            }
        }
        invalidate()
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
            KeyEvent.KEYCODE_DPAD_CENTER -> { pressSelectedKey(); true }
            // Erik's spec (v3): A = type, B = close (matches Android convention)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> { pressSelectedKey(); true }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { listener?.onHide(); true }
            KeyEvent.KEYCODE_BUTTON_X -> { listener?.onBackspace(); true }
            KeyEvent.KEYCODE_BUTTON_Y -> { shiftEnabled = !shiftEnabled; true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { cycleLayout(-1); true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { cycleLayout(1); true }
            KeyEvent.KEYCODE_BUTTON_START -> { listener?.onEnter(); true }
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

    private fun cycleLayout(dir: Int) {
        val order = Layout.entries
        layout = order[(order.indexOf(layout) + dir + order.size) % order.size]
    }

    private fun startRepeat(dRow: Int, dCol: Int) {
        moveSelection(dRow, dCol)
        repeatDirection = dRow to dCol
        stopRepeat()
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

    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }

    // ---- touch ----------------------------------------------------------------

    private var cellRects: List<List<RectF>> = emptyList()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        for ((r, row) in cellRects.withIndex()) {
            for ((c, rect) in row.withIndex()) {
                if (rect.contains(event.x, event.y)) {
                    selectedRow = r; selectedCol = c
                    pressKey(grid()[r][c].label)
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    // ---- drawing --------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bgPaint.color = keyboardColor
        // Opaque background — the IME window is translucent by default; without this
        // the app underneath shows through the gaps between keys.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val g = grid()
        val pad = 8f
        val bottomBarPx = 34f * resources.displayMetrics.density
        val gridH = height - bottomBarPx - pad
        val rowH = gridH / g.size
        val newRects = mutableListOf<List<RectF>>()

        g.forEachIndexed { r, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val cellW = (width - 2 * pad) / totalWeight
            val rowRects = mutableListOf<RectF>()
            var x = pad
            row.forEachIndexed { c, key ->
                val rect = RectF(x + 2, pad + r * rowH + 2, x + key.weight * cellW - 2, pad + (r + 1) * rowH - 2)
                x += key.weight * cellW
                rowRects += rect
                val selected = r == selectedRow && c == selectedCol
                val paint = when {
                    selected -> selectedPaint
                    key.isAction -> actionPaint
                    else -> keyPaint
                }
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                val label = when {
                    key.label.length == 1 && key.label[0].isLetter() &&
                        (capsLockEnabled || shiftEnabled || autoCap) -> key.label.uppercase()
                    else -> key.label
                }
                keyTextPaint.textSize = if (label.length > 2) 24f else minOf(40f, rowH * 0.5f)
                canvas.drawText(label, rect.centerX(), rect.centerY() - (keyTextPaint.ascent() + keyTextPaint.descent()) / 2f, keyTextPaint)
            }
            newRects += rowRects
        }
        cellRects = newRects

        // Bottom bar: state (left) + Done (right)
        val barTop = height - bottomBarPx
        val state = when (layout) {
            Layout.LETTERS -> when {
                capsLockEnabled -> "CAPS"
                shiftEnabled -> "Shift"
                autoCap -> "Abc"
                else -> "abc"
            }
            Layout.NUMBERS -> "123"
            Layout.SYMBOLS -> "@#:"
        }
        statePaint.textSize = 22f * resources.displayMetrics.density
        val stateText = "$state \u00b7 $DISPLAY_VERSION"
        canvas.drawText(stateText, 24f, barTop + bottomBarPx / 2 + statePaint.textSize / 3, statePaint)

        val doneRect = RectF(width - 30f * resources.displayMetrics.density, barTop + 4, width - 8f, height - 4f)
        canvas.drawRoundRect(doneRect, 8f, 8f, actionPaint)
        donePaint.textSize = 24f * resources.displayMetrics.density
        canvas.drawText(KEY_DONE, doneRect.centerX(), doneRect.centerY() + donePaint.textSize / 3, donePaint)

        // Hint
        tabTextPaint.textSize = 20f * resources.displayMetrics.density
        canvas.drawText(
            "\u00B7 A close \u00B7 B type \u00B7 X \u232B \u00B7 Y shift \u00B7 LB/RB tabs \u00B7 Start \u21B5",
            doneRect.left - 24f, barTop + bottomBarPx / 2 + tabTextPaint.textSize / 3, tabTextPaint,
        )
    }
}
