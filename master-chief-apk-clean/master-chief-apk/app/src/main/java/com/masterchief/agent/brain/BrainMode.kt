package com.masterchief.agent.brain

/**
 * The brain switcher.
 *
 * AUTO (default) — Chief decides per task (see BrainRouter). For cloud
 * thinking it uses the first provider that has a key set, in this order:
 * OpenRouter, Gemini, OpenAI, Claude, Custom endpoint.
 * LOCAL_ONLY — never call the network. Works offline; limited to simple tasks.
 * GEMINI     — always use Google Gemini directly (Google AI Studio key).
 * OPENROUTER — always use OpenRouter (one key, many models).
 * OPENAI     — always use OpenAI directly.
 * CLAUDE     — always use Anthropic Claude directly.
 * CUSTOM     — always use the custom OpenAI-compatible endpoint in Settings.
 *
 * The user flips this in Settings, or by saying "Chief, switch to local" /
 * "Chief, switch to OpenRouter". The choice is stored in DataStore and
 * survives a reboot. An old stored mode that no longer exists (e.g. the
 * retired GEMINI_FREE / GEMINI_CHEAP) falls back to AUTO.
 */
enum class BrainMode { AUTO, LOCAL_ONLY, GEMINI, OPENROUTER, OPENAI, CLAUDE, CUSTOM }

enum class BrainEngine { LOCAL, GEMINI, OPENROUTER, OPENAI, CLAUDE, CUSTOM }

/** One cloud brain provider. All keys live in DataStore (app-private),
 *  entered by the user in Settings > Brain — never in code, never in git. */
enum class BrainProvider(val label: String, val hint: String) {
    GEMINI(
        "Gemini",
        "Google AI Studio key — free at aistudio.google.com. Default model gemini-2.0-flash."
    ),
    OPENROUTER(
        "OpenRouter",
        "PROVEN — the one that works in your Agent Workspace. One key, many " +
            "models — get it at openrouter.ai/keys. Default model openai/gpt-6.1-terra, " +
            "the model already working there (440 models listed). Free models also " +
            "work, e.g. meta-llama/llama-3.3-70b-instruct:free — just paste the model id."
    ),
    OPENAI(
        "OpenAI",
        "Direct OpenAI key — platform.openai.com/api-keys. Default model gpt-4o-mini."
    ),
    CLAUDE(
        "Claude",
        "Anthropic key — console.anthropic.com. Default model claude-sonnet-4-20250514."
    ),
    CUSTOM(
        "Custom endpoint",
        "Any OpenAI-compatible /v1/chat/completions server. Enter its base URL " +
            "(e.g. https://your-provider.com/v1), your key and the model id."
    )
}

/** Which provider a fixed brain mode uses, or null for AUTO / LOCAL_ONLY. */
fun BrainMode.provider(): BrainProvider? = when (this) {
    BrainMode.GEMINI -> BrainProvider.GEMINI
    BrainMode.OPENROUTER -> BrainProvider.OPENROUTER
    BrainMode.OPENAI -> BrainProvider.OPENAI
    BrainMode.CLAUDE -> BrainProvider.CLAUDE
    BrainMode.CUSTOM -> BrainProvider.CUSTOM
    else -> null
}

/**
 * Reasoning effort — how hard the cloud model thinks before answering.
 * AUTO means the provider decides (the field is not sent at all).
 * Only OpenRouter and OpenAI use it: OpenRouter gets reasoning.effort,
 * OpenAI gets reasoning_effort. Gemini, Claude and Local do not use this
 * setting, so the selector is hidden for them instead of being a dud.
 */
enum class ReasoningEffort { AUTO, LOW, MEDIUM, HIGH }

/** Everything the router needs to reach the cloud brains. Built in the
 *  ViewModel from SettingsRepository at call time, so a key added in
 *  Settings takes effect on the very next message. */
data class BrainConfig(
    val geminiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val openRouterKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    val openRouterEndpoint: String = DEFAULT_OPENROUTER_ENDPOINT,
    val openAiKey: String = "",
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    val openAiEndpoint: String = DEFAULT_OPENAI_ENDPOINT,
    val claudeKey: String = "",
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,
    val customKey: String = "",
    val customModel: String = "",
    val customEndpoint: String = "",
    val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
        const val DEFAULT_OPENROUTER_MODEL = "openai/gpt-6.1-terra"
        const val DEFAULT_OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-20250514"
    }

    fun keyFor(p: BrainProvider): String = when (p) {
        BrainProvider.GEMINI -> geminiKey
        BrainProvider.OPENROUTER -> openRouterKey
        BrainProvider.OPENAI -> openAiKey
        BrainProvider.CLAUDE -> claudeKey
        BrainProvider.CUSTOM -> customKey
    }

    fun modelFor(p: BrainProvider): String = when (p) {
        BrainProvider.GEMINI -> geminiModel
        BrainProvider.OPENROUTER -> openRouterModel
        BrainProvider.OPENAI -> openAiModel
        BrainProvider.CLAUDE -> claudeModel
        BrainProvider.CUSTOM -> customModel
    }

    /** Ready means: key set, model set, and (for Custom) an endpoint set. */
    fun ready(p: BrainProvider): Boolean =
        keyFor(p).isNotBlank() && modelFor(p).isNotBlank() &&
            (p != BrainProvider.CUSTOM || customEndpoint.isNotBlank())

    /** First ready provider, in AUTO priority order. OpenRouter first: one
     *  key unlocks many models, so it is the main multi-model option. */
    fun firstReadyProvider(): BrainProvider? = listOf(
        BrainProvider.OPENROUTER,
        BrainProvider.GEMINI,
        BrainProvider.OPENAI,
        BrainProvider.CLAUDE,
        BrainProvider.CUSTOM
    ).firstOrNull { ready(it) }

    fun anyKeySet(): Boolean =
        BrainProvider.entries.any { keyFor(it).isNotBlank() }
}

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
