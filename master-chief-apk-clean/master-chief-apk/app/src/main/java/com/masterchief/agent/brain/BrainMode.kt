package com.masterchief.agent.brain

/**
 * The brain switcher.
 *
 * AUTO (default) — Chief decides per task (see BrainRouter).
 * LOCAL_ONLY   — never call the network. Works offline; limited to simple tasks.
 * GEMINI_FREE  — always use the free Gemini API tier (Google AI Studio key).
 * GEMINI_CHEAP — always use the cheap pay-as-you-go Gemini model.
 *
 * The user flips this in Settings, or by saying "Chief, switch to local"
 * / "Chief, switch to API". The choice is stored in DataStore and survives
 * a reboot.
 */
enum class BrainMode { AUTO, LOCAL_ONLY, GEMINI_FREE, GEMINI_CHEAP }

enum class BrainEngine { LOCAL, GEMINI_FREE, GEMINI_CHEAP }

data class BrainResult(
    val engine: BrainEngine,
    val text: String,
    /** Why this engine was chosen — shown in the chat so the user can trust it. */
    val reason: String,
    /** True when the request was escalated because local could not handle it. */
    val escalated: Boolean = false
)

/** How hard a task is. The router uses this, not a guess about the user. */
enum class TaskComplexity { SIMPLE, MEDIUM, HEAVY }

object TaskClassifier {
    private val heavyWords = listOf(
        "plan", "write", "draft", "summarise", "summarize", "explain", "compare",
        "research", "why", "how should", "decide", "advice", "email", "story",
        "translate", "code", "analyse", "analyze", "mood", "learn"
    )
    private val simpleWords = listOf(
        "time", "what time", "weather", "alarm", "timer", "open", "call",
        "turn on", "turn off", "brightness", "volume", "wifi", "clean",
        "battery", "date", "day is it"
    )

    fun classify(text: String): TaskComplexity {
        val t = text.lowercase()
        if (heavyWords.any { t.contains(it) }) return TaskComplexity.HEAVY
        if (simpleWords.any { t.contains(it) }) return TaskComplexity.SIMPLE
        return if (t.split(" ").size > 12) TaskComplexity.HEAVY else TaskComplexity.MEDIUM
    }
}
