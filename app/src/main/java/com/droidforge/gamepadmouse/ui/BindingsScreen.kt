package com.droidforge.gamepadmouse.ui

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidforge.gamepadmouse.input.BindingMode
import com.droidforge.gamepadmouse.input.ButtonBinding
import com.droidforge.gamepadmouse.input.DefaultBindings
import com.droidforge.gamepadmouse.input.MouseAction
import com.droidforge.gamepadmouse.service.GamepadMouseService
import com.droidforge.gamepadmouse.settings.Settings
import com.droidforge.gamepadmouse.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ButtonSlot(val keyCode: Int, val name: String, val description: String = "")

val ASSIGNABLE_BUTTONS = listOf(
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_A, "A", "Face button"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_B, "B", "Face button"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_X, "X", "Face button"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_Y, "Y", "Face button"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_L1, "LB", "Left bumper"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_R1, "RB", "Right bumper"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_L2, "LT", "Left trigger"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_R2, "RT", "Right trigger"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3", "Left stick click"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3", "Right stick click"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_START, "Start", "Menu button"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_SELECT, "Select / Back", "Back button"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_UP, "D-pad Up"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_DOWN, "D-pad Down"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_LEFT, "D-pad Left"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_RIGHT, "D-pad Right"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_CENTER, "D-pad Center"),
)

@Composable
fun BindingsScreen(settings: Settings, repo: SettingsRepository, scope: kotlinx.coroutines.CoroutineScope) {
    var editing by remember { mutableStateOf<ButtonBinding?>(null) }
    var showReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Button assignments", style = MaterialTheme.typography.titleMedium)
        Text(
            "Bindings can use one button or a combination, run in mouse mode, gamepad mode, or both, and optionally require a hold.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        settings.detailedBindings.forEach { binding ->
            BindingRow(binding = binding, onClick = { editing = binding })
        }
        Button(onClick = { editing = ButtonBinding(emptySet(), MouseAction.NONE) }, modifier = Modifier.fillMaxWidth()) {
            Text("Add binding")
        }
        HorizontalDivider()
        OutlinedButton(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset to defaults")
        }
    }

    editing?.let { original ->
        BindingEditorDialog(
            original = original,
            onDismiss = { GamepadMouseService.stopChordRecording(); editing = null },
            onSave = { updated ->
                scope.launch {
                    val list = settings.detailedBindings.toMutableList()
                    val index = list.indexOf(original)
                    if (index >= 0) list[index] = updated else list += updated
                    repo.setDetailedBindings(list)
                }
                editing = null
            },
            onDelete = {
                scope.launch { repo.setDetailedBindings(settings.detailedBindings - original) }
                editing = null
            },
        )
    }

    if (showReset) AlertDialog(
        onDismissRequest = { showReset = false },
        title = { Text("Reset bindings?") },
        text = { Text("Restore mouse-mode default bindings and remove custom combinations?") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.setDetailedBindings(DefaultBindings.detailed) }; showReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun BindingRow(binding: ButtonBinding, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(binding.action.label, style = MaterialTheme.typography.titleSmall)
            Text(
                binding.keyCodes.sorted().joinToString(" + ") { keyCodeToName(it) }.ifBlank { "No buttons" },
                color = MaterialTheme.colorScheme.primary,
            )
            val modes = binding.modes.sortedBy { it.name }.joinToString(" + ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
            Text("$modes · ${if (binding.holdDurationMs == 0L) "Tap" else "Hold ${binding.holdDurationMs} ms"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BindingEditorDialog(
    original: ButtonBinding,
    onDismiss: () -> Unit,
    onSave: (ButtonBinding) -> Unit,
    onDelete: () -> Unit,
) {
    var keys by remember(original) { mutableStateOf(original.keyCodes) }
    var action by remember(original) { mutableStateOf(original.action) }
    var modes by remember(original) { mutableStateOf(original.modes.ifEmpty { setOf(BindingMode.MOUSE) }) }
    var hold by remember(original) { mutableStateOf(original.holdDurationMs.toFloat()) }
    var choosingAction by remember { mutableStateOf(false) }
    val recorded by GamepadMouseService.recordedChord.collectAsState()
    val isRecording by GamepadMouseService.recordingChord.collectAsState()

    if (recorded != null) keys = recorded!!

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original.keyCodes.isEmpty()) "Add binding" else "Edit binding") },
        text = {
            Column(Modifier.fillMaxWidth().height(480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Buttons", style = MaterialTheme.typography.titleSmall)
                Text(keys.sorted().joinToString(" + ") { keyCodeToName(it) }.ifBlank { "None recorded" })
                OutlinedButton(onClick = { GamepadMouseService.startChordRecording() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isRecording) "Listening…" else "Record button or combination")
                }
                if (isRecording) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Listening for controller input…", style = MaterialTheme.typography.titleSmall)
                            Text("Hold the desired button or combination, then release it.")
                        }
                    }
                } else {
                    Text("Tap Record, then press and release the controller buttons together.", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                Text("Action", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { choosingAction = true }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }

                HorizontalDivider()
                Text("Active modes", style = MaterialTheme.typography.titleSmall)
                ModeCheck("Mouse mode", BindingMode.MOUSE, modes) { modes = it }
                ModeCheck("Gamepad mode", BindingMode.GAMEPAD, modes) { modes = it }

                HorizontalDivider()
                Text("Hold delay: ${hold.roundToInt()} ms", style = MaterialTheme.typography.titleSmall)
                Slider(value = hold, onValueChange = { hold = it }, valueRange = 0f..2000f, steps = 19)
                Text("0 ms runs immediately. Otherwise all buttons must remain held for the selected delay.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = keys.isNotEmpty() && action != MouseAction.NONE && modes.isNotEmpty(),
                onClick = { onSave(ButtonBinding(keys, action, modes, hold.roundToInt().toLong())) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (original.keyCodes.isNotEmpty()) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (choosingAction) CategorizedActionPickerDialog(
        buttonName = "binding",
        currentAction = action,
        onDismiss = { choosingAction = false },
        onSelect = { action = it; choosingAction = false },
    )
}

@Composable
private fun ModeCheck(label: String, mode: BindingMode, modes: Set<BindingMode>, onChange: (Set<BindingMode>) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(if (mode in modes) modes - mode else modes + mode) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = mode in modes, onCheckedChange = { checked -> onChange(if (checked) modes + mode else modes - mode) })
        Text(label)
    }
}
