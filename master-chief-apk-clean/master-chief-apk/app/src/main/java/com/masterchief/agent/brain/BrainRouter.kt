package com.masterchief.agent.brain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.masterchief.agent.data.SpartanProfile

/**
 * The auto-switcher. Decides, per request, which brain answers.
 *
 * Rules (in order):
 *  1. User override wins: LOCAL_ONLY / GEMINI_FREE / GEMINI_CHEAP.
 *  2. No network (or no API key) -> LOCAL. Never fail a simple task for
 *     want of a connection.
 *  3. SIMPLE task -> LOCAL. Fast, free, private, no API cost.
 *  4. MEDIUM task -> LOCAL if a local LLM model is installed, else Gemini Free.
 *  5. HEAVY task -> Gemini (free tier first; cheap tier if the user picked it
 *     in Settings, or if the free tier returns a quota error — see
 *     GeminiClient error handling in ChiefViewModel).
 *  6. If Gemini fails (quota / network / key), fall back to LOCAL and say so.
 *
 * The chosen engine + reason are shown in the chat, so the switch is
 * visible, not magic.
 */
class BrainRouter(
    private val context: Context,
    private val localBrain: LocalBrain,
    private val localLlm: LocalLlm,
    private val geminiFactory: (model: String) -> GeminiClient?
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
        geminiModel: String,
        preferCheap: Boolean = false
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
        if (mode == BrainMode.GEMINI_FREE || mode == BrainMode.GEMINI_CHEAP) {
            val engine = if (mode == BrainMode.GEMINI_CHEAP) BrainEngine.GEMINI_CHEAP else BrainEngine.GEMINI_FREE
            val client = geminiFactory(geminiModel)
                ?: return local(
                    "No Gemini API key set, sir. Add one in Settings > Brain, or switch to Local.",
                    "API requested but no key.", escalated = true)
            return geminiAsk(client, engine, text, "Brain mode: ${mode.name}.")
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

        // 4. Medium -> local LLM if installed, else Gemini Free.
        if (complexity == TaskComplexity.MEDIUM && localLlm.isAvailable()) {
            val ans = localLlm.ask(text)
            if (ans != null) return local(ans, "Medium task — local model on-device.")
        }

        // 5. Heavy / remaining -> Gemini.
        val client = geminiFactory(geminiModel)
            ?: return local(
                localBrain.answer(text, profile)
                    ?: "No Gemini API key, sir. Add your free key in Settings > Brain.",
                "No API key — local fallback.", escalated = true)
        val engine = if (preferCheap) BrainEngine.GEMINI_CHEAP else BrainEngine.GEMINI_FREE
        return geminiAsk(client, engine, text,
            if (complexity == TaskComplexity.HEAVY) "Heavy task — API brain."
            else "Local could not answer — escalated to API.")
    }

    private suspend fun geminiAsk(
        client: GeminiClient, engine: BrainEngine, text: String, reason: String
    ): BrainResult {
        val result = client.ask(GeminiClient.SYSTEM_PROMPT, text)
        return result.fold(
            onSuccess = { BrainResult(engine, it, reason) },
            onFailure = { e ->
                BrainResult(BrainEngine.LOCAL,
                    "API unavailable (${e.message}). Local brain standing by, sir — " +
                        "simple orders still work offline.",
                    "API failed — local fallback.", escalated = true)
            }
        )
    }
}
