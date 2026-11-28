package com.masterchief.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.brain.BrainMode
import com.masterchief.agent.ui.components.DeviceReadouts
import com.masterchief.agent.ui.components.HudDivider
import com.masterchief.agent.ui.components.HudHeader
import com.masterchief.agent.ui.components.HudPanel
import com.masterchief.agent.ui.components.ScanlineOverlay
import com.masterchief.agent.ui.components.ShieldGauge
import com.masterchief.agent.ui.theme.NavyPanel
import com.masterchief.agent.ui.theme.SpartanGold

/**
 * Command screen — the HUD home screen, in the AI Studio style.
 * Shield gauge, real device readouts, hub grid, directives, quick orders.
 */
@Composable
fun CommandScreen(state: ChiefUiState, vm: ChiefViewModel, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        ScanlineOverlay()
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)
        ) {
            HudHeader(state.device, state.brainMode.name, state.lastBrainUsed)
            Spacer(Modifier.height(12.dp))
            HudDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShieldGauge(
                    batteryPct = state.device?.batteryPct ?: 0,
                    charging = state.device?.charging == true
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    // Which provider the current brain mode needs, and whether
                    // its key is set. If not, the button below goes straight
                    // to Settings > Brain — a real action, not a dead label.
                    val activeKeySet = when (state.brainMode) {
                        BrainMode.GEMINI -> state.hasGeminiKey
                        BrainMode.OPENROUTER -> state.hasOpenRouterKey
                        BrainMode.OPENAI -> state.hasOpenAiKey
                        BrainMode.CLAUDE -> state.hasClaudeKey
                        BrainMode.CUSTOM -> state.hasCustomKey
                        BrainMode.AUTO -> state.hasGeminiKey || state.hasOpenRouterKey ||
                            state.hasOpenAiKey || state.hasClaudeKey || state.hasCustomKey
                        BrainMode.LOCAL_ONLY -> true
                    }
                    val activeLabel = when (state.brainMode) {
                        BrainMode.GEMINI -> "Gemini"
                        BrainMode.OPENROUTER -> "OpenRouter"
                        BrainMode.OPENAI -> "OpenAI"
                        BrainMode.CLAUDE -> "Claude"
                        BrainMode.CUSTOM -> "Custom endpoint"
                        BrainMode.AUTO -> "Auto (first key found)"
                        BrainMode.LOCAL_ONLY -> "Local only"
                    }
                    Text(
                        "BRAIN: ${state.brainMode} — $activeLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpartanGold
                    )
                    Text(
                        if (state.brainMode == BrainMode.LOCAL_ONLY) "No API needed, sir."
                        else if (activeKeySet) "API key: SET — cloud brain available, sir."
                        else "API key: NOT SET — chat uses the local brain only.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!activeKeySet) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { onNavigate("settings") }) {
                            Text("ADD API KEY")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "LIVE NOTIFICATIONS: ${state.notifications.size} seen this session",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        if (state.permissions?.notificationListenerEnabled == true)
                            "Notification access: GRANTED"
                        else "Notification access: NOT GRANTED — enable in Settings",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DeviceReadouts(state.device)
            Spacer(Modifier.height(12.dp))
            HudPanel("HUBS") {
                val hubs = listOf(
                    "CHAT" to "chat", "APP ARMORY" to "armory",
                    "PHOTO ORGANIZER" to "photos", "SITREP" to "briefing",
                    "CLEANUP" to "cleanup", "SETTINGS" to "settings"
                )
                hubs.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, route) ->
                            Button(
                                onClick = { onNavigate(route) },
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                            ) { Text(label) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("QUICK ORDERS", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.runPhotoSweep(); onNavigate("photos") }) {
                    Text("SCAN PHOTOS")
                }
                OutlinedButton(onClick = { vm.sendCommand("plan my day") }) {
                    Text("PLAN MY DAY")
                }
                OutlinedButton(onClick = { vm.sendCommand("open WhatsApp") }) {
                    Text("WHATSAPP")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("DIRECTIVES", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
            if (state.missions.isEmpty()) {
                Text("No directives yet, sir. Add one below.", style = MaterialTheme.typography.bodyMedium)
            }
            state.missions.forEach { m ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPanel)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (m.done) "✓ ${m.title}" else "○ ${m.title}")
                        Button(onClick = { vm.toggleMission(m.id) }) {
                            Text(if (m.done) "Reopen" else "Done")
                        }
                    }
                }
            }
            var newMission by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newMission, onValueChange = { newMission = it },
                    label = { Text("New directive") }, modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    if (newMission.isNotBlank()) { vm.addMission(newMission); newMission = "" }
                }) { Text("ADD") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate("chat") }, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN STANDBY CHAT")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
