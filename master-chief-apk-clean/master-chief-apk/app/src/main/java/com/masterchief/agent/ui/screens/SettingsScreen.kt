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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.brain.BrainMode
import com.masterchief.agent.brain.BrainProvider
import com.masterchief.agent.ui.components.HudPanel
import com.masterchief.agent.ui.theme.HaloGreen
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * Settings — brain switcher, provider keys and models, voice, profile,
 * and the permissions screen.
 *
 * Brain: one provider selector (Gemini, OpenRouter, OpenAI, Claude, Custom
 * endpoint, Local only, Auto). Every cloud provider has its own API-key
 * field (masked) and model field. Keys are stored on this phone only, in
 * DataStore — never in code, never in git, never sent anywhere except that
 * provider's own endpoint. Without any key, chat uses the local brain only.
 *
 * Every permission shows its REAL state (granted / not granted). Android
 * makes the user grant these in system settings — no app can do it silently,
 * and Chief never pretends a feature works without its permission.
 *
 * No dud buttons: every button here either saves a setting, opens the exact
 * system screen for a permission, or navigates to a real screen.
 */
@Composable
fun SettingsScreen(state: ChiefUiState, vm: ChiefViewModel, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text("SETTINGS — BRAIN & PERMISSIONS", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
        Spacer(Modifier.height(12.dp))

        HudPanel("BRAIN") {
            Text("Mode (current: ${state.brainMode})", style = MaterialTheme.typography.bodyMedium)
            Text(
                "AUTO uses the first provider with a key set, in this order: " +
                    "OpenRouter, Gemini, OpenAI, Claude, Custom. Simple orders " +
                    "stay on-device and cost nothing.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            BrainMode.entries.forEach { mode ->
                Button(
                    onClick = { vm.setBrainMode(mode) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) { Text(mode.name) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Keys are stored on this phone only. Never share a key, never " +
                    "commit it. Without any key, chat uses the local brain only.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("PROVIDERS — KEYS & MODELS") {
            ProviderBlock(
                provider = BrainProvider.GEMINI,
                keySet = state.hasGeminiKey,
                modelValue = state.geminiModel,
                onSaveKey = { vm.setGeminiKey(it) },
                onSaveModel = { vm.setGeminiModel(it) }
            )
            ProviderBlock(
                provider = BrainProvider.OPENROUTER,
                keySet = state.hasOpenRouterKey,
                modelValue = state.openRouterModel,
                onSaveKey = { vm.setOpenRouterKey(it) },
                onSaveModel = { vm.setOpenRouterModel(it) }
            )
            ProviderBlock(
                provider = BrainProvider.OPENAI,
                keySet = state.hasOpenAiKey,
                modelValue = state.openAiModel,
                onSaveKey = { vm.setOpenAiKey(it) },
                onSaveModel = { vm.setOpenAiModel(it) }
            )
            ProviderBlock(
                provider = BrainProvider.CLAUDE,
                keySet = state.hasClaudeKey,
                modelValue = state.claudeModel,
                onSaveKey = { vm.setClaudeKey(it) },
                onSaveModel = { vm.setClaudeModel(it) }
            )
            ProviderBlock(
                provider = BrainProvider.CUSTOM,
                keySet = state.hasCustomKey,
                modelValue = state.customModel,
                endpointValue = state.customEndpoint,
                onSaveKey = { vm.setCustomKey(it) },
                onSaveModel = { vm.setCustomModel(it) },
                onSaveEndpoint = { vm.setCustomEndpoint(it) }
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

        HudPanel("SPARTAN PROFILE") {
            Text(
                "What Chief has learned about you — likes, style, music, mood. " +
                    "Stored on this phone only.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Button(
                onClick = { onNavigate("profile") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("EDIT SPARTAN PROFILE") }
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("PERMISSIONS — REAL STATUS") {
            val p = state.permissions
            @Composable
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

/**
 * One provider's key + model editor. Top-level (not a local function) so
 * the Compose compiler never confuses it with a plain function.
 * The key field is masked; saving an empty key clears the stored key.
 */
@Composable
private fun ProviderBlock(
    provider: BrainProvider,
    keySet: Boolean,
    modelValue: String,
    onSaveKey: (String) -> Unit,
    onSaveModel: (String) -> Unit,
    endpointValue: String = "",
    onSaveEndpoint: ((String) -> Unit)? = null
) {
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(modelValue) }
    var endpoint by remember { mutableStateOf(endpointValue) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(provider.label, style = MaterialTheme.typography.titleSmall, color = SpartanGold)
        Text(
            "Key: ${if (keySet) "SET" else "NOT SET"}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (keySet) HaloGreen else SpartanGold
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("${provider.label} API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSaveKey(key); key = "" }) { Text("SAVE KEY") }
        if (onSaveEndpoint != null) {
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it },
                label = { Text("Endpoint base URL (before /chat/completions)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onSaveEndpoint(endpoint) }) { Text("SAVE ENDPOINT") }
        }
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("${provider.label} model id") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSaveModel(model) }) { Text("SAVE MODEL") }
        Text(provider.hint, style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}
