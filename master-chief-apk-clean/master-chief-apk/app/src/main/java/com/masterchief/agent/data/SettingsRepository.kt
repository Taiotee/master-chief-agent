package com.masterchief.agent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.masterchief.agent.brain.BrainMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chief_settings")

/**
 * App settings, including the brain switcher. The Gemini API key is stored
 * here (DataStore, app-private) — never in source code, never in git.
 * A production build should move the key to EncryptedSharedPreferences;
 * see README § Security.
 */
class SettingsRepository(private val context: Context) {
    companion object {
        private val BRAIN_MODE = stringPreferencesKey("brain_mode")
        private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
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
        it[GEMINI_MODEL] ?: "gemini-2.0-flash"
    }
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
