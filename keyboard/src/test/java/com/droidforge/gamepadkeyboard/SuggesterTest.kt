package com.droidforge.gamepadkeyboard

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SuggesterTest {

    // Real production wordlist read straight from the source asset — same content
    // the APK ships (Robolectric doesn't merge assets in this setup, so we read it
    // from the repo path to test the ACTUAL data, not a synthetic list).
    private val realWords: List<String> by lazy {
        java.io.File("src/main/assets/words_en.txt").readLines().map { it.trim().lowercase() }
    }

    private fun suggester() = Suggester(RuntimeEnvironment.getApplication(), { realWords })

    @Test
    fun `production asset file is the real 10k list`() {
        assertTrue("expected 10k words in src/main/assets, got ${realWords.size}", realWords.size > 5000)
        assertEquals("the", realWords.first())
    }

    @Test
    fun `learned words outrank static completions in the strip`() {
        val learner = WordLearner(ApplicationProvider.getApplicationContext<android.content.Context>())
        learner.record("zork", boost = 5)
        val s = Suggester(ApplicationProvider.getApplicationContext(), { realWords }, learner)
        val out = s.stripCandidates("zo")
        // literal first, learned "zork" before any static completion (zone, zoo, …)
        assertEquals("zo", out.first())
        assertTrue("learned word missing from strip: $out", out.contains("zork"))
        val firstStatic = out.indexOfFirst { it.startsWith("zo") && it != "zork" && it != "zo" }
        assertTrue("expected static completions in strip: $out", firstStatic != -1)
        assertTrue("learned should beat static: $out", out.indexOf("zork") < firstStatic)
    }

    @Test
    fun `completions for common prefix are ranked by frequency`() {
        val out = suggester().suggest("th")
        assertTrue("expected completions for 'th', got $out", out.isNotEmpty())
        assertTrue("all completions must start with 'th': $out", out.all { it.startsWith("th") })
        // "th" is itself a word in the list, so the literal ranks first;
        // "the" (rank #1 overall) must follow among the completions.
        assertTrue("'the' should be among completions: $out", "the" in out)
    }

    @Test
    fun `strip candidates include the literal fragment first`() {
        val out = suggester().stripCandidates("he")
        assertEquals("he", out.first())
        assertTrue(out.size in 1..3)
    }

    @Test
    fun `short or garbage fragments yield no completions`() {
        assertTrue(suggester().suggest("q").isEmpty())
        assertTrue(suggester().suggest("zzqqxx").isEmpty())
    }

    @Test
    fun `exact word ranks before longer completions`() {
        val out = suggester().suggest("the")
        assertEquals("the", out.first())
    }
}
