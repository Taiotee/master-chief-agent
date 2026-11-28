package com.masterchief.agent.brain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Gemini API client — plain REST, no heavy Google AI SDK.
 *
 * Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 * Auth: ?key=<API key from Google AI Studio> — free tier by default.
 *
 * Free tier (AI Studio): gemini-2.0-flash, generous free requests/day, rate-limited.
 * Cheap pay-as-you-go: same model with billing on — a few pence per 1M tokens.
 * The key lives in Settings (DataStore), entered by the user. It is never
 * logged, never baked into the APK, and never sent anywhere except Google.
 *
 * If the key is missing, this client fails fast with a clear message and the
 * router falls back to the local brain instead of crashing.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash"
) {
    private val json = Json { ignoreUnknownKeys = true }
    // 30 seconds max: Chief must never hang silently the way the AI Studio
    // web preview did (50+ seconds, no answer, no error). The router also
    // enforces a 30s timeout and reports it clearly.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class Req(val contents: List<Content>)
    @Serializable
    data class Content(val parts: List<Part>)
    @Serializable
    data class Part(val text: String)
    @Serializable
    data class Resp(val candidates: List<Candidate> = emptyList(), val error: ApiError? = null)
    @Serializable
    data class Candidate(val content: Content? = null)
    @Serializable
    data class ApiError(val code: Int = 0, val message: String = "", val status: String = "")

    suspend fun ask(systemPrompt: String, userText: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException(
                "No Gemini API key. Add your free key from Google AI Studio in Settings > Brain."))
        }
        val body = Req(listOf(Content(listOf(
            Part("$systemPrompt\n\nUser: $userText\nChief:")
        ))))
        val payload = json.encodeToString(Req.serializer(), body)
            .toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder().url(url).post(payload).build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { json.decodeFromString<Resp>(raw).error }.getOrNull()
                    throw IllegalStateException(
                        "Gemini API ${resp.code}: ${err?.message ?: raw.take(200)}")
                }
                val parsed = json.decodeFromString<Resp>(raw)
                parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    ?: throw IllegalStateException("Gemini returned no answer.")
            }
        }
    }

    companion object {
        /** Master Chief system prompt — military, brief, no fluff. */
        const val SYSTEM_PROMPT = "You are Master Chief, a UNSC Spartan AI agent on " +
            "Tayo's Android phone. Speak like a soldier: brief, direct, military style. " +
            "Acknowledge orders, report status, confirm before deleting, sending or paying " +
            "for anything. Never invent facts about the phone, messages or calendar — " +
            "say when you do not know. Keep answers short unless the user asks for detail."
    }
}
