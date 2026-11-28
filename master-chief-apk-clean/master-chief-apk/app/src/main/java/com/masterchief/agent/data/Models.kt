package com.masterchief.agent.data

import kotlinx.serialization.Serializable

/** What Master Chief has learned about Tayo. Stored locally, on-device only. */
@Serializable
data class SpartanProfile(
    val callsign: String = "Tayo",
    val likes: List<String> = emptyList(),
    val style: String = "",
    val musicTaste: String = "",
    val mood: String = "",
    val notes: String = ""
)

@Serializable
data class Mission(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAtEpochMs: Long = 0L
)

@Serializable
data class Protocol(
    val id: String,
    val name: String,
    val timeOfDay: String, // e.g. "22:00"
    val action: String,    // e.g. "Quiet hours: DND on, brightness low"
    val enabled: Boolean = true
)

enum class CleanupStatus { CANDIDATE, CONFIRMED, DONE, DISMISSED }

@Serializable
data class CleanupCandidate(
    val id: String,
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val reason: String, // "Exact duplicate", "Near-duplicate burst", "Large video"
    val status: String = CleanupStatus.CANDIDATE.name
)

@Serializable
data class ChatMessage(
    val id: String,
    val fromChief: Boolean,
    val text: String,
    val epochMs: Long = 0L
)

/** One entry in the command log — what Chief did, and with which brain. */
@Serializable
data class LogEntry(
    val epochMs: Long,
    val command: String,
    val brainUsed: String, // "LOCAL" | "GEMINI" | "OPENROUTER" | "OPENAI" | "CLAUDE" | "CUSTOM"
    val result: String
)
