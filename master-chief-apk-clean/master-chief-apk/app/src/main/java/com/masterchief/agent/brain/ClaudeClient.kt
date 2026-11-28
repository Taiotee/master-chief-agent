package com.masterchief.agent.brain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude, direct — the Messages API, which is NOT OpenAI-shaped,
 * so it gets its own small client behind the same ChatBrainClient interface.
 *
 * Endpoint: POST https://api.anthropic.com/v1/messages
 * Auth: x-api-key header. Version header: anthropic-version 2023-06-01.
 *
 * The key lives in Settings (DataStore), entered by the user. It is never
 * logged, never baked into the APK, and never sent anywhere except Anthropic.
 */
class ClaudeClient(
    private val apiKey: String,
    private val model: String = BrainConfig.DEFAULT_CLAUDE_MODEL
) : ChatBrainClient {
    override val label: String = "Claude"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class Req(
        val model: String,
        val max_tokens: Int = 1024,
        val system: String,
        val messages: List<Msg>
    )

    @Serializable
    data class Msg(val role: String, val content: String)

    @Serializable
    data class Resp(val content: List<Block> = emptyList(), val error: ApiError? = null)

    @Serializable
    data class Block(val type: String = "", val text: String = "")

    @Serializable
    data class ApiError(val type: String = "", val message: String = "")

    override suspend fun ask(systemPrompt: String, userText: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException(
                "No Claude API key. Add it in Settings > Brain."))
        }
        if (model.isBlank()) {
            return Result.failure(IllegalStateException(
                "No Claude model set. Pick a model id in Settings > Brain."))
        }
        val body = Req(model, 1024, systemPrompt, listOf(Msg("user", userText)))
        val payload = json.encodeToString(Req.serializer(), body)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload)
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { json.decodeFromString<Resp>(raw).error }.getOrNull()
                    throw IllegalStateException(
                        "Claude API ${resp.code}: ${err?.message?.take(200) ?: raw.take(200)}")
                }
                val parsed = json.decodeFromString<Resp>(raw)
                parsed.content.firstOrNull { it.type == "text" }?.text?.trim()
                    ?: throw IllegalStateException("Claude returned no answer.")
            }
        }
    }
}
