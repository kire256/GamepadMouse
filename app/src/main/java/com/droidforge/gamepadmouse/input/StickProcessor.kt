package com.droidforge.gamepadmouse.input

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.PI

/**
 * Pure math for turning analog stick input into cursor velocity.
 * No Android dependencies so it is trivially unit-testable.
 */
object StickProcessor {

    data class Velocity(val vx: Float, val vy: Float) {
        val isZero: Boolean get() = vx == 0f && vy == 0f
    }

    /**
     * Radial deadzone with rescaling so the output ramps smoothly from 0 at the deadzone edge
     * to 1.0 at full deflection (no "jump" when leaving the deadzone).
     */
    fun applyDeadzone(x: Float, y: Float, deadzone: Float): Pair<Float, Float> {
        val mag = hypot(x, y)
        if (mag < deadzone) return 0f to 0f
        val clamped = minOf(mag, 1f)
        val denom = 1f - deadzone
        if (denom <= 0f) return 0f to 0f   // guard: deadzone >= 1.0 would cause NaN
        val scaled = (clamped - deadzone) / denom
        val k = scaled / mag
        return (x * k) to (y * k)
    }

    /**
     * Response curve: exponent > 1 gives fine control near center and full speed at the edge.
     * Applied to magnitude so direction is preserved.
     */
    fun applyCurve(x: Float, y: Float, exponent: Float): Pair<Float, Float> {
        val mag = hypot(x, y)
        if (mag == 0f) return 0f to 0f
        val curved = mag.toDouble().pow(exponent.toDouble()).toFloat()
        val k = curved / mag
        return (x * k) to (y * k)
    }

    /**
     * @param x,y raw stick axes in -1..1
     * @param baseSpeedPxPerSec cursor speed at full deflection with no modifier
     * @param speedMultiplier 1.0 normal, <1 slow bumper, >1 fast bumper
     * @return velocity in px/sec
     */
    fun toVelocity(
        x: Float,
        y: Float,
        deadzone: Float,
        curveExponent: Float,
        baseSpeedPxPerSec: Float,
        speedMultiplier: Float,
    ): Velocity {
        val (dx, dy) = if (deadzone > 0f) applyDeadzone(x, y, deadzone) else (x to y)
        if (dx == 0f && dy == 0f) return Velocity(0f, 0f)
        val (cx, cy) = applyCurve(dx, dy, curveExponent)
        val speed = baseSpeedPxPerSec * speedMultiplier
        return Velocity(cx * speed, cy * speed)
    }

    /** Integrate a velocity over a frame, returning the new clamped position. */
    fun step(
        px: Float, py: Float,
        v: Velocity,
        dtSec: Float,
        maxX: Float, maxY: Float,
    ): Pair<Float, Float> {
        val nx = (px + v.vx * dtSec).coerceIn(0f, maxX)
        val ny = (py + v.vy * dtSec).coerceIn(0f, maxY)
        return nx to ny
    }

    /** Scroll distance (px) for one scroll tick from a stick axis; sign preserved. */
    fun scrollDistance(axis: Float, deadzone: Float, stepPx: Float): Float {
        if (abs(axis) < deadzone) return 0f
        val denom = 1f - deadzone
        if (denom <= 0f) return 0f
        val scaled = (abs(axis) - deadzone) / denom
        return (if (axis < 0) -1f else 1f) * scaled * stepPx
    }

    /**
     * Circular scroll: converts right-stick angular velocity into scroll distance.
     *
     * Clockwise rotation  → positive result (scroll DOWN / content up)
     * Counter-clockwise   → negative result (scroll UP / content down)
     *
     * Algorithm: cross product of previous and current stick vector gives the
     * signed angular velocity. We only act when the stick has enough magnitude
     * to be intentional (>= deadzone).
     *
     * @param prevX/prevY  right stick position in previous frame
     * @param currX/currY  right stick position in current frame
     * @param deadzone     minimum magnitude to register
     * @param stepPx       base scroll distance (amplified by angular velocity)
     * @return signed scroll distance in px (positive = down)
     */
    fun circularScrollDelta(
        prevX: Float, prevY: Float,
        currX: Float, currY: Float,
        deadzone: Float,
        stepPx: Float,
    ): Float {
        val mag = hypot(currX, currY)
        if (mag < deadzone) return 0f
        // Cross product gives sin(dθ) * |prev| * |curr| — positive = CW
        val cross = prevX * currY - prevY * currX
        // Normalise by squared magnitude so result is angular, not linear
        val prevMag = hypot(prevX, prevY)
        if (prevMag < deadzone) return 0f
        val dTheta = cross / (prevMag * mag) // ≈ sin(dθ), range -1..1
        // Amplify by stick deflection magnitude for speed control
        val speed = (mag + prevMag) / 2f  // average deflection 0..1
        return dTheta * stepPx * 30f * speed  // 30x multiplier for usable scroll speed
    }
}
