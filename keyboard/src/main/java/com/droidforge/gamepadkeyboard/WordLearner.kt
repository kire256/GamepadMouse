package com.droidforge.gamepadkeyboard

import android.content.Context
import java.io.File

/**
 * Words the keyboard has learned from use: word -> frequency, persisted as TSV
 * in the IME's private storage. Space/enter/picking-a-suggestion confirm a word;
 * confirmed words get suggestion priority over the static ranked list.
 * Same-process singleton: the service creates it, Options manages it.
 */
class WordLearner private constructor(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val words = LinkedHashMap<String, Int>()

    init {
        load()
        instance = this
    }

    /** Learn [word] (confirmed by use). Only plausible words: 2+ letters. */
    @Synchronized
    fun record(word: String, boost: Int = 1) {
        val w = word.lowercase().trim()
        if (w.length < 2 || !w.all { it.isLetter() || it == '\'' } ||
            w.startsWith("'") || w.endsWith("'") || w.contains("''")
        ) return
        words[w] = ((words[w] ?: 0) + boost).coerceAtMost(MAX_FREQ)
        persist()
    }

    @Synchronized
    fun forget(word: String): Boolean {
        val removed = words.remove(word.lowercase().trim()) != null
        if (removed) persist()
        return removed
    }

    @Synchronized
    fun knows(word: String): Boolean = words.containsKey(word.lowercase().trim())

    @Synchronized
    fun all(): Map<String, Int> = words.toMap()

    /** Learned words starting with [prefix] (longer than it), best-frequency first. */
    @Synchronized
    fun withPrefix(prefix: String, limit: Int = 3): List<String> =
        words.entries.asSequence()
            .filter { it.key.length > prefix.length && it.key.startsWith(prefix) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .toList()

    /** Most-learned words overall (for the options list). */
    @Synchronized
    fun top(limit: Int = 100): List<Pair<String, Int>> =
        words.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    @Synchronized
    private fun load() {
        runCatching {
            if (!file.exists()) return
            file.readLines().forEach { line ->
                val parts = line.split("\t")
                if (parts.size == 2) {
                    val freq = parts[1].toIntOrNull() ?: return@forEach
                    if (freq > 0) words[parts[0]] = freq
                }
            }
        }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            file.writeText(words.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        }
    }

    companion object {
        private const val FILE_NAME = "learned_words.tsv"
        private const val MAX_FREQ = 9999

        /** Same-process handle for OptionsActivity; null until the service first runs. */
        @Volatile
        var instance: WordLearner? = null
            private set
    }
}
