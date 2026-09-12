package com.droidforge.gamepadmouse.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordDetectorTest {

    private val start = 108 // KEYCODE_BUTTON_START
    private val select = 109 // KEYCODE_BUTTON_SELECT
    private val a = 96

    @Test
    fun firesOnlyWhenAllKeysHeld() {
        val d = ChordDetector(setOf(start, select))
        assertFalse(d.onKeyDown(start))
        assertTrue(d.onKeyDown(select))
    }

    @Test
    fun orderDoesNotMatter() {
        val d = ChordDetector(setOf(start, select))
        assertFalse(d.onKeyDown(select))
        assertTrue(d.onKeyDown(start))
    }

    @Test
    fun ignoresUnrelatedKeys() {
        val d = ChordDetector(setOf(start, select))
        assertFalse(d.onKeyDown(a))
        assertFalse(d.onKeyUp(a))
    }

    @Test
    fun doesNotRefireUntilFullyReleased() {
        val d = ChordDetector(setOf(start, select))
        d.onKeyDown(start); assertTrue(d.onKeyDown(select))
        // Auto-repeat / re-press of one key while other still held
        d.onKeyUp(select)
        assertFalse(d.onKeyDown(select))
        d.onKeyUp(start)
        // Now fully released → arms again
        d.onKeyDown(start)
        assertTrue(d.onKeyDown(select))
    }

    @Test
    fun singleKeyChordFiresImmediately() {
        val d = ChordDetector(setOf(a))
        assertTrue(d.onKeyDown(a))
        d.onKeyUp(a)
        assertTrue(d.onKeyDown(a))
    }

    @Test
    fun changingChordResetsState() {
        val d = ChordDetector(setOf(start, select))
        d.onKeyDown(start)
        d.chord = setOf(a, select)
        assertFalse(d.onKeyDown(select))
        assertTrue(d.onKeyDown(a))
        assertEquals(setOf(a, select), d.chord)
    }
}
