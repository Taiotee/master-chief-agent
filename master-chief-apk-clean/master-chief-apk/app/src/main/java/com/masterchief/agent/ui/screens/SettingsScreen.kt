package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/** Settings — brain switcher, Gemini key, voice, bubble, notification access. */
@Composable
fun SettingsScreen(state: ChiefUiState, vm: ChiefViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("SETTINGS — BRAIN & PERMISSIONS", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("BRAIN MODE (current: ${state.brainMode})")
        BrainMode.entries.forEach { mode ->
            Button(onClick = { vm.setBrainMode(mode) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(mode.name)
            }
        }
        Spacer(Modifier.height(12.dp))
        var key by remember { mutableStateOf("") }
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Gemini API key (free, from Google AI Studio)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.setGeminiKey(key); key = "" }) { Text("SAVE KEY") }
        Text("The key is stored on this phone only. Never share it, never commit it.",
            style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.openNotificationAccessSettings() }) {
            Text("ENABLE NOTIFICATION ACCESS")
        }
        Text("Needed for Chief to read and filter notifications. " +
            "Android makes you turn this on in system settings — no app can do it silently.",
            style = MaterialTheme.typography.labelMedium)
    }
}
