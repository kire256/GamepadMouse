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
    
    var cursorStyle = CursorStyle.ARROW
        set(value) {
            field = value
            invalidate()
        }
    
    var cursorSizeMultiplier = 1.0f
        set(value) {
            field = value
            invalidate()
        }
    
    var cursorColor = Color.WHITE
        set(value) {
            field = value
            fill.color = value
            invalidate()
        }

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
        when (cursorStyle) {
            CursorStyle.ARROW -> drawArrow(canvas, cursorX, cursorY)
            CursorStyle.DOT -> drawDot(canvas, cursorX, cursorY)
            CursorStyle.CROSSHAIR -> drawCrosshair(canvas, cursorX, cursorY)
            CursorStyle.CIRCLE -> drawCircle(canvas, cursorX, cursorY)
            CursorStyle.POINTER -> drawPointer(canvas, cursorX, cursorY)
            CursorStyle.TRIANGLE -> drawTriangle(canvas, cursorX, cursorY)
        }
    }
    
    private fun drawArrow(canvas: Canvas, x: Float, y: Float) {
        buildArrow(x, y)
        canvas.save(); canvas.translate(2f, 3f); canvas.drawPath(arrow, shadow); canvas.restore()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }

    private fun buildArrow(x: Float, y: Float) {
        val s = sizePx * cursorSizeMultiplier
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
    
    private fun drawDot(canvas: Canvas, x: Float, y: Float) {
        val radius = sizePx * 0.3f * cursorSizeMultiplier
        canvas.drawCircle(x, y, radius + 2f, shadow)
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, outline)
    }
    
    private fun drawCrosshair(canvas: Canvas, x: Float, y: Float) {
        val size = sizePx * 0.6f * cursorSizeMultiplier
        val thickness = context.resources.displayMetrics.density * 2f * cursorSizeMultiplier
        
        // Horizontal line
        canvas.drawRect(x - size, y - thickness/2, x + size, y + thickness/2, shadow)
        canvas.drawRect(x - size, y - thickness/2, x + size, y + thickness/2, fill)
        canvas.drawRect(x - size, y - thickness/2, x + size, y + thickness/2, outline)
        
        // Vertical line
        canvas.drawRect(x - thickness/2, y - size, x + thickness/2, y + size, shadow)
        canvas.drawRect(x - thickness/2, y - size, x + thickness/2, y + size, fill)
        canvas.drawRect(x - thickness/2, y - size, x + thickness/2, y + size, outline)
        
        // Center dot
        val dotRadius = sizePx * 0.15f * cursorSizeMultiplier
        canvas.drawCircle(x, y, dotRadius, fill)
        canvas.drawCircle(x, y, dotRadius, outline)
    }
    
    private fun drawCircle(canvas: Canvas, x: Float, y: Float) {
        val radius = sizePx * 0.5f * cursorSizeMultiplier
        canvas.drawCircle(x, y, radius + 2f, shadow)
        canvas.drawCircle(x, y, radius, outline)
        // Hollow circle with center dot
        val dotRadius = sizePx * 0.15f * cursorSizeMultiplier
        canvas.drawCircle(x, y, dotRadius, fill)
    }
    
    private fun drawPointer(canvas: Canvas, x: Float, y: Float) {
        // Pointing hand/finger cursor
        val s = sizePx * cursorSizeMultiplier
        arrow.reset()
        // Simplified hand pointing up
        arrow.moveTo(x, y)
        arrow.lineTo(x - s * 0.3f, y + s * 0.5f)
        arrow.lineTo(x - s * 0.15f, y + s * 0.5f)
        arrow.lineTo(x - s * 0.15f, y + s)
        arrow.lineTo(x + s * 0.15f, y + s)
        arrow.lineTo(x + s * 0.15f, y + s * 0.5f)
        arrow.lineTo(x + s * 0.3f, y + s * 0.5f)
        arrow.close()
        
        canvas.save(); canvas.translate(2f, 3f); canvas.drawPath(arrow, shadow); canvas.restore()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }
    
    private fun drawTriangle(canvas: Canvas, x: Float, y: Float) {
        // Rounded triangle pointing upper-left (like the image)
        val s = sizePx * cursorSizeMultiplier
        arrow.reset()
        
        // Start at the sharp tip (upper left)
        arrow.moveTo(x, y)
        
        // Top edge going to upper-right with rounded corner
        arrow.lineTo(x + s * 0.6f, y)
        
        // Rounded top-right corner
        arrow.cubicTo(
            x + s * 0.7f, y,
            x + s * 0.75f, y + s * 0.1f,
            x + s * 0.75f, y + s * 0.2f
        )
        
        // Right edge going down
        arrow.lineTo(x + s * 0.75f, y + s * 0.6f)
        
        // Rounded bottom-right corner  
        arrow.cubicTo(
            x + s * 0.75f, y + s * 0.7f,
            x + s * 0.7f, y + s * 0.75f,
            x + s * 0.6f, y + s * 0.75f
        )
        
        // Bottom edge going left
        arrow.lineTo(x + s * 0.2f, y + s * 0.75f)
        
        // Rounded bottom-left corner back to tip
        arrow.cubicTo(
            x + s * 0.1f, y + s * 0.75f,
            x, y + s * 0.6f,
            x, y
        )
        arrow.close()
        
        canvas.save(); canvas.translate(2f, 3f); canvas.drawPath(arrow, shadow); canvas.restore()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }
}
