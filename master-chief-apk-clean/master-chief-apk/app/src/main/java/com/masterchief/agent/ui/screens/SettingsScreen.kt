package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.brain.BrainMode
import com.masterchief.agent.ui.components.HudPanel
import com.masterchief.agent.ui.theme.HaloGreen
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * Settings — brain switcher, Gemini key, voice, and the permissions screen.
 * Every permission shows its REAL state (granted / not granted). Android
 * makes the user grant these in system settings — no app can do it silently,
 * and Chief never pretends a feature works without its permission.
 */
@Composable
fun SettingsScreen(state: ChiefUiState, vm: ChiefViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text("SETTINGS — BRAIN & PERMISSIONS", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
        Spacer(Modifier.height(12.dp))

        HudPanel("BRAIN") {
            Text("Mode (current: ${state.brainMode})", style = MaterialTheme.typography.bodyMedium)
            BrainMode.entries.forEach { mode ->
                Button(
                    onClick = { vm.setBrainMode(mode) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) { Text(mode.name) }
            }
            Spacer(Modifier.height(8.dp))
            var key by remember { mutableStateOf("") }
            Text(
                "Gemini key: ${if (state.hasGeminiKey) "SET" else "NOT SET"}",
                color = if (state.hasGeminiKey) HaloGreen else SpartanGold
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("Gemini API key (free, from Google AI Studio)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.setGeminiKey(key); key = "" }) { Text("SAVE KEY") }
            Text(
                "The key is stored on this phone only. Never share it, never commit it. " +
                    "Without a key, chat uses the local brain only.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("VOICE") {
            Button(
                onClick = { vm.setVoiceEnabled(!state.voiceEnabled) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.voiceEnabled) "VOICE: ON — TAP TO MUTE" else "VOICE: OFF — TAP TO ENABLE")
            }
            Text(
                "Chief speaks with Android text-to-speech. The voice itself is " +
                    "chosen in system TTS settings.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("PERMISSIONS — REAL STATUS") {
            val p = state.permissions
            fun statusRow(label: String, granted: Boolean?, onOpen: () -> Unit, openLabel: String) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (granted) {
                                true -> "GRANTED"
                                false -> "NOT GRANTED"
                                null -> "CHECKING…"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (granted == true) HaloGreen else SpartanGold
                        )
                    }
                    Button(onClick = onOpen) { Text(openLabel) }
                }
            }
            statusRow(
                "Photos / media — duplicate scan",
                p?.photosGranted, { vm.openAppSettings() }, "OPEN"
            )
            statusRow(
                "Notifications — post briefing alerts",
                p?.notificationsGranted, { vm.openAppSettings() }, "OPEN"
            )
            statusRow(
                "Notification access — read live notifications",
                p?.notificationListenerEnabled, { vm.openNotificationAccessSettings() }, "ENABLE"
            )
            statusRow(
                "Display over other apps — standby bubble",
                p?.overlayGranted, { vm.openOverlaySettings() }, "ENABLE"
            )
            statusRow(
                "Usage access — unused-app detection",
                p?.usageAccessGranted, { vm.openUsageAccessSettings() }, "ENABLE"
            )
            statusRow(
                "Location — weather for where you are",
                p?.locationGranted, { vm.openAppSettings() }, "OPEN"
            )
            Text(
                "Tap OPEN / ENABLE to go to the system screen and grant it there. " +
                    "Chief cannot grant these for you.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.refreshDevice() }, modifier = Modifier.fillMaxWidth()) {
                Text("REFRESH PERMISSION STATUS")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
