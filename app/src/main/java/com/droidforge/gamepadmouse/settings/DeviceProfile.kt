package com.droidforge.gamepadmouse.settings

import com.droidforge.gamepadmouse.input.BindingCodec
import com.droidforge.gamepadmouse.input.ButtonBinding
import com.droidforge.gamepadmouse.input.MouseAction
import kotlinx.serialization.Serializable

@Serializable
data class DeviceProfile(
    val deviceId: String,
    val deviceName: String,
    val settings: ProfileSettings,
    val lastUsed: Long = 0L,
)

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
    val toggleChord: List<Int> = listOf(108, 109),
    val chordHoldDurationMs: Long = 0L,
    val buttonBindings: Map<String, String> = emptyMap(),
    val detailedBindings: String = "",
    val audioPack: String = "MINIMAL",
    val cursorStyle: String = "ARROW",
    val cursorSize: Float = 1.0f,
    val cursorColor: Int = 0xFFFFFFFF.toInt(),
    val autoHideTimeoutMs: Long = 3000L,
    val keyboardWidthPercent: Float = 80f,
    val keyboardHeightPercent: Float = 45f,
    val keyboardAtTop: Boolean = false,
    val keyboardShowNumberRow: Boolean = true,
    val keyboardShowSystemKeys: Boolean = true,
    val keyboardColor: Int = 0xFF202124.toInt(),
)

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
    buttonBindings = buttonBindings.mapKeys { it.key.toString() }.mapValues { it.value.name },
    detailedBindings = BindingCodec.encode(detailedBindings),
    audioPack = audioPack,
    cursorStyle = cursorStyle,
    cursorSize = cursorSize,
    cursorColor = cursorColor,
    autoHideTimeoutMs = autoHideTimeoutMs,
    keyboardWidthPercent = keyboardWidthPercent,
    keyboardHeightPercent = keyboardHeightPercent,
    keyboardAtTop = keyboardAtTop,
    keyboardShowNumberRow = keyboardShowNumberRow,
    keyboardShowSystemKeys = keyboardShowSystemKeys,
    keyboardColor = keyboardColor,
)

fun ProfileSettings.toSettings(): Settings {
    val legacy = buttonBindings.mapNotNull { (key, value) ->
        runCatching { key.toInt() to MouseAction.valueOf(value) }.getOrNull()
    }.toMap()
    val migratedDetailed = if (detailedBindings.isBlank()) {
        legacy.map { (key, action) -> ButtonBinding(setOf(key), action) }
    } else BindingCodec.decode(detailedBindings)

    return Settings(
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
        buttonBindings = legacy,
        detailedBindings = migratedDetailed,
        audioPack = audioPack,
        cursorStyle = cursorStyle,
        cursorSize = cursorSize,
        cursorColor = cursorColor,
        autoHideTimeoutMs = autoHideTimeoutMs,
        keyboardWidthPercent = keyboardWidthPercent,
        keyboardHeightPercent = keyboardHeightPercent,
        keyboardAtTop = keyboardAtTop,
        keyboardShowNumberRow = keyboardShowNumberRow,
        keyboardShowSystemKeys = keyboardShowSystemKeys,
        keyboardColor = keyboardColor,
    )
}
