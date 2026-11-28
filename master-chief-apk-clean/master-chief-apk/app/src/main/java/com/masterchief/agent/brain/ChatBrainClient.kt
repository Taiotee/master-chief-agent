package com.masterchief.agent.brain

/**
 * One interface for every cloud brain. Gemini, OpenRouter, OpenAI, Claude
 * and any custom OpenAI-compatible endpoint all answer through this, so
 * the router, the 30-second timeout and the local fallback are identical
 * whichever provider the user picks.
 */
interface ChatBrainClient {
    /** Short label shown in the chat, e.g. "Gemini", "OpenRouter". */
    val label: String

    /** Ask one question. Result.failure carries a human-readable message
     *  (missing key, bad model id, quota, network) — never a silent hang. */
    suspend fun ask(systemPrompt: String, userText: String): Result<String>
}
