package com.droidforge.gamepadmouse.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.droidforge.gamepadmouse.R
import com.droidforge.gamepadmouse.input.DefaultBindings
import com.droidforge.gamepadmouse.input.MouseAction
import com.droidforge.gamepadmouse.settings.Settings
import com.droidforge.gamepadmouse.settings.SettingsRepository
import kotlinx.coroutines.launch

data class ButtonSlot(
    val keyCode: Int,
    val name: String,
    val description: String = "",
)

// Common gamepad buttons available for assignment
val ASSIGNABLE_BUTTONS = listOf(
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_A, "A", "Face button (right)"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_B, "B", "Face button (bottom)"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_X, "X", "Face button (top)"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_Y, "Y", "Face button (left)"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_L1, "LB", "Left bumper"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_R1, "RB", "Right bumper"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_L2, "LT", "Left trigger"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_R2, "RT", "Right trigger"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3", "Left stick click"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3", "Right stick click"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_START, "Start", "Menu button"),
    ButtonSlot(KeyEvent.KEYCODE_BUTTON_SELECT, "Select / Back", "Back button"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_UP, "D-pad Up", "Directional pad"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_DOWN, "D-pad Down", "Directional pad"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_LEFT, "D-pad Left", "Directional pad"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_RIGHT, "D-pad Right", "Directional pad"),
    ButtonSlot(KeyEvent.KEYCODE_DPAD_CENTER, "D-pad Center", "Directional pad"),
)

@Composable
fun BindingsScreen(
    settings: Settings,
    repo: SettingsRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var listeningForButton by remember { mutableStateOf<Int?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Button assignments", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap any button to assign an action. You can also press a physical gamepad button while this screen is open to quick-assign it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Show all assignable buttons
        ASSIGNABLE_BUTTONS.forEach { slot ->
            val currentAction = settings.buttonBindings[slot.keyCode] ?: MouseAction.NONE
            
            ButtonAssignmentRow(
                button = slot,
                action = currentAction,
                isListening = listeningForButton == slot.keyCode,
                onClick = {
                    showActionPicker = slot.keyCode
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset to defaults")
        }

        Spacer(Modifier.height(24.dp))
    }

    // Action picker dialog
    showActionPicker?.let { keyCode ->
        val slot = ASSIGNABLE_BUTTONS.find { it.keyCode == keyCode }
        CategorizedActionPickerDialog(
            buttonName = slot?.name ?: "Button",
            currentAction = settings.buttonBindings[keyCode] ?: MouseAction.NONE,
            onDismiss = { showActionPicker = null },
            onSelect = { action ->
                scope.launch {
                    val updated = settings.buttonBindings.toMutableMap()
                    if (action == MouseAction.NONE) {
                        updated.remove(keyCode)
                    } else {
                        updated[keyCode] = action
                    }
                    repo.setBindings(updated)
                }
                showActionPicker = null
            }
        )
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to defaults?") },
            text = { Text("This will restore all button assignments to their original defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.setBindings(DefaultBindings.buttons) }
                    showResetDialog = false
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ButtonAssignmentRow(
    button: ButtonSlot,
    action: MouseAction,
    isListening: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isListening) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Button glyph
                Text(
                    getButtonIcon(button.keyCode),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(button.name, style = MaterialTheme.typography.titleSmall)
                    if (button.description.isNotEmpty()) {
                        Text(
                            button.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Listening...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        if (action != MouseAction.NONE) {
                            Icon(
                                getActionIcon(action),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            action.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (action == MouseAction.NONE)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPickerDialog(
    buttonName: String,
    currentAction: MouseAction,
    onDismiss: () -> Unit,
    onSelect: (MouseAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign $buttonName") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)  // Fixed height to enable scrolling
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MouseAction.entries.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (action == currentAction) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
