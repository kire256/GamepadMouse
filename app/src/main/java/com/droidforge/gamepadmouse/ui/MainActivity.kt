package com.droidforge.gamepadmouse.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.droidforge.gamepadmouse.settings.SettingsRepository

class MainActivity : ComponentActivity() {

    private lateinit var repo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = SettingsRepository(applicationContext)
        setContent {
            GamepadMouseTheme {
                MainScreen(
                    repo = repo,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
