package com.droidforge.gamepadmouse.ui

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.droidforge.gamepadmouse.input.MouseAction

// Icon mapping for actions
fun getActionIcon(action: MouseAction): ImageVector = when (action) {
    MouseAction.NONE -> Icons.Filled.Menu
    MouseAction.TAP -> Icons.Filled.TouchApp
    MouseAction.LONG_PRESS -> Icons.Filled.TouchApp
    MouseAction.BACK -> Icons.Filled.KeyboardArrowLeft
    MouseAction.HOME -> Icons.Filled.Home
    MouseAction.RECENTS -> Icons.Filled.Menu
    MouseAction.SLOW -> Icons.Filled.Speed
    MouseAction.FAST -> Icons.Filled.Speed
    MouseAction.TOGGLE_MODE -> Icons.Filled.Settings
    MouseAction.TOGGLE_KEYBOARD -> Icons.Filled.TouchApp
    MouseAction.SNAP_TARGET -> Icons.Filled.Check
    MouseAction.SCREENSHOT -> Icons.Filled.Photo
    MouseAction.NOTIFICATIONS -> Icons.Filled.Notifications
    MouseAction.QUICK_SETTINGS -> Icons.Filled.Settings
    MouseAction.APP_PICKER -> Icons.Filled.Menu
    MouseAction.POWER_MENU -> Icons.Filled.Settings
    MouseAction.MEDIA_PLAY_PAUSE -> Icons.Filled.PlayArrow
    MouseAction.MEDIA_NEXT -> Icons.Filled.SkipNext
    MouseAction.MEDIA_PREVIOUS -> Icons.Filled.SkipPrevious
    MouseAction.VOLUME_UP -> Icons.Filled.VolumeUp
    MouseAction.VOLUME_DOWN -> Icons.Filled.VolumeDown
    MouseAction.VOLUME_MUTE -> Icons.Filled.VolumeMute
}

// Icon mapping for buttons - simple text glyphs
fun getButtonIcon(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> "[A]"
    KeyEvent.KEYCODE_BUTTON_B -> "[B]"
    KeyEvent.KEYCODE_BUTTON_X -> "[X]"
    KeyEvent.KEYCODE_BUTTON_Y -> "[Y]"
    KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
    KeyEvent.KEYCODE_BUTTON_R1 -> "RB"
    KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
    KeyEvent.KEYCODE_BUTTON_R2 -> "RT"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    KeyEvent.KEYCODE_BUTTON_START -> "≡"   // Menu icon
    KeyEvent.KEYCODE_BUTTON_SELECT -> "⊙"  // Circle with dot
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    KeyEvent.KEYCODE_DPAD_CENTER -> "●"
    else -> "?"
}

@Composable
fun CategorizedActionPickerDialog(
    buttonName: String,
    currentAction: MouseAction,
    onDismiss: () -> Unit,
    onSelect: (MouseAction) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val generalActions = listOf(
        MouseAction.NONE,
        MouseAction.TAP,
        MouseAction.LONG_PRESS,
        MouseAction.BACK,
        MouseAction.HOME,
        MouseAction.RECENTS,
        MouseAction.SLOW,
        MouseAction.FAST,
        MouseAction.TOGGLE_MODE,
        MouseAction.TOGGLE_KEYBOARD,
    )
    
    val systemActions = listOf(
        MouseAction.SCREENSHOT,
        MouseAction.NOTIFICATIONS,
        MouseAction.QUICK_SETTINGS,
        MouseAction.APP_PICKER,
        MouseAction.POWER_MENU,
    )
    
    val mediaActions = listOf(
        MouseAction.MEDIA_PLAY_PAUSE,
        MouseAction.MEDIA_NEXT,
        MouseAction.MEDIA_PREVIOUS,
        MouseAction.VOLUME_UP,
        MouseAction.VOLUME_DOWN,
        MouseAction.VOLUME_MUTE,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign $buttonName") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("General") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("System") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Media") }
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val actionsToShow = when (selectedTab) {
                        0 -> generalActions
                        1 -> systemActions
                        2 -> mediaActions
                        else -> generalActions
                    }
                    
                    actionsToShow.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(action) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    getActionIcon(action),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    action.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
