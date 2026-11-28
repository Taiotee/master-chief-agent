package com.masterchief.agent.brain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * One client for every OpenAI-compatible /v1/chat/completions endpoint:
 * OpenRouter, OpenAI direct, and any custom provider the user enters in
 * Settings. Only the base URL, key and model differ — the request shape,
 * the 30-second timeout and the error handling are shared.
 *
 * OpenRouter: base https://openrouter.ai/api/v1 — one key unlocks many
 * models (OpenAI, Anthropic, Meta, Google, …) by model id.
 * OpenAI direct: base https://api.openai.com/v1.
 * Custom: any server that speaks the same API — the user enters its base
 * URL (the part before /chat/completions), key and model id.
 *
 * The key lives in Settings (DataStore), entered by the user. It is never
 * logged, never baked into the APK, and never sent anywhere except the
 * provider's own endpoint.
 */
class OpenAiCompatibleClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    override val label: String,
    private val extraHeaders: Map<String, String> = emptyMap()
) : ChatBrainClient {
    private val json = Json { ignoreUnknownKeys = true }
    // 30 seconds max, matching the router's hard timeout: Chief must never
    // hang silently the way the AI Studio web preview did.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class Req(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double = 0.7,
        val max_tokens: Int = 1024
    )

    @Serializable
    data class Msg(val role: String, val content: String)

    @Serializable
    data class Resp(val choices: List<Choice> = emptyList(), val error: ApiError? = null)

    @Serializable
    data class Choice(val message: Msg? = null)

    @Serializable
    data class ApiError(val message: String = "", val code: String? = null, val type: String? = null)

    override suspend fun ask(systemPrompt: String, userText: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException(
                "No $label API key. Add it in Settings > Brain."))
        }
        if (model.isBlank()) {
            return Result.failure(IllegalStateException(
                "No $label model set. Pick a model id in Settings > Brain."))
        }
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException(
                "No $label endpoint set. Enter the server URL in Settings > Brain."))
        }
        val body = Req(model, listOf(Msg("system", systemPrompt), Msg("user", userText)))
        val payload = json.encodeToString(Req.serializer(), body)
            .toRequestBody("application/json".toMediaType())
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val reqBuilder = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(payload)
        extraHeaders.forEach { (name, value) -> reqBuilder.header(name, value) }
        return runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { json.decodeFromString<Resp>(raw).error }.getOrNull()
                    throw IllegalStateException(
                        "$label API ${resp.code}: ${err?.message?.take(200) ?: raw.take(200)}")
                }
                val parsed = json.decodeFromString<Resp>(raw)
                parsed.choices.firstOrNull()?.message?.content?.trim()
                    ?: throw IllegalStateException("$label returned no answer.")
            }
        }
    }

    companion object {
        const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"

        /** OpenRouter asks apps to identify themselves; these two headers do that. */
        val OPENROUTER_HEADERS = mapOf(
            "HTTP-Referer" to "https://github.com/Taiotee/master-chief-agent",
            "X-Title" to "Master Chief"
        )
    }
}
