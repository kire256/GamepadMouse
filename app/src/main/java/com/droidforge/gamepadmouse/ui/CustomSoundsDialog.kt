package com.droidforge.gamepadmouse.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Dialog for selecting custom sound files for each audio cue.
 * Allows user to pick audio files from device storage.
 */
@Composable
fun CustomSoundsDialog(
    onDismiss: () -> Unit,
    onSoundsSelected: (Map<String, Uri>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Store selected file URIs
    var tapUri by remember { mutableStateOf<Uri?>(null) }
    var longPressUri by remember { mutableStateOf<Uri?>(null) }
    var toggleUri by remember { mutableStateOf<Uri?>(null) }
    
    // File picker launchers for each sound type
    val tapPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            // Take persistent permission
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            tapUri = it
        }
    }
    
    val longPressPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            longPressUri = it
        }
    }
    
    val togglePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            toggleUri = it
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Choose Custom Sounds",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Text(
                    "Select audio files (MP3, WAV, OGG) for each action:",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Divider()
                
                // Tap sound
                SoundFilePicker(
                    label = "Tap / Click",
                    selectedUri = tapUri,
                    onPickFile = { tapPicker.launch("audio/*") }
                )
                
                // Long press sound
                SoundFilePicker(
                    label = "Long Press",
                    selectedUri = longPressUri,
                    onPickFile = { longPressPicker.launch("audio/*") }
                )
                
                // Toggle sound
                SoundFilePicker(
                    label = "Mode Toggle",
                    selectedUri = toggleUri,
                    onPickFile = { togglePicker.launch("audio/*") }
                )
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val sounds = mutableMapOf<String, Uri>()
                            tapUri?.let { sounds["tap"] = it }
                            longPressUri?.let { sounds["long_press"] = it }
                            toggleUri?.let { sounds["toggle"] = it }
                            
                            if (sounds.isNotEmpty()) {
                                onSoundsSelected(sounds)
                            }
                            onDismiss()
                        },
                        enabled = tapUri != null || longPressUri != null || toggleUri != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundFilePicker(
    label: String,
    selectedUri: Uri?,
    onPickFile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (selectedUri != null) {
                Text(
                    selectedUri.lastPathSegment ?: "File selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "No file selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = onPickFile) {
            Text("Choose")
        }
    }
}
