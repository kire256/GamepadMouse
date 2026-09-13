package com.droidforge.gamepadmouse.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * On-screen keyboard overlay for gamepad text input.
 * Shows QWERTY layout with highlighted selection.
 */
class KeyboardOverlayView(context: Context) : View(context) {
    var widthPercent: Float = 80f
        set(value) { field = value.coerceIn(40f, 100f); invalidate() }
    var heightPercent: Float = 45f
        set(value) { field = value.coerceIn(25f, 80f); invalidate() }
    
    enum class KeyboardLayout { LETTERS, NUMBERS, SYMBOLS }
    
    var currentLayout = KeyboardLayout.LETTERS
        set(value) {
            field = value
            invalidate()
        }
    
    var shiftEnabled = false
        set(value) {
            field = value
            invalidate()
        }
    
    var selectedRow = 0
        set(value) {
            field = value.coerceIn(0, getCurrentKeys().size - 1)
            selectedCol = selectedCol.coerceIn(0, getCurrentKeys()[field].size - 1)
            invalidate()
        }
    
    var selectedCol = 0
        set(value) {
            field = value.coerceIn(0, getCurrentKeys()[selectedRow].size - 1)
            invalidate()
        }
    
    var currentText = ""
        set(value) {
            field = value
            invalidate()
        }
    
    // Paint objects
    private val bgPaint = Paint().apply {
        color = 0xCC000000.toInt() // Semi-transparent black
        style = Paint.Style.FILL
    }
    
    private val keyBgPaint = Paint().apply {
        color = 0xFF333333.toInt()
        style = Paint.Style.FILL
    }
    
    private val keySelectedPaint = Paint().apply {
        color = 0xFF4CAF50.toInt() // Green
        style = Paint.Style.FILL
    }
    
    private val keyTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    private val textDisplayPaint = Paint().apply {
        color = Color.WHITE
        textSize = 56f
        isAntiAlias = true
    }
    
    private val labelPaint = Paint().apply {
        color = 0xFFAAAAAA.toInt()
        textSize = 32f
        isAntiAlias = true
    }
    
    // Keyboard layouts
    private val lettersLower = arrayOf(
        arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        arrayOf("z", "x", "c", "v", "b", "n", "m")
    )
    
    private val lettersUpper = arrayOf(
        arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        arrayOf("Z", "X", "C", "V", "B", "N", "M")
    )
    
    private val numbers = arrayOf(
        arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        arrayOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
        arrayOf(".", ",", "?", "!", "'", "_")
    )
    
    private val symbols = arrayOf(
        arrayOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        arrayOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        arrayOf(".", ",", "?", "!", "'", "\"")
    )
    
    init {
        isFocusable = false
        setWillNotDraw(false)
    }
    
    fun getCurrentSelectedKey(): String {
        val keys = getCurrentKeys()
        return keys[selectedRow][selectedCol]
    }
    
    fun moveSelection(dRow: Int, dCol: Int) {
        val keys = getCurrentKeys()
        var newRow = (selectedRow + dRow).coerceIn(0, keys.size - 1)
        var newCol = (selectedCol + dCol).coerceIn(0, keys[newRow].size - 1)
        
        selectedRow = newRow
        selectedCol = newCol
    }
    
    private fun getCurrentKeys(): Array<Array<String>> {
        return when (currentLayout) {
            KeyboardLayout.LETTERS -> if (shiftEnabled) lettersUpper else lettersLower
            KeyboardLayout.NUMBERS -> numbers
            KeyboardLayout.SYMBOLS -> symbols
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val w = screenW * widthPercent / 100f
        val h = screenH * heightPercent / 100f
        val left = (screenW - w) / 2f
        val top = screenH - h
        canvas.save()
        canvas.translate(left, top)
        
        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        
        // Draw text display area at top
        val textAreaHeight = h * 0.15f
        canvas.drawText(
            currentText.takeLast(40), // Show last 40 chars
            w / 2,
            textAreaHeight / 2 + 20,
            textDisplayPaint
        )
        
        // Draw layout indicator
        val layoutName = when (currentLayout) {
            KeyboardLayout.LETTERS -> if (shiftEnabled) "ABC (Upper)" else "abc (lower)"
            KeyboardLayout.NUMBERS -> "123"
            KeyboardLayout.SYMBOLS -> "#$%"
        }
        canvas.drawText(layoutName, 100f, textAreaHeight - 20, labelPaint)
        
        // Draw keyboard
        val keys = getCurrentKeys()
        val keyboardTop = textAreaHeight + 40
        val keyboardHeight = h - keyboardTop - 40
        val rowHeight = keyboardHeight / keys.size
        
        for (rowIdx in keys.indices) {
            val row = keys[rowIdx]
            val rowY = keyboardTop + rowIdx * rowHeight
            val keyWidth = (w - 40) / row.size
            
            for (colIdx in row.indices) {
                val keyX = 20 + colIdx * keyWidth
                val keyRect = RectF(
                    keyX + 5,
                    rowY + 5,
                    keyX + keyWidth - 5,
                    rowY + rowHeight - 5
                )
                
                // Draw key background
                val paint = if (rowIdx == selectedRow && colIdx == selectedCol) {
                    keySelectedPaint
                } else {
                    keyBgPaint
                }
                canvas.drawRoundRect(keyRect, 10f, 10f, paint)
                
                // Draw key text
                canvas.drawText(
                    row[colIdx],
                    keyRect.centerX(),
                    keyRect.centerY() + 15,
                    keyTextPaint
                )
            }
        }
        
        // Draw help text at bottom
        canvas.drawText(
            "D-Pad:Move  A:Type  B:Back  X:Space  Y:Shift  LB/RB:Layout  Start:Enter",
            w / 2,
            h - 20,
            labelPaint.apply { textAlign = Paint.Align.CENTER }
        )
        canvas.restore()
    }
}
