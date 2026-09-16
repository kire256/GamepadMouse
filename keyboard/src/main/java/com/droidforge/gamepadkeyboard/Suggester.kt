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

    /**
     * Closest dictionary word to [word] within Levenshtein distance [maxDistance]
     * (same first letter, length ±2, frequency-ranked). Null when nothing is close
     * enough — used for autocorrect-on-space. Cheap early exits keep this fast.
     */
    fun bestCorrection(word: String, maxDistance: Int = 2): String? {
        val w = word.lowercase()
        if (w.length < 3 || w[0] !in 'a'..'z') return null
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for ((rank, cand) in words.withIndex()) {
            if (cand[0] != w[0] || cand == w) continue
            if (kotlin.math.abs(cand.length - w.length) > maxDistance) continue
            if (levenshteinCapped(w, cand, maxDistance) <= maxDistance && rank < bestRank) {
                best = cand
                bestRank = rank
            }
        }
        return best
    }

    /** Bounded Levenshtein: early-exits once the distance exceeds [cap]. */
    private fun levenshteinCapped(a: String, b: String, cap: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val sub = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, sub)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > cap) return cap + 1
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }

    /** Diagnostic for tests/settings: how many words loaded (0 = asset missing). */
    val wordCount: Int get() = words.size

    /** Null when the asset loaded fine; the error message when it didn't. */
    val loadError: String? get() = _loadError
    private var _loadError: String? = null

    /** Last committed word, for caret-left completions. */
    var previousWord: String? = null

    private fun loadFromAssets(context: Context): List<String> {
        val fromAsset = runCatching {
            context.assets.open(WORDLIST).bufferedReader().readLines()
                // Format: "word count" (opensubs 50k) or plain "word" (10k legacy)
                .map { it.substringBefore(' ').trim().lowercase() }
                .filter { it.length >= 2 }
        }.getOrElse { e ->
            _loadError = e.message ?: e.javaClass.simpleName
            emptyList()
        }
        // Canonical contractions ranked ahead of the corpus (the opensubs list has
        // "doesnt" but no apostrophe forms; English wants doesn't/can't/I'm).
        return CONTRACTIONS + fromAsset
    }

    /** High-frequency apostrophe forms, ranked as a block at the top of the dict. */
    private val CONTRACTIONS = listOf(
        "can't", "don't", "won't", "doesn't", "didn't", "isn't", "aren't", "wasn't",
        "weren't", "hasn't", "haven't", "hadn't", "couldn't", "shouldn't", "wouldn't",
        "i'm", "i've", "i'll", "i'd", "it's", "that's", "there's", "what's", "let's",
        "we're", "they're", "you're", "you've", "you'll", "he's", "she's", "who's",
        "here's", "we've", "we'll", "they've", "o'clock",
    )

    /** Canonical apostrophe form for an apostrophe-less typing ("doesnt"→"doesn't"). */
    private val canonByStripped: Map<String, String> =
        CONTRACTIONS.associateBy { it.replace("'", "") }

    /** Static-list completions (no learned words), frequency-ranked. */
    fun suggest(fragment: String, max: Int = 3): List<String> {
        if (fragment.length < 2 || words.isEmpty()) return emptyList()
        val f = fragment.lowercase()
        val out = ArrayList<String>(max)
        // "doesnt" → canonical "doesn't" ahead of everything else
        val canonical = canonByStripped[f]
        if (canonical != null) out.add(canonical)
        if (f in wordSet && canonical == null) out.add(f)
        for (w in words) {
            if (out.size >= max) break
            if (w.length > f.length && w.startsWith(f) && w !in out) out.add(w)
        }
        return out
    }

    /**
     * Dictionary membership for autocorrect decisions. Bare forms with a canonical
     * apostrophe spelling ("doesnt", "dont", "cant") are treated as NOT known so
     * space converts them — except genuinely ambiguous words (its, id, ill).
     */
    private val ambiguousBare = setOf("its", "id", "ill", "well", "were")
    fun knows(word: String): Boolean {
        val w = word.lowercase()
        if (canonByStripped.containsKey(w) && w !in ambiguousBare) return false
        return w.isNotEmpty() && w in wordSet
    }

    /**
     * Fuzzy suggestions for words with typos: rank dictionary words by
     * (1) prefix match, (2) small edit distance to the typed fragment, keeping
     * frequency order inside each tier. Best effort, single pass, cheap caps.
     */
    fun fuzzySuggest(fragment: String, max: Int = 3): List<String> {
        val f = fragment.lowercase()
        if (f.length < 3 || words.isEmpty()) return emptyList()
        val prefix = suggest(f, max)                       // tier 1: completions
        if (prefix.size >= max) return prefix

        // Tier 2: nearest words by edit distance (≤2, tolerant of transpositions)
        val cap = if (f.length >= 5) 2 else 1
        val scored = ArrayList<Pair<Int, Int>>(8)         // (distance, freq-rank)
        for ((rank, w) in words.withIndex()) {
            if (w in prefix || kotlin.math.abs(w.length - f.length) > cap) continue
            if (w[0] != f[0] && w.getOrNull(1) != f.getOrNull(1)) continue
            val d = levenshteinCapped(f, w, cap)
            if (d <= cap) scored.add(d to rank)           // distance first, then freq
        }
        scored.sortWith(compareBy({ it.first }, { it.second }))
        return (prefix + scored.take(max - prefix.size).map { words[it.second] })
            .distinct().take(max)
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
        val base = (listOf(literal) + learned + suggest(literal)).distinct()
        if (base.size >= 3) return base.take(3)
        // Typed fragment looks like a typo (few/no prefix matches) → fuzzy tier
        val fuzzy = fuzzySuggest(literal).filter { it !in base }
        return (base + fuzzy).distinct().take(3)
    }

    private companion object {
        const val WORDLIST = "words_en.txt"
    }
}
