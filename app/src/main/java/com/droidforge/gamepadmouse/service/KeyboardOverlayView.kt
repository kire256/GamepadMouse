package com.droidforge.gamepadmouse.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class KeyboardOverlayView(context: Context) : View(context) {
    enum class KeyboardLayout { LETTERS, NUMBERS, SYMBOLS }

    companion object {
        const val KEY_SHIFT = "⇧"
        const val KEY_CAPS = "CAPS"
        const val KEY_BACKSPACE = "⌫"
        const val KEY_SPACE = "SPACE"
        const val KEY_ENTER = "ENTER"
        const val KEY_HIDE = "HIDE"
        const val KEY_POSITION = "MOVE"
    }

    var widthPercent = 80f
        set(value) { field = value.coerceIn(40f, 100f); invalidate() }
    var heightPercent = 45f
        set(value) { field = value.coerceIn(25f, 80f); invalidate() }
    var atTop = false
        set(value) { field = value; invalidate() }
    var showNumberRow = true
        set(value) { field = value; normalizeSelection(); invalidate() }
    var showSystemKeys = true
        set(value) { field = value; normalizeSelection(); invalidate() }
    var keyboardColor = 0xFF202124.toInt()
        set(value) { field = value; bgPaint.color = value; invalidate() }
    var currentLayout = KeyboardLayout.LETTERS
        set(value) { field = value; normalizeSelection(); invalidate() }
    var shiftEnabled = false
        set(value) { field = value; invalidate() }
    var capsLockEnabled = false
        set(value) { field = value; invalidate() }
    var selectedRow = 0
        private set
    var selectedCol = 0
        private set
    var currentText = ""
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = keyboardColor }
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3C4043.toInt() }
    private val keySelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CAF50.toInt() }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f; textAlign = Paint.Align.CENTER
    }
    private val textDisplayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 46f; textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDDDDDD.toInt(); textSize = 26f; textAlign = Paint.Align.CENTER
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
        arrayOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        arrayOf(".", ",", "?", "!", "'", "\""),
    )

    init { isFocusable = false; setWillNotDraw(false) }

    fun getCurrentSelectedKey(): String = currentRows()[selectedRow][selectedCol]

    fun moveSelection(dRow: Int, dCol: Int) {
        val rows = currentRows()
        selectedRow = (selectedRow + dRow).coerceIn(0, rows.lastIndex)
        selectedCol = (selectedCol + dCol).coerceIn(0, rows[selectedRow].lastIndex)
        invalidate()
    }

    fun displayCharacter(key: String): String {
        if (key.length != 1 || !key[0].isLetter()) return key
        val uppercase = capsLockEnabled.xor(shiftEnabled)
        return if (uppercase) key.uppercase() else key.lowercase()
    }

    fun consumeOneShotShift() {
        if (shiftEnabled) shiftEnabled = false
    }

    private fun normalizeSelection() {
        val rows = currentRows()
        selectedRow = selectedRow.coerceIn(0, rows.lastIndex)
        selectedCol = selectedCol.coerceIn(0, rows[selectedRow].lastIndex)
    }

    private fun currentRows(): List<Array<String>> {
        val rows = mutableListOf<Array<String>>()
        if (currentLayout == KeyboardLayout.LETTERS && showNumberRow) {
            rows += arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }
        rows += when (currentLayout) {
            KeyboardLayout.LETTERS -> letters
            KeyboardLayout.NUMBERS -> numbers
            KeyboardLayout.SYMBOLS -> symbols
        }
        if (showSystemKeys) {
            rows += arrayOf(KEY_SHIFT, KEY_CAPS, KEY_BACKSPACE, KEY_SPACE, KEY_ENTER, KEY_POSITION, KEY_HIDE)
        }
        return rows
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val w = screenW * widthPercent / 100f
        val h = screenH * heightPercent / 100f
        val left = (screenW - w) / 2f
        val top = if (atTop) 0f else screenH - h
        canvas.save()
        canvas.translate(left, top)
        canvas.drawRoundRect(0f, 0f, w, h, 18f, 18f, bgPaint)

        val textAreaHeight = h * 0.14f
        canvas.drawText(currentText.takeLast(40), w / 2f, textAreaHeight * 0.55f, textDisplayPaint)
        val state = when (currentLayout) {
            KeyboardLayout.LETTERS -> when {
                capsLockEnabled -> "CAPS"
                shiftEnabled -> "Shift"
                else -> "abc"
            }
            KeyboardLayout.NUMBERS -> "123"
            KeyboardLayout.SYMBOLS -> "#$%"
        }
        canvas.drawText(state, w / 2f, textAreaHeight - 5f, labelPaint)

        val rows = currentRows()
        val keyboardTop = textAreaHeight + 8f
        val keyboardBottom = h - 30f
        val rowHeight = (keyboardBottom - keyboardTop) / rows.size
        rows.forEachIndexed { rowIndex, row ->
            val rowY = keyboardTop + rowIndex * rowHeight
            val keyWidth = (w - 20f) / row.size
            row.forEachIndexed { columnIndex, rawKey ->
                val keyRect = RectF(
                    10f + columnIndex * keyWidth + 4f,
                    rowY + 4f,
                    10f + (columnIndex + 1) * keyWidth - 4f,
                    rowY + rowHeight - 4f,
                )
                canvas.drawRoundRect(
                    keyRect, 9f, 9f,
                    if (rowIndex == selectedRow && columnIndex == selectedCol) keySelectedPaint else keyBgPaint,
                )
                val label = displayCharacter(rawKey)
                keyTextPaint.textSize = if (label.length > 2) 25f else 42f
                canvas.drawText(label, keyRect.centerX(), keyRect.centerY() - (keyTextPaint.ascent() + keyTextPaint.descent()) / 2f, keyTextPaint)
            }
        }
        canvas.drawText("D-pad: move · A: select · LB/RB: layout", w / 2f, h - 7f, labelPaint)
        canvas.restore()
    }
}
