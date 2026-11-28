package com.masterchief.agent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.masterchief.agent.brain.BrainConfig
import com.masterchief.agent.brain.BrainMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chief_settings")

/**
 * App settings, including the brain switcher. Every provider API key is
 * stored here (DataStore, app-private) — never in source code, never in git.
 * A production build should move the keys to EncryptedSharedPreferences;
 * see README § Security.
 */
class SettingsRepository(private val context: Context) {
    companion object {
        private val BRAIN_MODE = stringPreferencesKey("brain_mode")
        private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val OPENROUTER_KEY = stringPreferencesKey("openrouter_api_key")
        private val OPENROUTER_MODEL = stringPreferencesKey("openrouter_model")
        private val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        private val OPENAI_MODEL = stringPreferencesKey("openai_model")
        private val CLAUDE_KEY = stringPreferencesKey("claude_api_key")
        private val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        private val CUSTOM_KEY = stringPreferencesKey("custom_api_key")
        private val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        private val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        private val MILITARY_VOICE = booleanPreferencesKey("military_voice")
        private val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        private val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val NOTIFICATION_FILTER = booleanPreferencesKey("notification_filter")
        private val BRIEFING_TIME = stringPreferencesKey("briefing_time")
    }

    val brainMode: Flow<BrainMode> = context.dataStore.data.map {
        runCatching { BrainMode.valueOf(it[BRAIN_MODE] ?: BrainMode.AUTO.name) }
            .getOrDefault(BrainMode.AUTO)
    }
    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[GEMINI_KEY] ?: "" }
    val geminiModel: Flow<String> = context.dataStore.data.map {
        it[GEMINI_MODEL] ?: BrainConfig.DEFAULT_GEMINI_MODEL
    }
    val openRouterApiKey: Flow<String> = context.dataStore.data.map { it[OPENROUTER_KEY] ?: "" }
    val openRouterModel: Flow<String> = context.dataStore.data.map {
        it[OPENROUTER_MODEL] ?: BrainConfig.DEFAULT_OPENROUTER_MODEL
    }
    val openAiApiKey: Flow<String> = context.dataStore.data.map { it[OPENAI_KEY] ?: "" }
    val openAiModel: Flow<String> = context.dataStore.data.map {
        it[OPENAI_MODEL] ?: BrainConfig.DEFAULT_OPENAI_MODEL
    }
    val claudeApiKey: Flow<String> = context.dataStore.data.map { it[CLAUDE_KEY] ?: "" }
    val claudeModel: Flow<String> = context.dataStore.data.map {
        it[CLAUDE_MODEL] ?: BrainConfig.DEFAULT_CLAUDE_MODEL
    }
    val customApiKey: Flow<String> = context.dataStore.data.map { it[CUSTOM_KEY] ?: "" }
    val customModel: Flow<String> = context.dataStore.data.map { it[CUSTOM_MODEL] ?: "" }
    val customEndpoint: Flow<String> = context.dataStore.data.map { it[CUSTOM_ENDPOINT] ?: "" }
    val militaryVoice: Flow<Boolean> = context.dataStore.data.map { it[MILITARY_VOICE] ?: true }
    val voiceEnabled: Flow<Boolean> = context.dataStore.data.map { it[VOICE_ENABLED] ?: true }
    val bubbleEnabled: Flow<Boolean> = context.dataStore.data.map { it[BUBBLE_ENABLED] ?: false }
    val notificationFilter: Flow<Boolean> =
        context.dataStore.data.map { it[NOTIFICATION_FILTER] ?: false }
    val briefingTime: Flow<String> = context.dataStore.data.map { it[BRIEFING_TIME] ?: "06:30" }

    suspend fun setBrainMode(mode: BrainMode) {
        context.dataStore.edit { it[BRAIN_MODE] = mode.name }
    }
    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[GEMINI_KEY] = key.trim() }
    }
    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { it[GEMINI_MODEL] = model.trim() }
    }
    suspend fun setOpenRouterApiKey(key: String) {
        context.dataStore.edit { it[OPENROUTER_KEY] = key.trim() }
    }
    suspend fun setOpenRouterModel(model: String) {
        context.dataStore.edit { it[OPENROUTER_MODEL] = model.trim() }
    }
    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { it[OPENAI_KEY] = key.trim() }
    }
    suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit { it[OPENAI_MODEL] = model.trim() }
    }
    suspend fun setClaudeApiKey(key: String) {
        context.dataStore.edit { it[CLAUDE_KEY] = key.trim() }
    }
    suspend fun setClaudeModel(model: String) {
        context.dataStore.edit { it[CLAUDE_MODEL] = model.trim() }
    }
    suspend fun setCustomApiKey(key: String) {
        context.dataStore.edit { it[CUSTOM_KEY] = key.trim() }
    }
    suspend fun setCustomModel(model: String) {
        context.dataStore.edit { it[CUSTOM_MODEL] = model.trim() }
    }
    suspend fun setCustomEndpoint(url: String) {
        context.dataStore.edit { it[CUSTOM_ENDPOINT] = url.trim().trimEnd('/') }
    }
    suspend fun setMilitaryVoice(on: Boolean) {
        context.dataStore.edit { it[MILITARY_VOICE] = on }
    }
    suspend fun setVoiceEnabled(on: Boolean) {
        context.dataStore.edit { it[VOICE_ENABLED] = on }
    }
    suspend fun setBubbleEnabled(on: Boolean) {
        context.dataStore.edit { it[BUBBLE_ENABLED] = on }
    }
    suspend fun setNotificationFilter(on: Boolean) {
        context.dataStore.edit { it[NOTIFICATION_FILTER] = on }
    }
    suspend fun setBriefingTime(time: String) {
        context.dataStore.edit { it[BRIEFING_TIME] = time }
    }
}
