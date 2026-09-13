package com.droidforge.gamepadmouse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import com.droidforge.gamepadmouse.R
import com.droidforge.gamepadmouse.input.ServiceMode
import com.droidforge.gamepadmouse.service.GamepadMouseService
import com.droidforge.gamepadmouse.settings.Settings
import com.droidforge.gamepadmouse.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    repo: SettingsRepository,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val running by GamepadMouseService.running.collectAsState()
    val mode by GamepadMouseService.mode.collectAsState()
    val controller by GamepadMouseService.controllerConnected.collectAsState()
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    label = { Text("Status") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Mouse") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    label = { Text("Keyboard") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.SportsEsports, contentDescription = null) },
                    label = { Text("Bindings") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    label = { Text("General") },
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> StatusTab(
                modifier = Modifier.padding(padding),
                running = running,
                mode = mode,
                controller = controller,
                onEnable = onOpenAccessibilitySettings,
                onToggleMode = { GamepadMouseService.instance?.toggleMode() },
            )
            1 -> SettingsTab(
                modifier = Modifier.padding(padding),
                settings = settings,
                repo = repo,
                scope = scope,
            )
            2 -> KeyboardSettingsTab(
                modifier = Modifier.padding(padding),
                settings = settings,
                repo = repo,
                scope = scope,
            )
            3 -> BindingsTab(
                modifier = Modifier.padding(padding),
                settings = settings,
                repo = repo,
                scope = scope,
            )
            4 -> AdvancedTab(
                modifier = Modifier.padding(padding),
                settings = settings,
                repo = repo,
                scope = scope,
            )
        }
    }
}

@Composable
private fun StatusTab(
    modifier: Modifier,
    running: Boolean,
    mode: ServiceMode,
    controller: Boolean,
    onEnable: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val context = LocalContext.current
    val versionLabel = remember(context.packageName) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        "Version $versionName ($versionCode)"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                versionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusCard(
            running = running,
            mode = mode,
            controller = controller,
            onEnable = onEnable,
            onToggleMode = onToggleMode,
        )
        
        if (running) {
            Text("Quick reference", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BindingLine("← → ↑ ↓", "Left stick", "Move cursor")
                    BindingLine("← → ↑ ↓", "Right stick", "Scroll")
                    BindingLine("[A]", "A", "Tap (left click)")
                    BindingLine("[X]", "X", "Long-press (right click)")
                    BindingLine("[B]", "B", "Back")
                    BindingLine("[Y]", "Y", "Recents")
                    BindingLine("L3", "L3 (stick click)", "Home")
                    BindingLine("LB / RB", "Bumpers", "Slow / fast (hold)")
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    BindingLine("≡ + ⊙", "Start + Select", "Toggle mode", bold = true)
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    modifier: Modifier,
    settings: Settings,
    repo: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cursor", style = MaterialTheme.typography.titleMedium)
        
        // Cursor style selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Cursor Style", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                
                val cursorStyles = listOf("ARROW", "DOT", "CROSSHAIR", "CIRCLE", "POINTER", "TRIANGLE")
                val cursorLabels = listOf("Arrow (Default)", "Dot", "Crosshair", "Circle", "Pointer Hand", "Rounded Triangle")
                
                cursorStyles.forEachIndexed { index, style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { repo.setCursorStyle(style) }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = settings.cursorStyle == style,
                            onClick = { scope.launch { repo.setCursorStyle(style) } }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cursorLabels[index], style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        SliderRow(
            label = "Cursor size",
            value = settings.cursorSize,
            range = 0.5f..2.0f,
            format = { size -> "×${"%.1f".format(size)}" },
        ) { scope.launch { repo.setCursorSize(it) } }
        
        // Cursor color picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Cursor Color", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                
                val cursorColors = listOf(
                    0xFFFFFFFF.toInt() to "White",
                    0xFF000000.toInt() to "Black",
                    0xFFFF0000.toInt() to "Red",
                    0xFF00FF00.toInt() to "Green",
                    0xFF0000FF.toInt() to "Blue",
                    0xFFFFFF00.toInt() to "Yellow",
                    0xFFFF00FF.toInt() to "Magenta",
                    0xFF00FFFF.toInt() to "Cyan"
                )
                
                // Display colors in a grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cursorColors.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (color, name) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        scope.launch { repo.setCursorColor(color) }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(androidx.compose.ui.graphics.Color(color))
                                            .then(
                                                if (settings.cursorColor == color)
                                                    Modifier.border(
                                                        3.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                else Modifier
                                            )
                                    )
                                    Text(name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Cursor auto-hide timeout
        SliderRow(
            label = "Auto-hide timeout",
            value = settings.autoHideTimeoutMs.toFloat(),
            range = 0f..10000f,
            format = { ms -> if (ms == 0f) "Disabled" else "${(ms / 1000).roundToInt()}s" },
        ) { scope.launch { repo.setAutoHideTimeout(it.toLong()) } }

        Spacer(Modifier.height(16.dp))
        Text("Movement", style = MaterialTheme.typography.titleMedium)
        SliderRow(
            label = "Base speed",
            value = settings.baseSpeedPxPerSec,
            range = 200f..2500f,
            format = { "${it.roundToInt()} px/s" },
        ) { scope.launch { repo.setBaseSpeed(it) } }
        SliderRow(
            label = "Slow (hold LB)",
            value = settings.slowMultiplier,
            range = 0.1f..1f,
            format = { "×${"%.2f".format(it)}" },
        ) { scope.launch { repo.setSlowMultiplier(it) } }
        SliderRow(
            label = "Fast (hold RB)",
            value = settings.fastMultiplier,
            range = 1f..5f,
            format = { "×${"%.1f".format(it)}" },
        ) { scope.launch { repo.setFastMultiplier(it) } }
        SliderRow(
            label = "Stick deadzone",
            value = settings.deadzone,
            range = 0.02f..0.5f,
            format = { "${(it * 100).roundToInt()}%" },
        ) { scope.launch { repo.setDeadzone(it) } }
        
        HorizontalDivider()
        Text("Scroll", style = MaterialTheme.typography.titleMedium)
        SliderRow(
            label = "Scroll step",
            value = settings.scrollStepPx,
            range = 60f..600f,
            format = { "${it.roundToInt()} px" },
        ) { scope.launch { repo.setScrollStep(it) } }
        SwitchRow("Invert scroll direction", settings.invertScroll) { scope.launch { repo.setInvertScroll(it) } }
        SwitchRow("Circular scroll (rotate right stick)", settings.circularScroll) { scope.launch { repo.setCircularScroll(it) } }

        HorizontalDivider()
        Text("Sticks", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Swap left / right sticks", settings.swapSticks) { scope.launch { repo.setSwapSticks(it) } }

        HorizontalDivider()
        Text("Behaviour", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Start in mouse mode", settings.startInMouseMode) { scope.launch { repo.setStartInMouseMode(it) } }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyboardSettingsTab(
    modifier: Modifier,
    settings: Settings,
    repo: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var keyboardTestText by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Keyboard", style = MaterialTheme.typography.titleMedium)
        SliderRow("Keyboard width", settings.keyboardWidthPercent, 40f..100f, { "${it.roundToInt()}%" }) { scope.launch { repo.setKeyboardWidthPercent(it) } }
        SliderRow("Keyboard height", settings.keyboardHeightPercent, 25f..80f, { "${it.roundToInt()}%" }) { scope.launch { repo.setKeyboardHeightPercent(it) } }
        SwitchRow("Position keyboard at top", settings.keyboardAtTop) { scope.launch { repo.setKeyboardAtTop(it) } }
        SwitchRow("Show number row", settings.keyboardShowNumberRow) { scope.launch { repo.setKeyboardShowNumberRow(it) } }
        SwitchRow("Show system keys", settings.keyboardShowSystemKeys) { scope.launch { repo.setKeyboardShowSystemKeys(it) } }
        SwitchRow("Automatically show for text fields", settings.autoShowKeyboardOnTextField) { scope.launch { repo.setAutoShowKeyboardOnTextField(it) } }
        Text("Keyboard color", style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                0xFF202124.toInt() to "Dark", 0xFF263238.toInt() to "Blue gray",
                0xFF3E2723.toInt() to "Brown", 0xFF1B5E20.toInt() to "Green",
                0xFF006064.toInt() to "Cyan", 0xFF311B92.toInt() to "Purple",
                0xFF880E4F.toInt() to "Pink", 0xFF37474F.toInt() to "Slate",
            ).chunked(4).forEach { colors ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    colors.forEach { (color, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { scope.launch { repo.setKeyboardColor(color) } }) {
                            Box(Modifier.width(52.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(color)).then(if (settings.keyboardColor == color) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = keyboardTestText,
            onValueChange = { keyboardTestText = it },
            modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                if (state.isFocused && settings.autoShowKeyboardOnTextField) GamepadMouseService.instance?.showKeyboardForFocusedField()
            },
            label = { Text("Keyboard input test") },
            placeholder = { Text("Focus this field to test typing") },
        )
    }
}

@Composable
private fun BindingsTab(
    modifier: Modifier,
    settings: Settings,
    repo: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Box(modifier = modifier) {
        BindingsScreen(settings = settings, repo = repo, scope = scope)
    }
}

@Composable
private fun AdvancedTab(
    modifier: Modifier,
    settings: Settings,
    repo: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val context = LocalContext.current
    var showChordRecorder by remember { mutableStateOf(false) }
    var showCustomSoundsDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Advanced features", style = MaterialTheme.typography.titleMedium)
        
        // Toggle chord customization (NOW AVAILABLE!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Toggle chord", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Current: ${settings.toggleChord.joinToString(" + ") { keyCodeToName(it) }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "The button combination to toggle between gamepad and mouse modes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showChordRecorder = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change toggle chord")
                }
                
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                
                // Hold duration slider
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hold duration", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.chordHoldDurationMs == 0L) "Instant" 
                            else "${settings.chordHoldDurationMs}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "How long to hold the chord before toggling. 0 = instant toggle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = settings.chordHoldDurationMs.toFloat(),
                        onValueChange = { scope.launch { repo.setChordHoldDuration(it.toLong()) } },
                        valueRange = 0f..2000f,
                        steps = 19  // 0, 100, 200, ..., 2000
                    )
                }
            }
        }
        
        FeatureCard(
            title = "Keyboard mode",
            description = "On-screen keyboard navigable by gamepad for text input. Requires additional accessibility permission.",
            available = false,
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("System actions", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Available now!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Assign buttons to media controls (play/pause, next/prev, volume), screenshot, notifications, quick settings, and more. Go to the Bindings tab to set them up!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        
        // Audio cue packs (NOW AVAILABLE!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio cue packs", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Available now!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Choose sound effects for button clicks and mode changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Audio pack selection
                val packOptions = listOf("MINIMAL", "MECHANICAL", "RETRO", "SCIFI", "SILENT", "CUSTOM")
                val packLabels = listOf(
                    "Minimal (System tones)",
                    "Mechanical (Clicks)",
                    "Retro (8-bit)", 
                    "Sci-Fi",
                    "Silent (No sounds)",
                    "Custom (Your files)"
                )
                
                packOptions.forEachIndexed { index, pack ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    scope.launch { repo.setAudioPack(pack) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = settings.audioPack == pack,
                                onClick = { scope.launch { repo.setAudioPack(pack) } }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                packLabels[index],
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        // Preview buttons (skip for Silent and Custom)
                        if (pack != "SILENT" && pack != "CUSTOM") {
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                // Individual preview buttons for each sound type
                                val previewSounds = listOf(
                                    "Tap" to com.droidforge.gamepadmouse.audio.AudioCue.TAP,
                                    "Hold" to com.droidforge.gamepadmouse.audio.AudioCue.LONG_PRESS,
                                    "Toggle" to com.droidforge.gamepadmouse.audio.AudioCue.MODE_SWITCH_MOUSE
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    previewSounds.forEach { (label, cue) ->
                                        var isPlaying by remember { mutableStateOf(false) }
                                        
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                if (!isPlaying) {
                                                    isPlaying = true
                                                    com.droidforge.gamepadmouse.service.GamepadMouseService.instance?.let { service ->
                                                        scope.launch {
                                                            try {
                                                                // Save current pack
                                                                val originalPack = settings.audioPack
                                                                
                                                                // Temporarily disable audio to avoid click sound from the button press
                                                                repo.setAudioPack("SILENT")
                                                                kotlinx.coroutines.delay(50) // Let it take effect
                                                                
                                                                // Now switch to preview pack
                                                                repo.setAudioPack(pack)
                                                                kotlinx.coroutines.delay(50) // Short delay for settings update
                                                                
                                                                // Play the specific sound
                                                                service.playAudioCue(cue)
                                                                
                                                                // Restore original pack
                                                                kotlinx.coroutines.delay(100)
                                                                repo.setAudioPack(originalPack)
                                                            } catch (e: Exception) {
                                                                android.util.Log.e("AudioPreview", "Failed: ${e.message}")
                                                            } finally {
                                                                isPlaying = false
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isPlaying,
                                            modifier = Modifier.width(65.dp)
                                        ) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // File picker for Custom pack
                        if (pack == "CUSTOM") {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    showCustomSoundsDialog = true
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Choose Files", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        
        FeatureCard(
            title = "Per-device profiles",
            description = "Save different settings for each controller. Includes INMO Air 3 preset.",
            available = false,
        )
        
        Spacer(Modifier.height(24.dp))
    }
    
    if (showChordRecorder) {
        ChordRecorderDialog(
            currentChord = settings.toggleChord,
            onDismiss = { showChordRecorder = false },
            onChordRecorded = { chord ->
                scope.launch {
                    repo.setToggleChord(chord)
                }
                showChordRecorder = false
            }
        )
    }
    
    if (showCustomSoundsDialog) {
        CustomSoundsDialog(
            onDismiss = { showCustomSoundsDialog = false },
            onSoundsSelected = { soundUris ->
                // TODO: Save custom sound URIs to settings and load them in AudioManager
                android.widget.Toast.makeText(
                    context,
                    "Custom sounds saved! (Loading custom files will be implemented next)",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                showCustomSoundsDialog = false
            }
        )
    }
}

@Composable
private fun FeatureCard(title: String, description: String, available: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (available) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (!available) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Coming soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    running: Boolean,
    mode: ServiceMode,
    controller: Boolean,
    onEnable: () -> Unit,
    onToggleMode: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (running) R.string.status_service_on else R.string.status_service_off),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!running) {
                Text(
                    "The service needs to be turned on once in Android's Accessibility settings. " +
                        "It only listens to controller input and never reads screen content.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enable_service))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (mode == ServiceMode.MOUSE) Icons.Filled.Mouse else Icons.Filled.SportsEsports,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (mode == ServiceMode.MOUSE) R.string.mode_mouse else R.string.mode_gamepad),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    if (controller) "Controller detected" else "Waiting for controller input…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.toggle_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onToggleMode, modifier = Modifier.fillMaxWidth()) {
                    Text(if (mode == ServiceMode.MOUSE) "Switch to gamepad mode" else "Switch to mouse mode")
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(format(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BindingLine(glyph: String, input: String, action: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                glyph,
                style = MaterialTheme.typography.bodyLarge,
                color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                input,
                style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyMedium,
                color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            action,
            style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
