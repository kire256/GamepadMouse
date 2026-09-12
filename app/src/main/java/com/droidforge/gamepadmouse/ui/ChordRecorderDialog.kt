package com.droidforge.gamepadmouse.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.droidforge.gamepadmouse.service.GamepadMouseService

// Map of common keycodes to readable names
private val BUTTON_NAMES = mapOf(
    KeyEvent.KEYCODE_BUTTON_A to "A",
    KeyEvent.KEYCODE_BUTTON_B to "B",
    KeyEvent.KEYCODE_BUTTON_X to "X",
    KeyEvent.KEYCODE_BUTTON_Y to "Y",
    KeyEvent.KEYCODE_BUTTON_L1 to "LB",
    KeyEvent.KEYCODE_BUTTON_R1 to "RB",
    KeyEvent.KEYCODE_BUTTON_L2 to "LT",
    KeyEvent.KEYCODE_BUTTON_R2 to "RT",
    KeyEvent.KEYCODE_BUTTON_THUMBL to "L3",
    KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
    KeyEvent.KEYCODE_BUTTON_START to "Start",
    KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
    KeyEvent.KEYCODE_DPAD_UP to "D-Up",
    KeyEvent.KEYCODE_DPAD_DOWN to "D-Down",
    KeyEvent.KEYCODE_DPAD_LEFT to "D-Left",
    KeyEvent.KEYCODE_DPAD_RIGHT to "D-Right",
    KeyEvent.KEYCODE_DPAD_CENTER to "D-Center",
)

fun keyCodeToName(keyCode: Int): String = BUTTON_NAMES[keyCode] ?: "Button $keyCode"

@Composable
fun ChordRecorderDialog(
    currentChord: Set<Int>,
    onDismiss: () -> Unit,
    onChordRecorded: (Set<Int>) -> Unit,
) {
    val recordedChord by GamepadMouseService.recordedChord.collectAsState()

    DisposableEffect(Unit) {
        GamepadMouseService.startChordRecording()
        onDispose {
            GamepadMouseService.stopChordRecording()
        }
    }

    Dialog(
        onDismissRequest = {
            GamepadMouseService.stopChordRecording()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Record toggle chord",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Text(
                    "Hold 2 or more buttons together, then release to set your custom toggle chord.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Listening indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = recordedChord == null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Listening for gamepad input...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Show recorded chord
                recordedChord?.let { chord ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Recorded:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                chord.joinToString(" + ") { keyCodeToName(it) },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        GamepadMouseService.stopChordRecording()
                        onDismiss()
                    }) {
                        Text("Cancel")
                    }
                    
                    TextButton(
                        onClick = {
                            recordedChord?.let { chord ->
                                if (chord.size >= 2) {
                                    // Stop recording BEFORE closing dialog and saving
                                    // This prevents held chord buttons from triggering actions
                                    GamepadMouseService.stopChordRecording()
                                    onChordRecorded(chord)
                                }
                            }
                        },
                        enabled = recordedChord != null && (recordedChord?.size ?: 0) >= 2
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
