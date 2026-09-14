package com.droidforge.gamepadkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Gamepad-navigable on-screen keyboard.
 *
 * Gamepad mapping:
 *  - D-pad / left stick: move key selection
 *  - A: press selected key     B: hide keyboard
 *  - X: backspace              Y: shift
 *  - LB/RB: switch layout      Start: Enter     Select/Back: hide
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

    enum class Layout { LETTERS, NUMBERS, SYMBOLS }

    companion object {
        const val KEY_SHIFT = "\u21E7"      // ⇧
        const val KEY_CAPS = "CAPS"
        const val KEY_BACKSPACE = "\u232B"  // ⌫
        const val KEY_SPACE = "SPACE"
        const val KEY_ENTER = "ENTER"
        const val KEY_HIDE = "HIDE"
        private val REPEAT_DELAY_MS = 400L
        private val REPEAT_RATE_MS = 60L
    }

    private val letters = listOf(
        arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        arrayOf("z", "x", "c", "v", "b", "n", "m"),
    )
    private val numbers = listOf(
        arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        arrayOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
        arrayOf(".", ",", "?", "!", "'", "_"),
    )
    private val symbols = listOf(
        arrayOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        arrayOf("~", "\\", "|", "<", ">", "\u20AC", "\u00A3", "\u00A5", "\u2022", "\u00B0"),
        arrayOf(".", ",", "?", "!", "'", "\""),
    )

    var layout: Layout = Layout.LETTERS
        set(value) { field = value; normalizeSelection(); invalidate() }
    var shiftEnabled = false
        set(value) { field = value; invalidate() }
    var capsLockEnabled = false
        set(value) { field = value; invalidate() }
    var keyboardColor = 0xEE14141B.toInt()
        set(value) { field = value; invalidate() }

    private var selectedRow = 1
    private var selectedCol = 0

    // Key-repeat while a d-pad direction is held
    private var repeatRunnable: Runnable? = null
    private var repeatDirection: Pair<Int, Int>? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = keyboardColor }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2E37.toInt() }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D81F6.toInt() }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA3AF.toInt(); textSize = 24f; textAlign = Paint.Align.CENTER
    }

    init { setWillNotDraw(false) }

    private fun rows(): List<Array<String>> {
        val rows = mutableListOf<Array<String>>()
        if (layout == Layout.LETTERS) rows += arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        rows += when (layout) {
            Layout.LETTERS -> letters
            Layout.NUMBERS -> numbers
            Layout.SYMBOLS -> symbols
        }
        rows += arrayOf(KEY_SHIFT, KEY_CAPS, KEY_BACKSPACE, KEY_SPACE, KEY_ENTER, KEY_HIDE)
        return rows
    }

    private fun normalizeSelection() {
        val rows = rows()
        selectedRow = selectedRow.coerceIn(0, rows.lastIndex)
        selectedCol = selectedCol.coerceIn(0, rows[selectedRow].lastIndex)
    }

    fun moveSelection(dRow: Int, dCol: Int) {
        val rows = rows()
        selectedRow = (selectedRow + dRow).coerceIn(0, rows.lastIndex)
        selectedCol = selectedCol.coerceIn(0, rows[selectedRow].lastIndex)
        invalidate()
    }

    fun selectedKey(): String = rows()[selectedRow][selectedCol]

    fun pressSelectedKey() = pressKey(selectedKey())

    fun pressKey(key: String) {
        when (key) {
            KEY_SHIFT -> shiftEnabled = !shiftEnabled
            KEY_CAPS -> capsLockEnabled = !capsLockEnabled
            KEY_BACKSPACE -> listener?.onBackspace()
            KEY_SPACE -> { listener?.onKey(" "); consumeOneShotShift() }
            KEY_ENTER -> { listener?.onEnter(); consumeOneShotShift() }
            KEY_HIDE -> listener?.onHide()
            else -> {
                val upper = capsLockEnabled xor shiftEnabled
                listener?.onKey(if (key.length == 1 && key[0].isLetter() && upper) key.uppercase() else key)
                consumeOneShotShift()
            }
        }
        invalidate()
    }

    private fun consumeOneShotShift() { if (shiftEnabled) shiftEnabled = false }

    /**
     * Gamepad key routing. Returns true when consumed.
     * D-pad repeat starts after [REPEAT_DELAY_MS], then fires every [REPEAT_RATE_MS].
     */
    fun onGamepadKeyDown(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> { startRepeat(-1, 0); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { startRepeat(1, 0); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { startRepeat(0, -1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { startRepeat(0, 1); true }
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_BUTTON_A -> { pressSelectedKey(); true }
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BACK -> { listener?.onHide(); true }
        KeyEvent.KEYCODE_BUTTON_X -> { listener?.onBackspace(); true }
        KeyEvent.KEYCODE_BUTTON_Y -> { shiftEnabled = !shiftEnabled; true }
        KeyEvent.KEYCODE_BUTTON_L1 -> { cycleLayout(-1); true }
        KeyEvent.KEYCODE_BUTTON_R1 -> { cycleLayout(1); true }
        KeyEvent.KEYCODE_BUTTON_START -> { listener?.onEnter(); true }
        KeyEvent.KEYCODE_BUTTON_SELECT -> { listener?.onHide(); true }
        else -> false
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val key = keyAt(event.x, event.y)
        if (key != null) pressKey(key)
        performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun keyAt(x: Float, y: Float): String? {
        val rows = rows()
        val pad = 10f
        val rowH = (height - 2 * pad) / rows.size
        val row = ((y - pad) / rowH).toInt().coerceIn(0, rows.lastIndex)
        val keyW = (width - 2 * pad) / rows[row].size
        val col = ((x - pad) / keyW).toInt().coerceIn(0, rows[row].lastIndex)
        selectedRow = row; selectedCol = col; invalidate()
        return rows[row][col]
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keyboardColor.let { bgPaint.color = it }
        val rows = rows()
        val pad = 10f
        val rowH = (height - 2 * pad) / rows.size
        rows.forEachIndexed { r, row ->
            val keyW = (width - 2 * pad) / row.size
            row.forEachIndexed { c, key ->
                val rect = RectF(pad + c * keyW + 3, pad + r * rowH + 3, pad + (c + 1) * keyW - 3, pad + (r + 1) * rowH - 3)
                val selected = r == selectedRow && c == selectedCol
                canvas.drawRoundRect(rect, 10f, 10f, if (selected) selectedPaint else keyPaint)
                val label = when {
                    key.length == 1 && key[0].isLetter() && (capsLockEnabled xor shiftEnabled) -> key.uppercase()
                    else -> key
                }
                keyTextPaint.textSize = if (label.length > 2) 26f else minOf(48f, rowH * 0.55f)
                canvas.drawText(label, rect.centerX(), rect.centerY() - (keyTextPaint.ascent() + keyTextPaint.descent()) / 2f, keyTextPaint)
            }
        }
        val state = when (layout) {
            Layout.LETTERS -> when { capsLockEnabled -> "CAPS"; shiftEnabled -> "Shift"; else -> "abc" }
            Layout.NUMBERS -> "123"
            Layout.SYMBOLS -> "#$%"
        }
        canvas.drawText(
            "$state  \u00B7  D-pad move \u00B7 A type \u00B7 B hide \u00B7 X del \u00B7 Y shift \u00B7 LB/RB layout",
            width / 2f, height - 6f, hintPaint,
        )
    }
}
