package com.droidforge.gamepadmouse.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.droidforge.gamepadmouse.input.DefaultBindings
import com.droidforge.gamepadmouse.input.BindingCodec
import com.droidforge.gamepadmouse.input.ButtonBinding
import com.droidforge.gamepadmouse.input.MouseAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val baseSpeedPxPerSec: Float = 900f,
    val slowMultiplier: Float = 0.35f,
    val fastMultiplier: Float = 2.5f,
    val deadzone: Float = 0.15f,
    val curveExponent: Float = 1.6f,
    val scrollStepPx: Float = 180f,
    val invertScroll: Boolean = false,
    val swapSticks: Boolean = false,
    val circularScroll: Boolean = false,
    val startInMouseMode: Boolean = true,
    val toggleChord: Set<Int> = DefaultBindings.toggleChord,
    val chordHoldDurationMs: Long = 0L,
    val buttonBindings: Map<Int, MouseAction> = DefaultBindings.buttons,
    val detailedBindings: List<ButtonBinding> = DefaultBindings.detailed,
    val audioPack: String = "MINIMAL",  // AudioPack enum name
    val cursorStyle: String = "ARROW",   // CursorStyle enum name
    val cursorSize: Float = 1.0f,        // Cursor size multiplier (0.5 to 2.0)
    val cursorColor: Int = 0xFFFFFFFF.toInt(),  // ARGB color
    val autoHideTimeoutMs: Long = 3000L,
    val keyboardWidthPercent: Float = 80f,
    val keyboardHeightPercent: Float = 45f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gamepad_mouse")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_SPEED = floatPreferencesKey("base_speed")
        val SLOW_MULT = floatPreferencesKey("slow_mult")
        val FAST_MULT = floatPreferencesKey("fast_mult")
        val DEADZONE = floatPreferencesKey("deadzone")
        val CURVE_EXP = floatPreferencesKey("curve_exp")
        val SCROLL_STEP = floatPreferencesKey("scroll_step")
        val INVERT_SCROLL = booleanPreferencesKey("invert_scroll")
        val SWAP_STICKS = booleanPreferencesKey("swap_sticks")
        val CIRCULAR_SCROLL = booleanPreferencesKey("circular_scroll")
        val START_IN_MOUSE_MODE = booleanPreferencesKey("start_in_mouse_mode")
        val TOGGLE_CHORD = stringSetPreferencesKey("toggle_chord")
        val CHORD_HOLD_DURATION = longPreferencesKey("chord_hold_duration")
        val BINDINGS = stringPreferencesKey("bindings")
        val DETAILED_BINDINGS = stringPreferencesKey("detailed_bindings_v2")
        val AUDIO_PACK = stringPreferencesKey("audio_pack")
        val CURSOR_STYLE = stringPreferencesKey("cursor_style")
        val CURSOR_SIZE = floatPreferencesKey("cursor_size")
        val CURSOR_COLOR = intPreferencesKey("cursor_color")
        val AUTO_HIDE_TIMEOUT = longPreferencesKey("auto_hide_timeout_ms")
        val KEYBOARD_WIDTH = floatPreferencesKey("keyboard_width_percent")
        val KEYBOARD_HEIGHT = floatPreferencesKey("keyboard_height_percent")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        // Migrate toggleChord from old string format to stringSet
        val toggleChordSet = try {
            p[Keys.TOGGLE_CHORD]?.mapNotNull { it.toIntOrNull() }?.toSet()
        } catch (e: ClassCastException) {
            // Old format was a string, try to parse it
            null
        }
        
        Settings(
            baseSpeedPxPerSec = p[Keys.BASE_SPEED] ?: 900f,
            slowMultiplier = p[Keys.SLOW_MULT] ?: 0.35f,
            fastMultiplier = p[Keys.FAST_MULT] ?: 2.5f,
            deadzone = p[Keys.DEADZONE] ?: 0.15f,
            curveExponent = p[Keys.CURVE_EXP] ?: 1.6f,
            scrollStepPx = p[Keys.SCROLL_STEP] ?: 180f,
            invertScroll = p[Keys.INVERT_SCROLL] ?: false,
            swapSticks = p[Keys.SWAP_STICKS] ?: false,
            circularScroll = p[Keys.CIRCULAR_SCROLL] ?: false,
            startInMouseMode = p[Keys.START_IN_MOUSE_MODE] ?: true,
            toggleChord = toggleChordSet ?: DefaultBindings.toggleChord,
            chordHoldDurationMs = p[Keys.CHORD_HOLD_DURATION] ?: 0L,
            buttonBindings = p[Keys.BINDINGS]?.let(::decodeBindings) ?: DefaultBindings.buttons,
            detailedBindings = p[Keys.DETAILED_BINDINGS]?.let(BindingCodec::decode)
                ?: p[Keys.BINDINGS]?.let(BindingCodec::decode)
                ?: DefaultBindings.detailed,
            audioPack = p[Keys.AUDIO_PACK] ?: "MINIMAL",
            cursorStyle = p[Keys.CURSOR_STYLE] ?: "ARROW",
            cursorSize = p[Keys.CURSOR_SIZE] ?: 1.0f,
            cursorColor = p[Keys.CURSOR_COLOR] ?: 0xFFFFFFFF.toInt(),
            autoHideTimeoutMs = p[Keys.AUTO_HIDE_TIMEOUT] ?: 3000L,
            keyboardWidthPercent = p[Keys.KEYBOARD_WIDTH] ?: 80f,
            keyboardHeightPercent = p[Keys.KEYBOARD_HEIGHT] ?: 45f,
        )
    }

    suspend fun setBaseSpeed(v: Float) = context.dataStore.edit { it[Keys.BASE_SPEED] = v }
    suspend fun setSlowMultiplier(v: Float) = context.dataStore.edit { it[Keys.SLOW_MULT] = v }
    suspend fun setFastMultiplier(v: Float) = context.dataStore.edit { it[Keys.FAST_MULT] = v }
    suspend fun setDeadzone(v: Float) = context.dataStore.edit { it[Keys.DEADZONE] = v }
    suspend fun setCurveExponent(v: Float) = context.dataStore.edit { it[Keys.CURVE_EXP] = v }
    suspend fun setScrollStep(v: Float) = context.dataStore.edit { it[Keys.SCROLL_STEP] = v }
    suspend fun setSwapSticks(v: Boolean) = context.dataStore.edit { it[Keys.SWAP_STICKS] = v }
    suspend fun setInvertScroll(v: Boolean) = context.dataStore.edit { it[Keys.INVERT_SCROLL] = v }
    suspend fun setCircularScroll(v: Boolean) = context.dataStore.edit { it[Keys.CIRCULAR_SCROLL] = v }
    suspend fun setStartInMouseMode(v: Boolean) = context.dataStore.edit { it[Keys.START_IN_MOUSE_MODE] = v }
    suspend fun setChordHoldDuration(v: Long) = context.dataStore.edit { it[Keys.CHORD_HOLD_DURATION] = v }
    suspend fun setBindings(b: Map<Int, MouseAction>) = context.dataStore.edit { it[Keys.BINDINGS] = encodeBindings(b) }
    suspend fun setDetailedBindings(bindings: List<ButtonBinding>) = context.dataStore.edit {
        it[Keys.DETAILED_BINDINGS] = BindingCodec.encode(bindings)
    }
    suspend fun setToggleChord(c: Set<Int>) = context.dataStore.edit { it[Keys.TOGGLE_CHORD] = c.map { it.toString() }.toSet() }
    suspend fun setAudioPack(pack: String) = context.dataStore.edit { it[Keys.AUDIO_PACK] = pack }
    suspend fun setCursorStyle(style: String) = context.dataStore.edit { it[Keys.CURSOR_STYLE] = style }
    suspend fun setCursorSize(size: Float) = context.dataStore.edit { it[Keys.CURSOR_SIZE] = size }
    suspend fun setCursorColor(color: Int) = context.dataStore.edit { it[Keys.CURSOR_COLOR] = color }
    suspend fun setAutoHideTimeout(ms: Long) = context.dataStore.edit { it[Keys.AUTO_HIDE_TIMEOUT] = ms }
    suspend fun setKeyboardWidthPercent(value: Float) = context.dataStore.edit { it[Keys.KEYBOARD_WIDTH] = value.coerceIn(40f, 100f) }
    suspend fun setKeyboardHeightPercent(value: Float) = context.dataStore.edit { it[Keys.KEYBOARD_HEIGHT] = value.coerceIn(25f, 80f) }

    companion object {
        fun encodeBindings(b: Map<Int, MouseAction>): String =
            b.entries.joinToString(",") { "${it.key}:${it.value.name}" }

        fun decodeBindings(s: String): Map<Int, MouseAction> =
            s.split(",").filter { it.contains(":") }.mapNotNull { pair ->
                val (k, v) = pair.split(":", limit = 2)
                val code = k.toIntOrNull() ?: return@mapNotNull null
                val action = runCatching { MouseAction.valueOf(v) }.getOrNull() ?: return@mapNotNull null
                code to action
            }.toMap()

        fun decodeChord(s: String): Set<Int> =
            s.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet().ifEmpty { DefaultBindings.toggleChord }
    }
}
