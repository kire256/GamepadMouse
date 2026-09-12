package com.droidforge.gamepadmouse.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.droidforge.gamepadmouse.input.DefaultBindings
import com.droidforge.gamepadmouse.input.MouseAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val baseSpeedPxPerSec: Float = 900f,
    val slowMultiplier: Float = 0.35f,
    val fastMultiplier: Float = 2.5f,
    val deadzone: Float = 0.15f,
    val curveExponent: Float = 1.6f,
    val scrollStepPx: Float = 220f,
    val swapSticks: Boolean = false,
    val invertScroll: Boolean = false,
    val circularScroll: Boolean = false,
    val startInMouseMode: Boolean = false,
    val chordHoldDurationMs: Long = 0L,  // 0 = instant toggle, >0 = must hold for this long
    val buttonBindings: Map<Int, MouseAction> = DefaultBindings.buttons,
    val toggleChord: Set<Int> = DefaultBindings.toggleChord,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gamepad_mouse")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_SPEED = floatPreferencesKey("base_speed")
        val SLOW_MULT = floatPreferencesKey("slow_mult")
        val FAST_MULT = floatPreferencesKey("fast_mult")
        val DEADZONE = floatPreferencesKey("deadzone")
        val CURVE = floatPreferencesKey("curve")
        val SCROLL_STEP = floatPreferencesKey("scroll_step")
        val SWAP_STICKS = booleanPreferencesKey("swap_sticks")
        val INVERT_SCROLL = booleanPreferencesKey("invert_scroll")
        val CIRCULAR_SCROLL = booleanPreferencesKey("circular_scroll")
        val START_IN_MOUSE = booleanPreferencesKey("start_in_mouse")
        val CHORD_HOLD_MS = androidx.datastore.preferences.core.longPreferencesKey("chord_hold_ms")
        val BINDINGS = stringPreferencesKey("bindings")      // "keyCode:ACTION,keyCode:ACTION"
        val TOGGLE_CHORD = stringPreferencesKey("toggle_chord") // "keyCode,keyCode"
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            baseSpeedPxPerSec = p[Keys.BASE_SPEED] ?: 900f,
            slowMultiplier = p[Keys.SLOW_MULT] ?: 0.35f,
            fastMultiplier = p[Keys.FAST_MULT] ?: 2.5f,
            deadzone = p[Keys.DEADZONE] ?: 0.15f,
            curveExponent = p[Keys.CURVE] ?: 1.6f,
            scrollStepPx = p[Keys.SCROLL_STEP] ?: 220f,
            swapSticks = p[Keys.SWAP_STICKS] ?: false,
            invertScroll = p[Keys.INVERT_SCROLL] ?: false,
            circularScroll = p[Keys.CIRCULAR_SCROLL] ?: false,
            startInMouseMode = p[Keys.START_IN_MOUSE] ?: false,
            chordHoldDurationMs = p[Keys.CHORD_HOLD_MS] ?: 0L,
            buttonBindings = p[Keys.BINDINGS]?.let(::decodeBindings) ?: DefaultBindings.buttons,
            toggleChord = p[Keys.TOGGLE_CHORD]?.let(::decodeChord) ?: DefaultBindings.toggleChord,
        )
    }

    suspend fun setBaseSpeed(v: Float) = context.dataStore.edit { it[Keys.BASE_SPEED] = v }
    suspend fun setSlowMultiplier(v: Float) = context.dataStore.edit { it[Keys.SLOW_MULT] = v }
    suspend fun setFastMultiplier(v: Float) = context.dataStore.edit { it[Keys.FAST_MULT] = v }
    suspend fun setDeadzone(v: Float) = context.dataStore.edit { it[Keys.DEADZONE] = v }
    suspend fun setScrollStep(v: Float) = context.dataStore.edit { it[Keys.SCROLL_STEP] = v }
    suspend fun setSwapSticks(v: Boolean) = context.dataStore.edit { it[Keys.SWAP_STICKS] = v }
    suspend fun setInvertScroll(v: Boolean) = context.dataStore.edit { it[Keys.INVERT_SCROLL] = v }
    suspend fun setCircularScroll(v: Boolean) = context.dataStore.edit { it[Keys.CIRCULAR_SCROLL] = v }
    suspend fun setStartInMouseMode(v: Boolean) = context.dataStore.edit { it[Keys.START_IN_MOUSE] = v }
    suspend fun setChordHoldDuration(v: Long) = context.dataStore.edit { it[Keys.CHORD_HOLD_MS] = v }
    suspend fun setBindings(b: Map<Int, MouseAction>) = context.dataStore.edit { it[Keys.BINDINGS] = encodeBindings(b) }
    suspend fun setToggleChord(c: Set<Int>) = context.dataStore.edit { it[Keys.TOGGLE_CHORD] = c.joinToString(",") }

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
