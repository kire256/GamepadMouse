package com.droidforge.gamepadkeyboard

import android.content.Context

/**
 * Ranked-word autocomplete. Loads a frequency-ranked wordlist once from assets
 * (google-10000-english, public domain / MIT) and scores candidates:
 * frequency rank first, then length (shorter = likelier completion).
 */
class Suggester(context: Context) {

    private val words: List<String> = run {
        runCatching {
            context.assets.open(WORDLIST).bufferedReader().readLines().map { it.trim().lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Membership set — the list itself is frequency-ranked (NOT sorted), so no binary search. */
    private val wordSet: Set<String> = words.toHashSet()

    /** Last committed word, for caret-left completions. */
    var previousWord: String? = null

    /**
     * Completions for [fragment] (the word being typed, lowercase, letters only).
     * Fragment itself comes first when it's a real word; up to [max] total.
     */
    fun suggest(fragment: String, max: Int = 3): List<String> {
        if (fragment.length < 2 || words.isEmpty()) return emptyList()
        val f = fragment.lowercase()
        val out = ArrayList<String>(max)
        // Exact word ranks first (typed so far is already a word)
        if (f in wordSet) out.add(f)
        for (w in words) {
            if (out.size >= max) break
            if (w.length > f.length && w.startsWith(f) && w !in out) out.add(w)
        }
        return out
    }

    /**
     * Three candidates for the strip: the literal fragment (typed text) plus the
     * top completions, deduped, fragment-first.
     */
    fun stripCandidates(fragment: String): List<String> {
        if (fragment.isEmpty()) {
            return listOfNotNull(previousWord).take(3)
        }
        val literal = fragment.lowercase()
        return (listOf(literal) + suggest(literal)).distinct().take(3)
    }

    private companion object {
        const val WORDLIST = "words_en.txt"
    }
}
