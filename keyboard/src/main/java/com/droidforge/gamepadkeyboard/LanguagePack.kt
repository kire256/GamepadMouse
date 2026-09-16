package com.droidforge.gamepadkeyboard

/**
 * A keyboard language pack: layout letter set, autocomplete wordlist, optional
 * pinyin mode. Assets are named words_<code>.txt (frequency-ranked).
 */
enum class LanguagePack(
    val code: String,
    val label: String,
    /** QWERTY middle-row order; non-ASCII chars replace their nearest English key. */
    val topRow: List<String>,
    val homeRow: List<String>,
    val bottomRow: List<String>,
    val pinyin: Boolean = false,
) {
    EN(
        "en", "English (QWERTY)",
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"),
    ),
    ES(
        "es", "Español (QWERTY)",
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l","ñ"),
        listOf("z","x","c","v","b","n","m"),
    ),
    FR(
        "fr", "Français (AZERTY)",
        listOf("a","z","e","r","t","y","u","i","o","p"),
        listOf("q","s","d","f","g","h","j","k","l","m"),
        listOf("w","x","c","v","b","n"),
    ),
    DE(
        "de", "Deutsch (QWERTZ)",
        listOf("q","w","e","r","t","z","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("y","x","c","v","b","n","m"),
    ),
    ZH(
        "zh", "中文 (Pinyin)",
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"),
        pinyin = true,
    ),
    ;

    companion object {
        fun fromCode(code: String): LanguagePack =
            entries.firstOrNull { it.code == code } ?: EN

        /** Frequency wordlist asset for a pack; null when the pack has none. */
        fun wordlistAsset(pack: LanguagePack): String? =
            if (pack == EN) "words_en.txt" else "words_${pack.code}.txt"
    }
}
