package com.droidforge.gamepadkeyboard

import android.content.Context
import org.json.JSONObject

/**
 * Pinyin → hanzi conversion for Chinese input. Type "nihao" and the strip offers
 * 你好. Candidates come from a CC-CEDICT-derived index (pinyin → hanzi, ranked by
 * usage frequency from the opensubs corpus). Longest-prefix matching, so
 * "nihao" resolves as a phrase before "ni hao" splits into two syllables.
 */
class PinyinEngine(context: Context) {

    private val index: Map<String, List<String>>

    init {
        val map = HashMap<String, List<String>>()
        runCatching {
            val json = JSONObject(context.assets.open(INDEX).bufferedReader().readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = json.getJSONArray(k)
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                map[k] = list
            }
        }
        index = map
    }

    val loaded: Boolean get() = index.isNotEmpty()

    /**
     * Hanzi candidates for the raw pinyin typed so far (letters only, lowercase).
     * Tries progressively shorter prefixes: "nihao" as a whole, then "ni"+"hao"
     * joined top-1s, then just the leading syllable — mirroring real IME behavior.
     */
    fun candidates(raw: String, max: Int = 3): List<String> {
        val pinyin = raw.lowercase().filter { it in 'a'..'z' }
        if (pinyin.length < 2 || !loaded) return emptyList()

        // 1) Whole-string phrase hit
        index[pinyin]?.let { return it.take(max) }

        // 2) Longest proper prefix that has candidates (e.g. "niha" → "ni" phrase start)
        var cut = pinyin.length
        while (cut > 1) {
            val prefix = pinyin.substring(0, cut)
            val head = index[prefix]
            if (head != null) {
                val rest = pinyin.substring(cut)
                if (rest.isEmpty()) return head.take(max)
                // Join: head candidates + best single candidate(s) for the remainder
                val restCands = candidates(rest, 2)
                val joined = restCands.mapNotNull { r -> head.firstOrNull()?.let { it + r } }
                return (joined + head).take(max)
            }
            cut--
        }

        // 3) Fall back to first-syllable candidates
        val first = index.entries.firstOrNull { pinyin.startsWith(it.key) }?.value
        return first?.take(max) ?: emptyList()
    }

    private companion object {
        const val INDEX = "pinyin_index.json"
    }
}
