package org.dumbtv.launcher.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

data class SettingsDestination(
    val title: String,
    val detail: String,
    val action: String,
)

object SettingsDestinations {
    val all: List<SettingsDestination> = listOf(
        SettingsDestination(
            title = "All settings",
            detail = "Picture, sound, network, and the rest of this TV",
            action = Settings.ACTION_SETTINGS,
        ),
        SettingsDestination(
            title = "Network",
            detail = "Wi-Fi and ethernet",
            action = Settings.ACTION_WIFI_SETTINGS,
        ),
        SettingsDestination(
            title = "Apps",
            detail = "Open, stop, or remove an app",
            action = Settings.ACTION_APPLICATION_SETTINGS,
        ),
        SettingsDestination(
            title = "Picture",
            detail = "Display and screen",
            action = Settings.ACTION_DISPLAY_SETTINGS,
        ),
        SettingsDestination(
            title = "Sound",
            detail = "Volume and audio output",
            action = Settings.ACTION_SOUND_SETTINGS,
        ),
        SettingsDestination(
            title = "Date and time",
            detail = "Clock, zone, and format",
            action = Settings.ACTION_DATE_SETTINGS,
        ),
        SettingsDestination(
            title = "About",
            detail = "Model, version, and status",
            action = Settings.ACTION_DEVICE_INFO_SETTINGS,
        ),
        SettingsDestination(
            title = "Accessibility",
            detail = "Captions, audio, and controls",
            action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
        ),
        SettingsDestination(
            title = "Home screen",
            detail = "Choose what opens when you press Home",
            action = Settings.ACTION_HOME_SETTINGS,
        ),
        SettingsDestination(
            title = "Captions",
            detail = "Subtitle display",
            action = Settings.ACTION_CAPTIONING_SETTINGS,
        ),
        SettingsDestination(
            title = "Storage",
            detail = "Space used on this TV",
            action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        ),
    )

    fun open(context: Context, destination: SettingsDestination) {
        val intent = Intent(destination.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val canOpen = intent.resolveActivity(context.packageManager) != null
        val launch = if (canOpen) {
            intent
        } else {
            Toast.makeText(
                context,
                "That screen isn't on this TV. Opening settings.",
                Toast.LENGTH_SHORT,
            ).show()
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(launch) }
            .onFailure {
                Toast.makeText(context, "Settings didn't open.", Toast.LENGTH_SHORT).show()
            }
    }
}
