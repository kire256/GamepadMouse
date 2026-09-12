package com.droidforge.gamepadmouse.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
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
                    label = { Text("Settings") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.SportsEsports, contentDescription = null) },
                    label = { Text("Bindings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    label = { Text("Advanced") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
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
            2 -> BindingsTab(
                modifier = Modifier.padding(padding),
                settings = settings,
                repo = repo,
                scope = scope,
            )
            3 -> AdvancedTab(
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

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
    var showChordRecorder by remember { mutableStateOf(false) }
    
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
        
        FeatureCard(
            title = "Audio cue packs",
            description = "Replace beeps with custom sound effects. Choose from bundled packs or pick your own files.",
            available = false,
        )
        
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
