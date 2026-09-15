package com.droidforge.gamepadkeyboard

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordLearnerTest {

    private fun learner() = WordLearner(ApplicationProvider.getApplicationContext())

    @Test
    fun `record and recall`() {
        val l = learner()
        l.record("flurb")
        assertTrue(l.knows("flurb"))
        assertTrue(l.knows("Flurb")) // case-insensitive
        assertFalse(l.knows("nope"))
    }

    @Test
    fun `frequency accumulates and prefixes rank by use`() {
        val l = learner()
        repeat(3) { l.record("banana") }
        l.record("bandana")
        l.record("ban")
        val out = l.withPrefix("ban")
        assertEquals("banana", out.first()) // 3 uses beats 1
        assertTrue("bandana" in out)
    }

    @Test
    fun `rejects junk words`() {
        val l = learner()
        l.record("x")        // too short
        l.record("ab3")      // not all letters
        l.record("")         // empty
        assertTrue(l.all().isEmpty())
    }

    @Test
    fun `forget removes and persists across instances`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val l1 = WordLearner(ctx)
        l1.record("persistme")
        val l2 = WordLearner(ctx) // fresh instance, same storage
        assertTrue(l2.knows("persistme"))
        assertTrue(l2.forget("persistme"))
        assertFalse(WordLearner(ctx).knows("persistme"))
    }
}
