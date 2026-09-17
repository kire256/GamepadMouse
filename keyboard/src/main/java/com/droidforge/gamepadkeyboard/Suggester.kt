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
        // Proper nouns: "Erik" looks like a typo of "eric" to any dictionary —
        // a Capitalized word (not ALL-CAPS) is never autocorrected.
        if (word.length > 1 && word[0].isUpperCase() && word.drop(1).any { it.isLowerCase() }) return null
        val w = word.lowercase()
        if (w.length < 3 || w[0] !in 'a'..'z') return null
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        var bestRank = Int.MAX_VALUE
        for ((rank, cand) in words.withIndex()) {
            if (cand[0] != w[0] || cand == w) continue
            if (kotlin.math.abs(cand.length - w.length) > maxDistance) continue
            if (levenshteinCapped(w, cand, maxDistance) > maxDistance) continue
            // Positional first (a neighbor-key typo is a far likelier intent than a
            // same-distance random letter), frequency as tiebreak.
            val score = positionalScore(w, cand) * 1000 + rank
            if (score < bestScore || (score == bestScore && rank < bestRank)) {
                best = cand
                bestScore = score
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

    /** High-frequency apostrophe forms — companion so loadFromAssets can read them
     *  during construction (instance properties aren't initialized in call order). */
    private companion object {
        val CONTRACTIONS = listOf(
            "can't", "don't", "won't", "doesn't", "didn't", "isn't", "aren't", "wasn't",
            "weren't", "hasn't", "haven't", "hadn't", "couldn't", "shouldn't", "wouldn't",
            "i'm", "i've", "i'll", "i'd", "it's", "that's", "there's", "what's", "let's",
            "we're", "they're", "you're", "you've", "you'll", "he's", "she's", "who's",
            "here's", "we've", "we'll", "they've", "o'clock",
        )

        /** Canonical apostrophe form for an apostrophe-less typing ("doesnt"→"doesn't"). */
        val canonByStripped: Map<String, String> =
            CONTRACTIONS.associateBy { it.replace("'", "") }

        const val WORDLIST = "words_en.txt"
    }

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

    // ---- next-word prediction -------------------------------------------------
    // On-device pair learning: every committed word records prev→next with a
    // weight; static seeds cover common bigrams so prediction works from day one.

    private val pairWeights = LinkedHashMap<String, Int>()

    /** Static seed: common English bigrams (boot-time predictions, no history needed). */
    private val seedPairs = mapOf(
        "the" to listOf("same", "way", "most", "other"),
        "of" to listOf("the", "course"),
        "and" to listOf("the", "then"),
        "to" to listOf("the", "be", "do"),
        "in" to listOf("the", "fact"),
        "is" to listOf("the", "not", "a"),
        "that" to listOf("the", "is"),
        "you" to listOf("can", "are", "know", "should"),
        "it" to listOf("is", "was", "will"),
        "for" to listOf("the", "a"),
        "with" to listOf("the", "me", "you"),
        "this" to listOf("is", "was"),
        "but" to listOf("the", "i"),
        "not" to listOf("the", "only", "to"),
        "on" to listOf("the", "top"),
        "as" to listOf("the", "well"),
        "are" to listOf("the", "you", "not"),
        "we" to listOf("are", "can", "will", "have"),
        "can" to listOf("be", "get", "do", "you"),
        "will" to listOf("be", "not", "have"),
        "have" to listOf("to", "been", "a"),
        "has" to listOf("been", "to"),
        "was" to listOf("the", "not", "a"),
        "would" to listOf("be", "like", "have"),
        "could" to listOf("be", "have", "get"),
        "should" to listOf("be", "have"),
        "do" to listOf("you", "not", "it"),
        "does" to listOf("not", "it"),
        "if" to listOf("you", "the", "it"),
        "no" to listOf("one", "longer", "matter"),
        "good" to listOf("idea", "luck", "morning"),
        "how" to listOf("are", "do", "much", "about"),
        "all" to listOf("the", "of"),
        "there" to listOf("is", "are", "was"),
        "when" to listOf("you", "the", "it"),
        "what" to listOf("is", "do", "about"),
        "one" to listOf("of", "day"),
        "my" to listOf("own", "life"),
        "your" to listOf("own", "name"),
        "new" to listOf("one", "york"),
        "at" to listOf("the", "least", "all"),
        "from" to listOf("the", "me"),
        "they" to listOf("are", "will", "have"),
        "i" to listOf("don't", "am", "was", "will", "think", "can")
    )

    /** Record that [next] followed [prev] (called by the service on every commit). */
    fun recordPair(prev: String, next: String) {
        val p = prev.lowercase().trim()
        val n = next.lowercase().trim()
        if (p.isEmpty() || n.isEmpty()) return
        val key = "$p $n"
        val w = (pairWeights[key] ?: 0) + 1
        pairWeights[key] = w
        if (pairWeights.size > 500) {  // bound memory: drop oldest-inserted
            val it = pairWeights.entries.iterator()
            it.next(); it.remove()
        }
    }

    /** Next-word candidates for [prev]: learned pairs (weighted) over static seeds. */
    fun predictNext(prev: String?, max: Int = 3): List<String> {
        if (prev.isNullOrBlank()) return emptyList()
        val p = prev.lowercase().trim()
        val scored = ArrayList<Pair<String, Int>>()
        for ((k, w) in pairWeights) {
            val i = k.indexOf(' ')
            if (i > 0 && k.startsWith("$p ")) scored.add(k.substring(i + 1) to w)
        }
        scored.sortByDescending { it.second }
        val out = scored.map { it.first }.toMutableList()
        for (s in seedPairs[p].orEmpty()) if (s !in out) out.add(s)
        return out.take(max)
    }

    // ---- positional (QWERTY-geometry) typo scoring -----------------------------
    // Finger-accuracy model: a mistyped letter is USUALLY a key adjacent to the
    // intended one, not a random letter. hwllo → w is adjacent to e → hello.

    private val keyPos: Map<Char, Pair<Int, Int>> = buildMap {
        "qwertyuiop".forEachIndexed { i, ch -> put(ch, 0 to i) }
        "asdfghjkl".forEachIndexed { i, ch -> put(ch, 1 to i + 1) }
        "zxcvbnm".forEachIndexed { i, ch -> put(ch, 2 to i + 2) }
    }

    /** True when a/b are keyboard neighbors (incl. diagonals) or the same key. */
    private fun adjacent(a: Char, b: Char): Boolean {
        if (a == b) return true
        val pa = keyPos[a] ?: return false
        val pb = keyPos[b] ?: return false
        return Math.abs(pa.first - pb.first) <= 1 && Math.abs(pa.second - pb.second) <= 1
    }

    /**
     * Positional penalty for [typed] vs [candidate] (same length): each mismatch
     * costs 4 if the letters are QWERTY-neighbors, 9 otherwise.
     */
    private fun positionalScore(typed: String, candidate: String): Int {
        if (typed.length != candidate.length) return 99
        var cost = 0
        for (i in typed.indices) {
            if (typed[i] != candidate[i]) {
                cost += if (adjacent(typed[i], candidate[i])) 4 else 9
            }
        }
        return cost
    }

    /** Glide (Swype-style) resolve: real traces are LONGER than the word (the path
     *  crosses pass-through keys), so [word] must be an in-order subsequence OF the
     *  trace. Consecutive duplicate keys (sampling jitter) collapse first; ranked by
     *  fewest leftover trace letters, then frequency. Learned words join the pool. */
    fun glideCandidates(trace: String, max: Int = 3): List<String> {
        if (trace.length < 2) return emptyList()
        val t = StringBuilder(trace.length)
        var prev = ' '
        for (ch in trace) if (ch != prev) { t.append(ch); prev = ch }  // lll → l
        val T = t.toString()
        if (T.length < 2) return emptyList()

        // Doubled letters (hello→helo) may consume a single trace occurrence —
        // real fingers don't always loop a key twice.
        fun collapse(w: String): String {
            val sb = StringBuilder(w.length)
            var p = ' '
            for (ch in w) if (ch != p) { sb.append(ch); p = ch }
            return sb.toString()
        }

        fun subsequence(wRaw: String): Boolean {
            val w = collapse(wRaw)
            var ti = 0
            for (ch in w) {
                while (ti < T.length && T[ti] != ch) ti++
                if (ti == T.length) return false
                ti++
            }
            return true
        }

        data class Cand(val word: String, val extras: Int, val rank: Int)
        val out = ArrayList<Cand>()
        for ((rank, w) in words.withIndex()) {
            if (w.length < 2 || w.length > T.length + 3) continue
            if (subsequence(w)) out.add(Cand(w, T.length - collapse(w).length, rank))
        }
        learner?.all()?.keys?.let { learned ->
            for (w in learned) {
                if (w.length < 2 || w.length > T.length + 3) continue
                if (subsequence(w)) out.add(Cand(w, T.length - collapse(w).length + 1, -1))
            }
        }
        out.sortWith(compareBy({ it.extras }, { it.rank }))
        return out.take(max).map { it.word }
    }

    /**
     * Candidates for the strip: the literal fragment (typed text) first, then
     * LEARNED words with that prefix (use-frequency order), then static completions.
     * Empty fragment → next-word prediction from the previous word. Deduped, max 3.
     */
    fun stripCandidates(fragment: String): List<String> {
        if (fragment.isEmpty()) {
            return predictNext(previousWord).ifEmpty { listOfNotNull(previousWord) }
        }
        val literal = fragment.lowercase()
        val learned = learner?.withPrefix(literal, 3).orEmpty()
        val base = (listOf(literal) + learned + suggest(literal)).distinct()
        if (base.size >= 3) return base.take(3)
        // Typed fragment looks like a typo (few/no prefix matches) → fuzzy tier
        val fuzzy = fuzzySuggest(literal).filter { it !in base }
        return (base + fuzzy).distinct().take(3)
    }
}