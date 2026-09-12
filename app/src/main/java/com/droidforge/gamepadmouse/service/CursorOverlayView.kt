package com.droidforge.gamepadmouse.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Full-screen, non-focusable, non-touchable overlay that ONLY draws the cursor.
 * Never participates in focus so it can never be hidden/removed by the system's
 * focus-management logic.
 */
class CursorOverlayView(context: Context) : View(context) {

    var cursorX = 0f
        private set
    var cursorY = 0f
        private set

    private val sizePx = context.resources.displayMetrics.density * 22f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000; style = Paint.Style.FILL
    }
    private val arrow = Path()

    init {
        isFocusable = false
        setWillNotDraw(false)
    }

    fun setCursor(x: Float, y: Float) {
        if (x.isNaN() || y.isNaN()) return
        if (x == cursorX && y == cursorY) return
        cursorX = x; cursorY = y
        invalidate()
    }

    fun centerCursor() {
        val cx = if (width > 0) width / 2f else 540f
        val cy = if (height > 0) height / 2f else 960f
        setCursor(cx, cy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && oldh == 0) centerCursor()
        else setCursor(cursorX.coerceIn(0f, w.toFloat()), cursorY.coerceIn(0f, h.toFloat()))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buildArrow(cursorX, cursorY)
        canvas.save(); canvas.translate(2f, 3f); canvas.drawPath(arrow, shadow); canvas.restore()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }

    private fun buildArrow(x: Float, y: Float) {
        val s = sizePx
        arrow.reset()
        arrow.moveTo(x, y)
        arrow.lineTo(x, y + s)
        arrow.lineTo(x + s * 0.27f, y + s * 0.78f)
        arrow.lineTo(x + s * 0.47f, y + s * 1.15f)
        arrow.lineTo(x + s * 0.62f, y + s * 1.07f)
        arrow.lineTo(x + s * 0.42f, y + s * 0.70f)
        arrow.lineTo(x + s * 0.75f, y + s * 0.70f)
        arrow.close()
    }
}
