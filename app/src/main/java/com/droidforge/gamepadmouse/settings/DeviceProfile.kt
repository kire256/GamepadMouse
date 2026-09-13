package com.droidforge.gamepadmouse.settings

import kotlinx.serialization.Serializable

/**
 * A profile for a specific gamepad device.
 * Stores all settings associated with one controller.
 */
@Serializable
data class DeviceProfile(
    val deviceId: String,              // Unique device identifier
    val deviceName: String,            // Human-readable name
    val settings: ProfileSettings,     // All the actual settings
    val lastUsed: Long = 0L           // Timestamp for sorting
)

/**
 * Settings that can be saved per-profile.
 * Mirrors the main Settings class but serializable.
 */
@Serializable
data class ProfileSettings(
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
    val toggleChord: List<Int> = listOf(96, 97),  // Set → List for serialization
    val chordHoldDurationMs: Long = 0L,
    val buttonBindings: Map<String, String> = emptyMap(),  // KeyCode → ActionName
    val audioPack: String = "MINIMAL",
    val cursorStyle: String = "ARROW",
    val cursorSize: Float = 1.0f,
    val cursorColor: Int = 0xFFFFFFFF.toInt()
)

/**
 * Convert Settings to ProfileSettings
 */
fun Settings.toProfileSettings(): ProfileSettings = ProfileSettings(
    baseSpeedPxPerSec = baseSpeedPxPerSec,
    slowMultiplier = slowMultiplier,
    fastMultiplier = fastMultiplier,
    deadzone = deadzone,
    curveExponent = curveExponent,
    scrollStepPx = scrollStepPx,
    invertScroll = invertScroll,
    swapSticks = swapSticks,
    circularScroll = circularScroll,
    startInMouseMode = startInMouseMode,
    toggleChord = toggleChord.toList(),
    chordHoldDurationMs = chordHoldDurationMs,
    buttonBindings = buttonBindings.mapKeys { it.key.toString() }
        .mapValues { it.value.name },
    audioPack = audioPack,
    cursorStyle = cursorStyle,
    cursorSize = cursorSize,
    cursorColor = cursorColor
)

/**
 * Convert ProfileSettings back to Settings
 */
fun ProfileSettings.toSettings(): Settings = Settings(
    baseSpeedPxPerSec = baseSpeedPxPerSec,
    slowMultiplier = slowMultiplier,
    fastMultiplier = fastMultiplier,
    deadzone = deadzone,
    curveExponent = curveExponent,
    scrollStepPx = scrollStepPx,
    invertScroll = invertScroll,
    swapSticks = swapSticks,
    circularScroll = circularScroll,
    startInMouseMode = startInMouseMode,
    toggleChord = toggleChord.toSet(),
    chordHoldDurationMs = chordHoldDurationMs,
    buttonBindings = buttonBindings.mapKeys { it.key.toInt() }
        .mapValues { com.droidforge.gamepadmouse.input.MouseAction.valueOf(it.value) },
    audioPack = audioPack,
    cursorStyle = cursorStyle,
    cursorSize = cursorSize,
    cursorColor = cursorColor
)
