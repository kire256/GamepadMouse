package com.droidforge.gamepadkeyboard

import android.content.Context
import android.content.SharedPreferences

/** User preferences for the keyboard (persisted via SharedPreferences). */
class KeyboardPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)

    var skinName: String
        get() = sp.getString(KEY_SKIN, Skin.STEAM_DECK.name) ?: Skin.STEAM_DECK.name
        set(v) = sp.edit().putString(KEY_SKIN, v).apply()

    var soundPackName: String
        get() = sp.getString(KEY_SOUND, SoundPack.CLASSIC.name) ?: SoundPack.CLASSIC.name
        set(v) = sp.edit().putString(KEY_SOUND, v).apply()

    var hapticsEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTICS, v).apply()

    var suggestionsEnabled: Boolean
        get() = sp.getBoolean(KEY_SUGGESTIONS, true)
        set(v) = sp.edit().putBoolean(KEY_SUGGESTIONS, v).apply()

    /** Keyboard height in dp (clamped at use site). */
    var heightDp: Int
        get() = sp.getInt(KEY_HEIGHT, 250)
        set(v) = sp.edit().putInt(KEY_HEIGHT, v).apply()

    /** Selection repeat: first-step delay and step rate, in ms. */
    var repeatDelayMs: Int
        get() = sp.getInt(KEY_REPEAT_DELAY, 400)
        set(v) = sp.edit().putInt(KEY_REPEAT_DELAY, v).apply()

    var repeatRateMs: Int
        get() = sp.getInt(KEY_REPEAT_RATE, 60)
        set(v) = sp.edit().putInt(KEY_REPEAT_RATE, v).apply()

    /** Show the ◀ ▶ text-cursor keys on the bottom bar. */
    var arrowsVisible: Boolean
        get() = sp.getBoolean(KEY_ARROWS, true)
        set(v) = sp.edit().putBoolean(KEY_ARROWS, v).apply()

    /** Active language pack code (en/es/fr/de/zh). */
    var languageCode: String
        get() = sp.getString(KEY_LANG, "en") ?: "en"
        set(v) = sp.edit().putString(KEY_LANG, v).apply()

    /** Escalating backspace: fast repeats switch to word-chunk deletes. */
    var escalatingBackspace: Boolean
        get() = sp.getBoolean(KEY_ESC_BS, true)
        set(v) = sp.edit().putBoolean(KEY_ESC_BS, v).apply()

    /** Compact letters: hide redundant punctuation keys from the main layout. */
    var compactMode: Boolean
        get() = sp.getBoolean(KEY_COMPACT, false)
        set(v) = sp.edit().putBoolean(KEY_COMPACT, v).apply()

    private companion object {
        const val KEY_SKIN = "skin"
        const val KEY_SOUND = "sound_pack"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SUGGESTIONS = "suggestions"
        const val KEY_HEIGHT = "height_dp"
        const val KEY_REPEAT_DELAY = "repeat_delay_ms"
        const val KEY_REPEAT_RATE = "repeat_rate_ms"
        const val KEY_ARROWS = "arrows_visible"
        const val KEY_LANG = "language"
        const val KEY_ESC_BS = "escalating_backspace"
        const val KEY_COMPACT = "compact_mode"
    }
}
