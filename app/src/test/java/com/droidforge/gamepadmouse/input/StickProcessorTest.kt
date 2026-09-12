package com.droidforge.gamepadmouse.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StickProcessorTest {

    @Test
    fun deadzone_zeroesSmallInput() {
        val (x, y) = StickProcessor.applyDeadzone(0.05f, -0.08f, 0.15f)
        assertEquals(0f, x, 0f)
        assertEquals(0f, y, 0f)
    }

    @Test
    fun deadzone_rescalesSmoothlyFromEdge() {
        // Just past the deadzone should be near zero, not a jump.
        val (x, _) = StickProcessor.applyDeadzone(0.16f, 0f, 0.15f)
        assertTrue(x > 0f && x < 0.03f)
        // Full deflection stays full.
        val (fx, _) = StickProcessor.applyDeadzone(1f, 0f, 0.15f)
        assertEquals(1f, fx, 1e-5f)
    }

    @Test
    fun deadzone_preservesDirection() {
        val (x, y) = StickProcessor.applyDeadzone(0.6f, 0.6f, 0.15f)
        assertEquals(x, y, 1e-6f)
        assertTrue(hypot(x, y) <= 1f)
    }

    @Test
    fun curve_isFineNearCentreAndFullAtEdge() {
        val (half, _) = StickProcessor.applyCurve(0.5f, 0f, 2f)
        assertEquals(0.25f, half, 1e-5f)
        val (full, _) = StickProcessor.applyCurve(1f, 0f, 2f)
        assertEquals(1f, full, 1e-5f)
    }

    @Test
    fun velocity_appliesSpeedAndMultiplier() {
        val v = StickProcessor.toVelocity(1f, 0f, 0f, 1f, 900f, 2.5f)
        assertEquals(2250f, v.vx, 1e-3f)
        assertEquals(0f, v.vy, 0f)
        val slow = StickProcessor.toVelocity(1f, 0f, 0f, 1f, 900f, 0.35f)
        assertEquals(315f, slow.vx, 1e-3f)
    }

    @Test
    fun velocity_insideDeadzoneIsZero() {
        val v = StickProcessor.toVelocity(0.1f, 0.1f, 0.2f, 1.6f, 900f, 1f)
        assertTrue(v.isZero)
    }

    @Test
    fun step_clampsToScreen() {
        val (x, y) = StickProcessor.step(1070f, 5f, StickProcessor.Velocity(1000f, -1000f), 0.1f, 1080f, 1920f)
        assertEquals(1080f, x, 0f)
        assertEquals(0f, y, 0f)
    }

    @Test
    fun step_integratesOverDt() {
        val (x, y) = StickProcessor.step(100f, 100f, StickProcessor.Velocity(600f, -300f), 1f / 60f, 1080f, 1920f)
        assertEquals(110f, x, 1e-3f)
        assertEquals(95f, y, 1e-3f)
    }

    @Test
    fun scroll_respectsDeadzoneAndSign() {
        assertEquals(0f, StickProcessor.scrollDistance(0.1f, 0.15f, 200f), 0f)
        assertTrue(StickProcessor.scrollDistance(-1f, 0.15f, 200f) < 0f)
        assertEquals(200f, StickProcessor.scrollDistance(1f, 0.15f, 200f), 1e-4f)
        assertFalse(StickProcessor.scrollDistance(0.5f, 0.15f, 200f) > 200f)
    }
}
