package com.droidforge.gamepadkeyboard

import android.content.Context

/**
 * Ranked-word autocomplete. Words come from a frequency-ranked list
 * (google-10000-english, public domain / MIT) MERGED with words learned from
 * use (WordLearner — confirmed words outrank static completions).
 * Production loads from assets; tests may inject lines directly.
 */
class Suggester(
    context: Context,
    wordProvider: (() -> List<String>)? = null,
    private val learner: WordLearner? = null,
) {

    private val words: List<String> = wordProvider?.invoke() ?: loadFromAssets(context)

    /** Membership set — the list itself is frequency-ranked (NOT sorted), so no binary search. */
    private val wordSet: Set<String> = words.toHashSet()

    /** Diagnostic for tests/settings: how many words loaded (0 = asset missing). */
    val wordCount: Int get() = words.size

    /** Null when the asset loaded fine; the error message when it didn't. */
    val loadError: String? get() = _loadError
    private var _loadError: String? = null

    /** Last committed word, for caret-left completions. */
    var previousWord: String? = null

    private fun loadFromAssets(context: Context): List<String> =
        runCatching {
            context.assets.open(WORDLIST).bufferedReader().readLines().map { it.trim().lowercase() }
        }.getOrElse { e ->
            _loadError = e.message ?: e.javaClass.simpleName
            emptyList()
        }

    /** Static-list completions (no learned words), frequency-ranked. */
    fun suggest(fragment: String, max: Int = 3): List<String> {
        if (fragment.length < 2 || words.isEmpty()) return emptyList()
        val f = fragment.lowercase()
        val out = ArrayList<String>(max)
        if (f in wordSet) out.add(f)
        for (w in words) {
            if (out.size >= max) break
            if (w.length > f.length && w.startsWith(f) && w !in out) out.add(w)
        }
        return out
    }

    /**
     * Candidates for the strip: the literal fragment (typed text) first, then
     * LEARNED words with that prefix (use-frequency order), then static completions.
     * Empty fragment → last committed word. Deduped, max 3.
     */
    fun stripCandidates(fragment: String): List<String> {
        if (fragment.isEmpty()) {
            return listOfNotNull(previousWord).take(3)
        }
        val literal = fragment.lowercase()
        val learned = learner?.withPrefix(literal, 3).orEmpty()
        return (listOf(literal) + learned + suggest(literal)).distinct().take(3)
    }

    private companion object {
        const val WORDLIST = "words_en.txt"
    }
}
