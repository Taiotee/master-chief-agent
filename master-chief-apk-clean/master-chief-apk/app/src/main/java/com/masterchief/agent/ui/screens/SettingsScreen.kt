package com.masterchief.agent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.masterchief.agent.ChiefUiState
import com.masterchief.agent.ChiefViewModel
import com.masterchief.agent.brain.BrainMode
import com.masterchief.agent.brain.BrainProvider
import com.masterchief.agent.brain.ReasoningEffort
import com.masterchief.agent.ui.components.HudPanel
import com.masterchief.agent.ui.theme.HaloGreen
import com.masterchief.agent.ui.theme.SpartanGold
import com.masterchief.agent.ui.theme.TextMuted

/**
 * Settings — brain switcher, provider keys/models/endpoints, reasoning
 * effort, voice, profile, and the permissions screen.
 *
 * Model settings work like a model dialog: each provider block is a draft.
 * Type a key (blank keeps the saved key), pick or type a model id, edit
 * the base URL where it applies — then SAVE writes it to DataStore, or
 * CANCEL throws the draft away. "Get model list" really fetches that
 * provider's catalogue (needs a saved key and a connection) and tapping a
 * model saves it at once. Reasoning effort is sent to OpenRouter and
 * OpenAI only. There is no Execution-mode selector: Master Chief's real
 * autonomy control is the cleanup permission on the Cleanup screen, and a
 * second toggle with no wired behaviour would be a dud.
 *
 * Keys are stored on this phone only, in DataStore — never in code, never
 * in git, never sent anywhere except that provider's own endpoint.
 *
 * Every permission shows its REAL state (granted / not granted). Android
 * makes the user grant these in system settings — no app can do it silently,
 * and Chief never pretends a feature works without its permission.
 *
 * No dud buttons: every button here either saves a setting, fetches a real
 * list, opens the exact system screen for a permission, or navigates to a
 * real screen.
 */
@Composable
fun SettingsScreen(state: ChiefUiState, vm: ChiefViewModel, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()

    // Local-model file picker: pick a .task file already on the phone;
    // the ViewModel copies it into the app files dir as local_model.task.
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.installLocalModel(uri)
    }

    // Model picker dialog — shown while a provider's list is loading,
    // listed, or failed. The error text inside it is the real reason.
    val pickerProvider = state.modelListProvider
    if (pickerProvider != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissModelList() },
            title = { Text("MODELS — ${pickerProvider.label}") },
            text = {
                when {
                    state.modelListLoading -> Text("Fetching model list…")
                    state.modelListError != null -> Text(state.modelListError ?: "")
                    else -> Column {
                        Text("${state.modelList.size} models — tap one to use it.")
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.height(400.dp)) {
                            items(state.modelList) { id ->
                                TextButton(
                                    onClick = { vm.applyModelChoice(pickerProvider, id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(id, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { vm.dismissModelList() }) { Text("CLOSE") }
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text("SETTINGS — BRAIN & PERMISSIONS", style = MaterialTheme.typography.titleMedium, color = SpartanGold)
        Spacer(Modifier.height(12.dp))

        HudPanel("BRAIN") {
            Text("Mode (current: ${state.brainMode})", style = MaterialTheme.typography.bodyMedium)
            Text(
                "AUTO uses the first provider with a key set — OpenRouter first " +
                    "(the proven one), then Gemini, OpenAI, Claude, Custom. Simple orders " +
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

        HudPanel("MODEL SETTINGS") {
            Text(
                "OpenRouter is the proven one — start there. The others are " +
                    "optional extras; each is only used when you enter its key.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            ProviderBlock(
                provider = BrainProvider.OPENROUTER,
                keySet = state.hasOpenRouterKey,
                modelValue = state.openRouterModel,
                endpointValue = state.openRouterEndpoint,
                showEndpointField = true,
                endpointLabel = "Base URL",
                fixedEndpointText = null,
                isFetchingModels = state.modelListLoading && pickerProvider == BrainProvider.OPENROUTER,
                onSaveKey = { vm.setOpenRouterKey(it) },
                onDeleteKey = { vm.setOpenRouterKey("") },
                onSaveModel = { vm.setOpenRouterModel(it) },
                onSaveEndpoint = { vm.setOpenRouterEndpoint(it) },
                onGetModels = { vm.fetchModelList(BrainProvider.OPENROUTER) }
            )
            ProviderBlock(
                provider = BrainProvider.GEMINI,
                keySet = state.hasGeminiKey,
                modelValue = state.geminiModel,
                endpointValue = "",
                showEndpointField = false,
                fixedEndpointText = "Endpoint: https://generativelanguage.googleapis.com/v1beta (fixed)",
                isFetchingModels = state.modelListLoading && pickerProvider == BrainProvider.GEMINI,
                onSaveKey = { vm.setGeminiKey(it) },
                onDeleteKey = { vm.setGeminiKey("") },
                onSaveModel = { vm.setGeminiModel(it) },
                onSaveEndpoint = null,
                onGetModels = { vm.fetchModelList(BrainProvider.GEMINI) }
            )
            ProviderBlock(
                provider = BrainProvider.OPENAI,
                keySet = state.hasOpenAiKey,
                modelValue = state.openAiModel,
                endpointValue = state.openAiEndpoint,
                showEndpointField = true,
                endpointLabel = "Base URL",
                fixedEndpointText = null,
                isFetchingModels = state.modelListLoading && pickerProvider == BrainProvider.OPENAI,
                onSaveKey = { vm.setOpenAiKey(it) },
                onDeleteKey = { vm.setOpenAiKey("") },
                onSaveModel = { vm.setOpenAiModel(it) },
                onSaveEndpoint = { vm.setOpenAiEndpoint(it) },
                onGetModels = { vm.fetchModelList(BrainProvider.OPENAI) }
            )
            ProviderBlock(
                provider = BrainProvider.CLAUDE,
                keySet = state.hasClaudeKey,
                modelValue = state.claudeModel,
                endpointValue = "",
                showEndpointField = false,
                fixedEndpointText = "Endpoint: https://api.anthropic.com/v1 (fixed)",
                isFetchingModels = state.modelListLoading && pickerProvider == BrainProvider.CLAUDE,
                onSaveKey = { vm.setClaudeKey(it) },
                onDeleteKey = { vm.setClaudeKey("") },
                onSaveModel = { vm.setClaudeModel(it) },
                onSaveEndpoint = null,
                onGetModels = { vm.fetchModelList(BrainProvider.CLAUDE) }
            )
            ProviderBlock(
                provider = BrainProvider.CUSTOM,
                keySet = state.hasCustomKey,
                modelValue = state.customModel,
                endpointValue = state.customEndpoint,
                showEndpointField = true,
                endpointLabel = "Base URL (required)",
                fixedEndpointText = null,
                isFetchingModels = state.modelListLoading && pickerProvider == BrainProvider.CUSTOM,
                onSaveKey = { vm.setCustomKey(it) },
                onDeleteKey = { vm.setCustomKey("") },
                onSaveModel = { vm.setCustomModel(it) },
                onSaveEndpoint = { vm.setCustomEndpoint(it) },
                onGetModels = { vm.fetchModelList(BrainProvider.CUSTOM) }
            )
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("LOCAL MODEL — ON-DEVICE AI") {
            val lm = state.localModel
            Text(
                when {
                    lm == null -> "No local model installed."
                    lm.meetsMinSize ->
                        "Installed: ${lm.fileName} — ${lm.sizeBytes / 1_000_000} MB"
                    else ->
                        "Installed: ${lm.fileName} — ${lm.sizeBytes / 1_000_000} MB — " +
                            "TOO SMALL (needs over 100 MB; the engine will ignore it)"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (lm != null) {
                Text(lm.path, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            } else if (state.localModelExpectedPath.isNotBlank()) {
                Text(
                    "Expected file: ${state.localModelExpectedPath}",
                    style = MaterialTheme.typography.labelMedium, color = TextMuted
                )
            }
            Text(
                if (state.localModelEngineWired)
                    "Local LLM engine: WIRED — a staged model answers medium tasks offline."
                else
                    "Local LLM engine: NOT WIRED in this build. The rule-based Local " +
                        "Brain (time, alarms, apps, settings) is what answers simple " +
                        "tasks offline. A staged model file will work once the " +
                        "MediaPipe engine is wired.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Text(
                "This phone (OPPO A74 5G, Snapdragon 480, 6 GB RAM, CPU-only): " +
                    "only small models fit, and they will be slow — a few seconds " +
                    "per sentence.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { modelPicker.launch("*/*") },
                    enabled = !state.localModelBusy,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) { Text(if (state.localModelBusy) "COPYING…" else "CHOOSE MODEL FILE (.task)") }
                Button(
                    onClick = { vm.refreshLocalModel() },
                    enabled = !state.localModelBusy,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) { Text("REFRESH STATUS") }
            }
            if (lm != null) {
                Button(
                    onClick = { vm.deleteLocalModel() },
                    enabled = !state.localModelBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) { Text("REMOVE MODEL") }
            }
            if (state.localModelMessage != null) {
                Text(
                    state.localModelMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpartanGold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "GET A MODEL — download on Wi-Fi, these are large:",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "1. Qwen2.5-0.5B-Instruct (q8 .task) — about 547 MB. Best fit " +
                    "for this phone: smallest and fastest. Free, no sign-in.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Text(
                "2. Gemma-2B-IT (q8 .task) — about 2 GB, and gated: accept " +
                    "Google's Gemma licence on Hugging Face first. Bigger and " +
                    "slower on this phone.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Text(
                "After downloading, come back here and tap CHOOSE MODEL FILE, " +
                    "then pick the .task file. In-app download is NOT implemented " +
                    "yet — download in your browser (links below), then pick the " +
                    "file here.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.openUrl("https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) { Text("QWEN2.5-0.5B — OPEN PAGE") }
            Button(
                onClick = { vm.openUrl("https://huggingface.co/litert-community/Gemma2-2B-IT") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) { Text("GEMMA-2B-IT — OPEN PAGE") }
            Button(
                onClick = { vm.openUrl("https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) { Text("GOOGLE AI EDGE — MODEL GUIDE") }
        }
        Spacer(Modifier.height(12.dp))

        HudPanel("REASONING EFFORT") {
            Text("Current: ${state.reasoningEffort}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Applies to OpenRouter and OpenAI only. Auto means the provider " +
                    "decides and nothing extra is sent. Gemini, Claude and Local " +
                    "do not use this setting.",
                style = MaterialTheme.typography.labelMedium, color = TextMuted
            )
            ReasoningEffort.entries.forEach { effort ->
                Button(
                    onClick = { vm.setReasoningEffort(effort) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) { Text(effort.name) }
            }
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
 * One provider's model settings, as a draft: edit key / model / base URL,
 * then SAVE writes the changed fields to DataStore, or CANCEL throws the
 * draft away. A blank key field keeps the saved key; ticking "Delete saved
 * API key" and saving clears it. Top-level (not a local function) so the
 * Compose compiler never confuses it with a plain function.
 */
@Composable
private fun ProviderBlock(
    provider: BrainProvider,
    keySet: Boolean,
    modelValue: String,
    endpointValue: String,
    showEndpointField: Boolean,
    fixedEndpointText: String?,
    isFetchingModels: Boolean,
    onSaveKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onSaveModel: (String) -> Unit,
    onSaveEndpoint: ((String) -> Unit)?,
    onGetModels: () -> Unit,
    endpointLabel: String = "Base URL"
) {
    var key by remember { mutableStateOf("") }
    // Keyed on the saved value, so picking a model from the list refreshes
    // the field to the newly saved model id.
    var model by remember(modelValue) { mutableStateOf(modelValue) }
    var endpoint by remember(endpointValue) { mutableStateOf(endpointValue) }
    var deleteKey by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(provider.label, style = MaterialTheme.typography.titleSmall, color = SpartanGold)
        Text(
            "Key: ${if (keySet) "SET" else "NOT SET"}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (keySet) HaloGreen else SpartanGold
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key (leave blank to keep saved key)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (keySet) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteKey, onCheckedChange = { deleteKey = it })
                Text("Delete saved API key", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (showEndpointField) {
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it },
                label = { Text(endpointLabel) },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (fixedEndpointText != null) {
            Text(fixedEndpointText, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model id") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = onGetModels,
                enabled = !isFetchingModels,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) { Text(if (isFetchingModels) "FETCHING…" else "GET MODEL LIST") }
            Button(
                onClick = {
                    if (deleteKey) {
                        onDeleteKey()
                        deleteKey = false
                    } else if (key.isNotBlank()) {
                        onSaveKey(key)
                    }
                    if (model.isNotBlank() && model != modelValue) onSaveModel(model)
                    if (onSaveEndpoint != null && endpoint.isNotBlank() && endpoint != endpointValue) {
                        onSaveEndpoint(endpoint)
                    }
                    key = ""
                },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) { Text("SAVE") }
            Button(
                onClick = {
                    key = ""
                    model = modelValue
                    endpoint = endpointValue
                    deleteKey = false
                },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) { Text("CANCEL") }
        }
        Text(provider.hint, style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}
