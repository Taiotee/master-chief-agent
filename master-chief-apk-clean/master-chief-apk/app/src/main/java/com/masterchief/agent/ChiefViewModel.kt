package com.masterchief.agent

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterchief.agent.agent.CommandParser
import com.masterchief.agent.agent.PhoneController
import com.masterchief.agent.agent.cleanup.CleanupEngine
import com.masterchief.agent.agent.cleanup.CleanupPermission
import com.masterchief.agent.agent.cleanup.ClutterItem
import com.masterchief.agent.agent.cleanup.StorageMonitor
import com.masterchief.agent.brain.BrainMode
import com.masterchief.agent.brain.BrainRouter
import com.masterchief.agent.brain.GeminiClient
import com.masterchief.agent.brain.LocalBrain
import com.masterchief.agent.brain.LocalLlm
import com.masterchief.agent.data.ChatMessage
import com.masterchief.agent.data.CleanupCandidate
import com.masterchief.agent.data.CleanupStatus
import com.masterchief.agent.data.JsonStore
import com.masterchief.agent.data.Mission
import com.masterchief.agent.data.SettingsRepository
import com.masterchief.agent.data.SpartanProfile
import com.masterchief.agent.voice.MilitaryVoice
import com.masterchief.agent.workers.CleanupAssessmentWorker
import com.masterchief.agent.workers.CleanupExecutor
import com.masterchief.agent.workers.MorningBriefingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val busy: Boolean = false
)

class ChiefViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JsonStore(app)
    private val settings = SettingsRepository(app)
    private val phone = PhoneController(app)
    private val voice = MilitaryVoice(app).also { it.init() }
    private val localBrain = LocalBrain(app)
    private val localLlm = LocalLlm(app)
    // The router needs a Gemini factory that reads the key at call time
    // (the key lives in DataStore, a Flow). runBlocking is used here because
    // the factory is called from an IO coroutine; a production build should
    // cache the key in memory on startup instead.
    private val realRouter = BrainRouter(app, localBrain, localLlm) { model ->
        val key = kotlinx.coroutines.runBlocking { settings.geminiApiKey.first() }
        if (key.isBlank()) null else GeminiClient(key, model)
    }

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
                        val mode = BrainMode.valueOf(cmd.mode)
                        settings.setBrainMode(mode)
                        _state.value = _state.value.copy(brainMode = mode)
                        addChiefMessage("Brain switched to ${mode.name}, sir.")
                    }
                    is com.masterchief.agent.agent.Command.Unknown -> {
                        val mode = settings.brainMode.first()
                        val model = settings.geminiModel.first()
                        val profile = store.loadProfile()
                        val result = realRouter.route(mode, text, profile, model)
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
        return "Briefing, sir. Today is $date. Lagos weather: check the Briefing screen " +
            "for the latest reading. ${missions.size} open directives. " +
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
        viewModelScope.launch { settings.setGeminiApiKey(key) }
    }

    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    override fun onCleared() { voice.shutdown(); super.onCleared() }
}
