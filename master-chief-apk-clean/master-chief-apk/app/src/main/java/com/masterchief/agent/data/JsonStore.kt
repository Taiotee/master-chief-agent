package com.masterchief.agent.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tiny JSON file store. No Room, no database migration risk, and the data
 * stays in the app's private files dir — nothing leaves the phone unless
 * the user sends it to the Gemini API in a chat message.
 */
class JsonStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    inline fun <reified T> load(fileName: String, default: T): T {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return default
        return runCatching { json.decodeFromString<T>(file.readText()) }.getOrDefault(default)
    }

    inline fun <reified T> save(fileName: String, value: T) {
        File(context.filesDir, fileName).writeText(json.encodeToString(value))
    }

    fun loadProfile(): SpartanProfile = load("profile.json", SpartanProfile())
    fun saveProfile(p: SpartanProfile) = save("profile.json", p)

    fun loadMissions(): List<Mission> = load("missions.json", emptyList())
    fun saveMissions(m: List<Mission>) = save("missions.json", m)

    fun loadProtocols(): List<Protocol> = load("protocols.json", defaultProtocols())
    fun saveProtocols(p: List<Protocol>) = save("protocols.json", p)

    fun loadCleanup(): List<CleanupCandidate> = load("cleanup.json", emptyList())
    fun saveCleanup(c: List<CleanupCandidate>) = save("cleanup.json", c)

    fun loadChat(): List<ChatMessage> = load("chat.json", emptyList())
    fun saveChat(c: List<ChatMessage>) = save("chat.json", c.takeLast(200))

    // ---- Intelligent cleanup: learning, assessments, reports ------------
    fun loadCleanupLearning(): com.masterchief.agent.agent.cleanup.CleanupLearning =
        load("cleanup_learning.json", com.masterchief.agent.agent.cleanup.CleanupLearning())
    fun saveCleanupLearning(l: com.masterchief.agent.agent.cleanup.CleanupLearning) =
        save("cleanup_learning.json", l)

    /** Record a keep/delete decision so future scores learn from it. */
    fun recordCleanupDecision(kind: String, deleted: Boolean) {
        val cur = loadCleanupLearning()
        val kept = cur.keptByKind.toMutableMap()
        val del = cur.deletedByKind.toMutableMap()
        if (deleted) del[kind] = (del[kind] ?: 0) + 1 else kept[kind] = (kept[kind] ?: 0) + 1
        saveCleanupLearning(cur.copy(keptByKind = kept, deletedByKind = del))
    }

    fun loadLastAssessment(): com.masterchief.agent.agent.cleanup.CleanupAssessment? =
        load("cleanup_assessment.json", null as com.masterchief.agent.agent.cleanup.CleanupAssessment?)
    fun saveLastAssessment(a: com.masterchief.agent.agent.cleanup.CleanupAssessment) =
        save("cleanup_assessment.json", a)

    fun loadCleanupReports(): List<com.masterchief.agent.agent.cleanup.CleanupReport> =
        load("cleanup_reports.json", emptyList())
    fun saveCleanupReport(r: com.masterchief.agent.agent.cleanup.CleanupReport) =
        save("cleanup_reports.json", (listOf(r) + loadCleanupReports()).take(20))

    fun loadCleanupPermission(): String = load("cleanup_permission.json", "AUTO_SAFE_ONLY")
    fun saveCleanupPermission(p: String) = save("cleanup_permission.json", p)

    private fun defaultProtocols() = listOf(
        Protocol("p-morning", "Morning briefing", "06:30",
            "Briefing: weather for Lagos, today's plan, priority mail"),
        Protocol("p-night", "Night watch", "22:00",
            "Quiet hours: Do Not Disturb on, brightness low, Wi-Fi stays on")
    )
}
