package com.masterchief.agent.brain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.masterchief.agent.data.SpartanProfile
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * The auto-switcher. Decides, per request, which brain answers.
 *
 * Rules (in order):
 *  1. User override wins: LOCAL_ONLY, or one fixed provider (GEMINI /
 *     OPENROUTER / OPENAI / CLAUDE / CUSTOM). A fixed provider with no key
 *     (or no model / endpoint) answers with a clear message saying what is
 *     missing and where to add it — never a silent hang.
 *  2. No network (or no API key) -> LOCAL. Never fail a simple task for
 *     want of a connection.
 *  3. SIMPLE task -> LOCAL. Fast, free, private, no API cost.
 *  4. MEDIUM task -> LOCAL if a local LLM model is installed, else the
 *     first ready cloud provider.
 *  5. HEAVY task -> first ready cloud provider (AUTO order: OpenRouter,
 *     Gemini, OpenAI, Claude, Custom).
 *  6. If the cloud provider fails (quota / network / key / timeout), fall
 *     back to LOCAL and say so.
 *
 * The chosen engine + reason are shown in the chat, so the switch is
 * visible, not magic.
 */
class BrainRouter(
    private val context: Context,
    private val localBrain: LocalBrain,
    private val localLlm: LocalLlm,
    private val clientFactory: (BrainProvider) -> ChatBrainClient?
) {
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun route(
        mode: BrainMode,
        text: String,
        profile: SpartanProfile,
        config: BrainConfig
    ): BrainResult {
        val complexity = TaskClassifier.classify(text)
        val online = isOnline()

        fun local(text: String, reason: String, escalated: Boolean = false) =
            BrainResult(BrainEngine.LOCAL, text, reason, escalated)

        // 1. Explicit user override.
        if (mode == BrainMode.LOCAL_ONLY) {
            val ans = localBrain.answer(text, profile)
                ?: localLlm.ask(text)
                ?: "Local brain only, sir. That task needs the API — switch brain mode to use it."
            return local(ans, "Brain mode: Local only.")
        }
        val provider = mode.provider()
        if (provider != null) {
            val client = clientFactory(provider)
                ?: return local(
                    missingSetupMessage(provider, config),
                    "API requested but not set up.", escalated = true)
            return apiAsk(
                client, BrainEngine.valueOf(provider.name), text,
                "Brain mode: ${mode.name} (${provider.label}).")
        }

        // 2. Offline -> local, always.
        if (!online) {
            val ans = localBrain.answer(text, profile)
                ?: localLlm.ask(text)
                ?: "Offline, sir. Local brain can handle simple orders only — " +
                    "time, alarms, settings, opening apps. Reconnect for heavier thinking."
            return local(ans, "Offline — local brain.", escalated = true)
        }

        // 3. Simple -> local.
        if (complexity == TaskComplexity.SIMPLE) {
            val ans = localBrain.answer(text, profile)
            if (ans != null) return local(ans, "Simple task — handled on-device, no API cost.")
        }

        // 4. Medium -> local LLM if installed, else first ready provider.
        if (complexity == TaskComplexity.MEDIUM && localLlm.isAvailable()) {
            val ans = localLlm.ask(text)
            if (ans != null) return local(ans, "Medium task — local model on-device.")
        }

        // 5. Heavy / remaining -> first ready cloud provider.
        val ready = config.firstReadyProvider()
            ?: return local(
                localBrain.answer(text, profile)
                    ?: "No API key set, sir. Add a key in Settings > Brain — " +
                        "Gemini, OpenRouter, OpenAI or Claude.",
                "No API key — local fallback.", escalated = true)
        val client = clientFactory(ready)
            ?: return local(
                localBrain.answer(text, profile)
                    ?: "No API key set, sir. Add one in Settings > Brain.",
                "No API key — local fallback.", escalated = true)
        return apiAsk(
            client, BrainEngine.valueOf(ready.name), text,
            if (complexity == TaskComplexity.HEAVY) "Heavy task — ${ready.label} brain."
            else "Local could not answer — escalated to ${ready.label}.")
    }

    /** Plain-English answer for a provider the user picked but has not set up yet. */
    private fun missingSetupMessage(provider: BrainProvider, config: BrainConfig): String = when {
        config.keyFor(provider).isBlank() ->
            "No ${provider.label} API key set, sir. Add it in Settings > Brain, " +
                "or switch to Local. ${provider.hint}"
        provider == BrainProvider.CUSTOM && config.customEndpoint.isBlank() ->
            "No custom endpoint set, sir. Enter your server URL in Settings > Brain."
        config.modelFor(provider).isBlank() ->
            "No ${provider.label} model set, sir. Pick a model id in Settings > Brain."
        else ->
            "${provider.label} is not set up, sir. Check Settings > Brain."
    }

    private suspend fun apiAsk(
        client: ChatBrainClient, engine: BrainEngine, text: String, reason: String
    ): BrainResult {
        // Hard 30-second limit: never hang silently. On timeout or any API
        // failure, say so plainly and fall back to the local brain.
        return try {
            val answer = withTimeout(30_000) {
                client.ask(GeminiClient.SYSTEM_PROMPT, text).getOrThrow()
            }
            BrainResult(engine, answer, reason)
        } catch (e: TimeoutCancellationException) {
            BrainResult(BrainEngine.LOCAL,
                "${client.label} did not answer in 30 seconds, sir — it may be busy or " +
                    "out of quota. Local brain standing by; simple orders still work.",
                "API timed out — local fallback.", escalated = true)
        } catch (e: Exception) {
            BrainResult(BrainEngine.LOCAL,
                "${client.label} unavailable (${e.message}). Local brain standing by, sir — " +
                    "simple orders still work offline.",
                "API failed — local fallback.", escalated = true)
        }
    }
}
