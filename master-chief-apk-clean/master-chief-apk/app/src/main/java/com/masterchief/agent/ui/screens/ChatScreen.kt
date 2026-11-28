package com.masterchief.agent.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.masterchief.agent.ui.theme.CortanaBlue
import com.masterchief.agent.ui.theme.NavyPanel
import com.masterchief.agent.ui.theme.SpartanGold

/**
 * Standby chat box — the AI Studio command console, rebuilt natively.
 * COMMANDER / CORTANA bubbles, quick-directive chips, text + voice input.
 * If Gemini is slow or down, Chief says so within 30 seconds — never hangs
 * silently. If no API key is set, the banner says so and chat uses the
 * local brain only.
 */
@Composable
fun ChatScreen(state: ChiefUiState, vm: ChiefViewModel, onNavigate: (String) -> Unit) {
    // Voice input via Android's speech recogniser — real, on-device.
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) vm.sendCommand(spoken)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("CORTANA // COMMAND LINK", style = MaterialTheme.typography.titleMedium, color = CortanaBlue)
        Text(
            "Brain: ${state.brainMode}  •  Last used: ${state.lastBrainUsed}",
            style = MaterialTheme.typography.labelMedium
        )
        // Which key the current brain mode needs. If it is missing, the
        // banner says so and its button goes straight to Settings > Brain.
        val needsKey = when (state.brainMode) {
            BrainMode.GEMINI -> !state.hasGeminiKey
            BrainMode.OPENROUTER -> !state.hasOpenRouterKey
            BrainMode.OPENAI -> !state.hasOpenAiKey
            BrainMode.CLAUDE -> !state.hasClaudeKey
            BrainMode.CUSTOM -> !state.hasCustomKey
            BrainMode.AUTO -> !(state.hasGeminiKey || state.hasOpenRouterKey ||
                state.hasOpenAiKey || state.hasClaudeKey || state.hasCustomKey)
            BrainMode.LOCAL_ONLY -> false
        }
        if (needsKey) {
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NavyPanel)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        if (state.brainMode == BrainMode.AUTO)
                            "NO API KEY SET — chat uses the local brain only (time, alarms, " +
                                "apps, simple orders). Add a key in Settings > Brain for " +
                                "heavier thinking."
                        else
                            "NO ${state.brainMode} API KEY — chat uses the local brain only " +
                                "(time, alarms, apps, simple orders). Add it in Settings > " +
                                "Brain for heavier thinking.",
                        color = SpartanGold
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onNavigate("settings") }) {
                        Text("OPEN BRAIN SETTINGS")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Quick-directive chips, like the AI Studio preview.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { vm.runPhotoSweep() }) { Text("SCAN PHOTOS") }
            OutlinedButton(onClick = { vm.sendCommand("set an alarm for 07:30") }) { Text("ALARM 07:30") }
            OutlinedButton(onClick = { vm.sendCommand("plan my day") }) { Text("PLAN MY DAY") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(state.chat.reversed()) { m ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (m.fromChief) NavyPanel
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (m.fromChief) "CORTANA" else "COMMANDER",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (m.fromChief) CortanaBlue else SpartanGold
                        )
                        Text(m.text)
                    }
                }
            }
        }
        if (state.busy) {
            Text(
                "Chief is working… if Gemini takes over 30 seconds, Chief will say so.",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        var input by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Give an order, sir…")
                }
                runCatching { voiceLauncher.launch(intent) }
            }) {
                Icon(Icons.Filled.Mic, contentDescription = "Tap to talk")
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("Give an order, sir…") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { vm.sendCommand(input); input = "" },
                enabled = !state.busy
            ) { Text("SEND") }
        }
    }
}
