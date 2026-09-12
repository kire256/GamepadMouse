package com.droidforge.gamepadmouse.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark, high-contrast palette: the INMO Air 3 uses transparent micro-OLED waveguides,
 * where black is see-through and bright colours pop. Large tap/focus targets by default.
 */
private val Scheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF00223A),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121418),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1B1E24),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF262A32),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFEF9A9A),
)

@Composable
fun GamepadMouseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
