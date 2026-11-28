package com.masterchief.agent

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterchief.agent.agent.CommandParser
import com.masterchief.agent.agent.PhoneController
import com.masterchief.agent.agent.PhotoSweeper
import com.masterchief.agent.agent.cleanup.CleanupEngine
import com.masterchief.agent.agent.cleanup.CleanupPermission
import com.masterchief.agent.agent.cleanup.ClutterItem
import com.masterchief.agent.agent.cleanup.StorageMonitor
import com.masterchief.agent.brain.BrainConfig
import com.masterchief.agent.brain.BrainMode
import com.masterchief.agent.brain.BrainProvider
import com.masterchief.agent.brain.BrainRouter
import com.masterchief.agent.brain.ClaudeClient
import com.masterchief.agent.brain.GeminiClient
import com.masterchief.agent.brain.LocalBrain
import com.masterchief.agent.brain.LocalLlm
import com.masterchief.agent.brain.OpenAiCompatibleClient
import com.masterchief.agent.brain.WeatherClient
import com.masterchief.agent.brain.WeatherNow
import com.masterchief.agent.data.AppEntry
import com.masterchief.agent.data.ChatMessage
import com.masterchief.agent.data.CleanupCandidate
import com.masterchief.agent.data.DeviceInfo
import com.masterchief.agent.data.DeviceStatus
import com.masterchief.agent.data.JsonStore
import com.masterchief.agent.data.Mission
import com.masterchief.agent.data.PermissionStatus
import com.masterchief.agent.data.SettingsRepository
import com.masterchief.agent.data.SpartanProfile
import com.masterchief.agent.services.LiveNotification
import com.masterchief.agent.services.NotificationHub
import com.masterchief.agent.voice.MilitaryVoice
import com.masterchief.agent.workers.CleanupAssessmentWorker
import com.masterchief.agent.workers.CleanupExecutor
import com.masterchief.agent.workers.MorningBriefingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ChiefUiState(
    val chat: List<ChatMessage> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val profile: SpartanProfile = SpartanProfile(),
    val brainMode: BrainMode = BrainMode.AUTO,
    val lastBrainUsed: String = "—",
    val cleanupItems: List<ClutterItem> = emptyList(),
    val cleanupReport: String = "",
    val storage: StorageMonitor.StorageState? = null,
    val cleanupPermission: CleanupPermission = CleanupPermission.AUTO_SAFE_ONLY,
    val busy: Boolean = false,
    // HUD v2 — all read from the phone / network at runtime, never invented.
    val device: DeviceStatus? = null,
    val permissions: PermissionStatus? = null,
    val weather: WeatherNow? = null,
    val weatherError: String? = null,
    val hasGeminiKey: Boolean = false,
    val hasOpenRouterKey: Boolean = false,
    val hasOpenAiKey: Boolean = false,
    val hasClaudeKey: Boolean = false,
    val hasCustomKey: Boolean = false,
    val geminiModel: String = "",
    val openRouterModel: String = "",
    val openAiModel: String = "",
    val claudeModel: String = "",
    val customModel: String = "",
    val customEndpoint: String = "",
    val voiceEnabled: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val appsLoaded: Boolean = false,
    val notifications: List<LiveNotification> = emptyList(),
    val photoDuplicates: List<CleanupCandidate> = emptyList(),
    val photoScanDone: Boolean = false
)

class ChiefViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JsonStore(app)
    private val settings = SettingsRepository(app)
    private val phone = PhoneController(app)
    private val voice = MilitaryVoice(app).also { it.init() }
    private val localBrain = LocalBrain(app)
    private val localLlm = LocalLlm(app)
    private val photoSweeper = PhotoSweeper(app)
    // The router needs a client factory that reads the keys at call time
    // (the keys live in DataStore, a Flow). runBlocking is used here because
    // the factory is called from an IO coroutine; a production build should
    // cache the keys in memory on startup instead. A provider returns null
    // when its key (or model / endpoint) is not set — the router then says
    // so plainly instead of failing silently.
    private val realRouter = BrainRouter(app, localBrain, localLlm) { provider ->
        val cfg = kotlinx.coroutines.runBlocking { loadBrainConfig() }
        // Null when the provider is not fully set up (key, model, and for
        // Custom the endpoint) — the router then explains what is missing.
        if (!cfg.ready(provider)) null
        else when (provider) {
            BrainProvider.GEMINI ->
                cfg.geminiKey.takeIf { it.isNotBlank() }?.let { GeminiClient(it, cfg.geminiModel) }
            BrainProvider.OPENROUTER ->
                cfg.openRouterKey.takeIf { it.isNotBlank() }?.let {
                    OpenAiCompatibleClient(
                        it, cfg.openRouterModel,
                        OpenAiCompatibleClient.OPENROUTER_BASE_URL, "OpenRouter",
                        OpenAiCompatibleClient.OPENROUTER_HEADERS
                    )
                }
            BrainProvider.OPENAI ->
                cfg.openAiKey.takeIf { it.isNotBlank() }?.let {
                    OpenAiCompatibleClient(
                        it, cfg.openAiModel,
                        OpenAiCompatibleClient.OPENAI_BASE_URL, "OpenAI"
                    )
                }
            BrainProvider.CLAUDE ->
                cfg.claudeKey.takeIf { it.isNotBlank() }?.let { ClaudeClient(it, cfg.claudeModel) }
            BrainProvider.CUSTOM ->
                if (cfg.customKey.isNotBlank() && cfg.customEndpoint.isNotBlank()) {
                    OpenAiCompatibleClient(
                        cfg.customKey, cfg.customModel, cfg.customEndpoint, "Custom"
                    )
                } else null
        }
    }

    /** Read every provider key / model from Settings in one place. */
    private suspend fun loadBrainConfig(): BrainConfig = BrainConfig(
        geminiKey = settings.geminiApiKey.first(),
        geminiModel = settings.geminiModel.first(),
        openRouterKey = settings.openRouterApiKey.first(),
        openRouterModel = settings.openRouterModel.first(),
        openAiKey = settings.openAiApiKey.first(),
        openAiModel = settings.openAiModel.first(),
        claudeKey = settings.claudeApiKey.first(),
        claudeModel = settings.claudeModel.first(),
        customKey = settings.customApiKey.first(),
        customModel = settings.customModel.first(),
        customEndpoint = settings.customEndpoint.first()
    )

    private val _state = MutableStateFlow(
        ChiefUiState(
            chat = store.loadChat(),
            missions = store.loadMissions(),
            profile = store.loadProfile(),
            cleanupItems = store.loadLastAssessment()?.items ?: emptyList(),
            cleanupReport = store.loadCleanupReports().firstOrNull()?.summary ?: "",
            cleanupPermission = runCatching {
                CleanupPermission.valueOf(store.loadCleanupPermission())
            }.getOrDefault(CleanupPermission.AUTO_SAFE_ONLY)
        )
    )
    val state: StateFlow<ChiefUiState> = _state

    init {
        MorningBriefingWorker.scheduleDaily(app)
        CleanupAssessmentWorker.scheduleWeekly(app)
        refreshStorage()
        refreshDevice()
        refreshWeather()
        viewModelScope.launch {
            val cfg = loadBrainConfig()
            _state.value = _state.value.copy(
                hasGeminiKey = cfg.geminiKey.isNotBlank(),
                hasOpenRouterKey = cfg.openRouterKey.isNotBlank(),
                hasOpenAiKey = cfg.openAiKey.isNotBlank(),
                hasClaudeKey = cfg.claudeKey.isNotBlank(),
                hasCustomKey = cfg.customKey.isNotBlank(),
                geminiModel = cfg.geminiModel,
                openRouterModel = cfg.openRouterModel,
                openAiModel = cfg.openAiModel,
                claudeModel = cfg.claudeModel,
                customModel = cfg.customModel,
                customEndpoint = cfg.customEndpoint,
                voiceEnabled = settings.voiceEnabled.first()
            )
        }
        viewModelScope.launch {
            NotificationHub.notifications.collect { list ->
                _state.value = _state.value.copy(notifications = list)
            }
        }
        addChiefMessage(MilitaryVoice.standingBy())
    }

    private fun addChiefMessage(text: String, brain: String = "") {
        val msg = ChatMessage(UUID.randomUUID().toString(), true, text, System.currentTimeMillis())
        val chat = (_state.value.chat + msg).takeLast(200)
        store.saveChat(chat)
        _state.value = _state.value.copy(chat = chat, lastBrainUsed = brain.ifBlank { _state.value.lastBrainUsed })
        viewModelScope.launch { if (settings.voiceEnabled.first()) voice.speak(text) }
    }

    private fun addUserMessage(text: String) {
        val msg = ChatMessage(UUID.randomUUID().toString(), false, text, System.currentTimeMillis())
        val chat = (_state.value.chat + msg).takeLast(200)
        store.saveChat(chat)
        _state.value = _state.value.copy(chat = chat)
    }

    fun refreshStorage() {
        _state.value = _state.value.copy(storage = StorageMonitor.state(getApplication()))
    }

    /** Real device readouts + permission states, re-read from Android. */
    fun refreshDevice() {
        val app = getApplication<Application>()
        _state.value = _state.value.copy(
            device = DeviceInfo.status(app),
            permissions = DeviceInfo.permissions(app)
        )
        refreshStorage()
    }

    /** Real Lagos weather from Open-Meteo. On failure, weatherError is set
     *  and the UI says "unavailable" — never an invented temperature. */
    fun refreshWeather() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = WeatherClient.fetch("Lagos", WeatherClient.LAGOS_LAT, WeatherClient.LAGOS_LON)
            result.fold(
                onSuccess = { _state.value = _state.value.copy(weather = it, weatherError = null) },
                onFailure = { _state.value = _state.value.copy(weatherError = it.message ?: "Network error") }
            )
        }
    }

    /** Real list of launchable apps installed on this phone. */
    fun loadApps() {
        if (_state.value.appsLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            val list = DeviceInfo.installedApps(getApplication())
            _state.value = _state.value.copy(apps = list, appsLoaded = true)
        }
    }

    fun openAppByPackage(pkg: String) {
        addChiefMessage(phone.openAppByPackage(pkg))
    }

    /** Real duplicate-photo sweep (MediaStore). Nothing is deleted here. */
    fun runPhotoSweep() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(busy = true, photoScanDone = false)
            try {
                val dups = photoSweeper.sweep()
                _state.value = _state.value.copy(photoDuplicates = dups, photoScanDone = true)
                addChiefMessage(
                    if (dups.isEmpty()) "Photo sweep complete, sir. No exact duplicates found."
                    else "Photo sweep complete, sir. ${dups.size} exact duplicate(s) found — " +
                        "oldest copy is always kept. Review them on the Photos hub before anything is deleted."
                )
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** Delete ONE duplicate the user confirmed. Android may still ask for
     *  its own confirmation in Photos; if it does, say so honestly. */
    fun deletePhotoCandidate(c: CleanupCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(c.path)
            val deleted = runCatching {
                getApplication<Application>().contentResolver.delete(uri, null, null)
            }.getOrNull() ?: 0
            _state.value = _state.value.copy(
                photoDuplicates = _state.value.photoDuplicates.filter { it.id != c.id }
            )
            refreshStorage()
            addChiefMessage(
                if (deleted > 0) "Duplicate deleted, sir. Freed ${CleanupEngine.formatBytes(c.sizeBytes)}."
                else "Could not delete that one, sir — Android needs your confirmation in Photos. " +
                    "Open Photos and delete it there."
            )
        }
    }

    fun keepPhotoCandidate(c: CleanupCandidate) {
        _state.value = _state.value.copy(
            photoDuplicates = _state.value.photoDuplicates.filter { it.id != c.id }
        )
        addChiefMessage("Understood, sir. Keeping ${c.fileName}.")
    }

    /** Set an alarm — Android opens the Clock app to confirm; Chief cannot
     *  set an alarm silently, and says so. */
    fun setAlarm(hour: Int, minute: Int, label: String = "Master Chief alarm") {
        addChiefMessage(phone.setAlarm(hour, minute, label))
    }

    /** The main entry point: the user typed / spoke an order in the standby chat box. */
    fun sendCommand(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        addUserMessage(text)
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(busy = true)
            try {
                when (val cmd = CommandParser.parse(text)) {
                    is com.masterchief.agent.agent.Command.OpenApp ->
                        addChiefMessage(phone.openApp(cmd.appName))
                    is com.masterchief.agent.agent.Command.Call ->
                        addChiefMessage(phone.dial(cmd.who))
                    is com.masterchief.agent.agent.Command.Text ->
                        addChiefMessage(phone.draftText(cmd.who, cmd.body))
                    is com.masterchief.agent.agent.Command.SetAlarm -> {
                        val m = Regex("(\\d{1,2})[:.](\\d{2})").find(text)
                        if (m != null) addChiefMessage(
                            phone.setAlarm(m.groupValues[1].toInt(), m.groupValues[2].toInt()))
                        else addChiefMessage("What time, sir? Say it like 07:30.")
                    }
                    is com.masterchief.agent.agent.Command.ToggleSetting ->
                        addChiefMessage(when (cmd.setting) {
                            "wifi" -> phone.setWifi(cmd.on)
                            "dnd" -> "Do Not Disturb needs notification-policy access, sir — " +
                                "open Settings > Apps > Master Chief to grant it once."
                            else -> "Setting not supported yet, sir: ${cmd.setting}."
                        })
                    is com.masterchief.agent.agent.Command.SetBrightness ->
                        addChiefMessage(phone.setBrightness(cmd.percent))
                    is com.masterchief.agent.agent.Command.PlayMusic ->
                        addChiefMessage(phone.playMusic(cmd.query))
                    is com.masterchief.agent.agent.Command.CleanPhotos -> runCleanup("manual")
                    is com.masterchief.agent.agent.Command.RefreshBriefing ->
                        addChiefMessage(briefingText())
                    is com.masterchief.agent.agent.Command.SwitchBrain -> {
                        val mode = runCatching { BrainMode.valueOf(cmd.mode) }.getOrNull()
                        if (mode == null) {
                            addChiefMessage("Unknown brain mode, sir: ${cmd.mode}.")
                        } else {
                            settings.setBrainMode(mode)
                            _state.value = _state.value.copy(brainMode = mode)
                            addChiefMessage("Brain switched to ${mode.name}, sir.")
                        }
                    }
                    is com.masterchief.agent.agent.Command.Unknown -> {
                        val mode = settings.brainMode.first()
                        val config = loadBrainConfig()
                        val profile = store.loadProfile()
                        val result = realRouter.route(mode, text, profile, config)
                        addChiefMessage(result.text, result.engine.name)
                    }
                }
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private fun briefingText(): String {
        val date = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
            .format(java.util.Date())
        val missions = _state.value.missions.filter { !it.done }
        val weather = _state.value.weather?.let { "${it.tempC}°C, ${it.description} in ${it.place}" }
            ?: "weather unavailable"
        return "Briefing, sir. Today is $date. Lagos weather: $weather. " +
            "${missions.size} open directives. " +
            "Storage: ${_state.value.storage?.let { "${it.freeBytes / 1_048_576} MB free" } ?: "unknown"}."
    }

    // ---- Cleanup: assessment, review, confirm, report --------------------

    /** Run a full intelligent assessment now (manual trigger). */
    fun runCleanup(trigger: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(busy = true)
            try {
                val learning = store.loadCleanupLearning()
                val engine = CleanupEngine(getApplication())
                val assessment = engine.assess(trigger, learning)
                store.saveLastAssessment(assessment)
                val (auto, review) = engine.applyPermission(assessment, _state.value.cleanupPermission)
                var freed = 0L
                val executor = CleanupExecutor(getApplication())
                for (item in auto) freed += executor.deleteIfSafe(item) ?: 0L
                val report = engine.buildReport(assessment, auto, review, freed)
                store.saveCleanupReport(report)
                _state.value = _state.value.copy(
                    cleanupItems = assessment.items,
                    cleanupReport = report.summary
                )
                refreshStorage()
                addChiefMessage(report.summary)
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** User tapped Confirm on one review item. */
    fun confirmCleanup(item: ClutterItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = CleanupExecutor(getApplication()).deleteConfirmed(item) ?: 0L
            store.recordCleanupDecision(item.kind, deleted = true)
            val remaining = _state.value.cleanupItems.filter { it.id != item.id }
            _state.value = _state.value.copy(cleanupItems = remaining)
            refreshStorage()
            addChiefMessage(
                if (freed > 0) "Confirmed and cleared, sir. Freed ${CleanupEngine.formatBytes(freed)}."
                else "Could not delete that one, sir — Android may need its own confirmation. Check the file in Photos."
            )
        }
    }

    /** User tapped Keep on one review item — Chief learns from this. */
    fun keepCleanup(item: ClutterItem) {
        viewModelScope.launch(Dispatchers.IO) {
            store.recordCleanupDecision(item.kind, deleted = false)
            _state.value = _state.value.copy(
                cleanupItems = _state.value.cleanupItems.filter { it.id != item.id })
            addChiefMessage("Understood, sir. I will keep ${item.fileName} — and remember that.")
        }
    }

    fun setCleanupPermission(level: CleanupPermission) {
        viewModelScope.launch {
            store.saveCleanupPermission(level.name)
            _state.value = _state.value.copy(cleanupPermission = level)
            addChiefMessage(
                when (level) {
                    CleanupPermission.ASK_EVERYTHING ->
                        "Cleanup permission set to ask for everything, sir. Nothing auto-cleans."
                    CleanupPermission.AUTO_SAFE_ONLY ->
                        "Cleanup permission set to auto-clean safe junk only, sir. Everything else asks first."
                    CleanupPermission.PAUSED ->
                        "Cleanup paused, sir. Manual sweeps only."
                }
            )
        }
    }

    // ---- Missions / profile ----------------------------------------------

    fun addMission(title: String) {
        val m = Mission(UUID.randomUUID().toString(), title, false, System.currentTimeMillis())
        val list = _state.value.missions + m
        store.saveMissions(list)
        _state.value = _state.value.copy(missions = list)
    }

    fun toggleMission(id: String) {
        val list = _state.value.missions.map { if (it.id == id) it.copy(done = !it.done) else it }
        store.saveMissions(list)
        _state.value = _state.value.copy(missions = list)
    }

    fun saveProfile(profile: SpartanProfile) {
        store.saveProfile(profile)
        _state.value = _state.value.copy(profile = profile)
    }

    fun setBrainMode(mode: BrainMode) {
        viewModelScope.launch {
            settings.setBrainMode(mode)
            _state.value = _state.value.copy(brainMode = mode)
        }
    }

    fun setGeminiKey(key: String) {
        viewModelScope.launch {
            settings.setGeminiApiKey(key)
            _state.value = _state.value.copy(hasGeminiKey = key.isNotBlank())
        }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch {
            settings.setGeminiModel(model)
            _state.value = _state.value.copy(geminiModel = model)
        }
    }

    fun setOpenRouterKey(key: String) {
        viewModelScope.launch {
            settings.setOpenRouterApiKey(key)
            _state.value = _state.value.copy(hasOpenRouterKey = key.isNotBlank())
        }
    }

    fun setOpenRouterModel(model: String) {
        viewModelScope.launch {
            settings.setOpenRouterModel(model)
            _state.value = _state.value.copy(openRouterModel = model)
        }
    }

    fun setOpenAiKey(key: String) {
        viewModelScope.launch {
            settings.setOpenAiApiKey(key)
            _state.value = _state.value.copy(hasOpenAiKey = key.isNotBlank())
        }
    }

    fun setOpenAiModel(model: String) {
        viewModelScope.launch {
            settings.setOpenAiModel(model)
            _state.value = _state.value.copy(openAiModel = model)
        }
    }

    fun setClaudeKey(key: String) {
        viewModelScope.launch {
            settings.setClaudeApiKey(key)
            _state.value = _state.value.copy(hasClaudeKey = key.isNotBlank())
        }
    }

    fun setClaudeModel(model: String) {
        viewModelScope.launch {
            settings.setClaudeModel(model)
            _state.value = _state.value.copy(claudeModel = model)
        }
    }

    fun setCustomKey(key: String) {
        viewModelScope.launch {
            settings.setCustomApiKey(key)
            _state.value = _state.value.copy(hasCustomKey = key.isNotBlank())
        }
    }

    fun setCustomModel(model: String) {
        viewModelScope.launch {
            settings.setCustomModel(model)
            _state.value = _state.value.copy(customModel = model)
        }
    }

    fun setCustomEndpoint(url: String) {
        viewModelScope.launch {
            settings.setCustomEndpoint(url)
            _state.value = _state.value.copy(customEndpoint = url.trim().trimEnd('/'))
        }
    }

    fun setVoiceEnabled(on: Boolean) {
        viewModelScope.launch {
            settings.setVoiceEnabled(on)
            _state.value = _state.value.copy(voiceEnabled = on)
        }
    }

    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${getApplication<Application>().packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${getApplication<Application>().packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    override fun onCleared() { voice.shutdown(); super.onCleared() }
}
