package com.masterchief.agent.brain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * "Get model list" — fetches the provider's real model catalogue so the
 * user can tap a model instead of typing an id from memory.
 *
 * OpenRouter / OpenAI / Custom: GET {baseUrl}/models, Bearer key,
 *   response {"data": [{"id": "..."}]}.
 * Gemini: GET https://generativelanguage.googleapis.com/v1beta/models?key=…,
 *   response {"models": [{"name": "models/gemini-2.0-flash"}]} — the
 *   "models/" prefix is stripped.
 * Claude: GET https://api.anthropic.com/v1/models, x-api-key header,
 *   response {"data": [{"id": "claude-…"}]}.
 *
 * Every failure is a plain-English Result.failure: no key, no network,
 * wrong key (401), unknown model list. The caller shows the message; the
 * list is never faked from a hard-coded catalogue.
 */
object ModelListClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class OpenAiListResp(val data: List<ModelEntry> = emptyList())

    @Serializable
    data class ModelEntry(val id: String = "")

    @Serializable
    data class GeminiListResp(val models: List<GeminiModel> = emptyList())

    @Serializable
    data class GeminiModel(val name: String = "")

    @Serializable
    data class ApiErrorBody(val error: ApiError? = null)

    @Serializable
    data class ApiError(val message: String = "")

    suspend fun fetch(provider: BrainProvider, config: BrainConfig): Result<List<String>> {
        val key = config.keyFor(provider)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException(
                "No ${provider.label} API key saved — add it first, then tap Get model list."))
        }
        val request: Request = when (provider) {
            BrainProvider.GEMINI -> Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key&pageSize=100")
                .get()
                .build()
            BrainProvider.CLAUDE -> Request.Builder()
                .url("https://api.anthropic.com/v1/models")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .get()
                .build()
            BrainProvider.OPENROUTER -> Request.Builder()
                .url(config.openRouterEndpoint.trimEnd('/') + "/models")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
            BrainProvider.OPENAI -> Request.Builder()
                .url(config.openAiEndpoint.trimEnd('/') + "/models")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
            BrainProvider.CUSTOM -> {
                if (config.customEndpoint.isBlank()) {
                    return Result.failure(IllegalStateException(
                        "No custom endpoint set — enter the server URL first."))
                }
                Request.Builder()
                    .url(config.customEndpoint.trimEnd('/') + "/models")
                    .header("Authorization", "Bearer $key")
                    .get()
                    .build()
            }
        }
        return runCatching {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { json.decodeFromString<ApiErrorBody>(raw).error }.getOrNull()
                    val detail = err?.message?.take(200) ?: raw.take(200)
                    throw IllegalStateException(
                        "${provider.label} model list failed (${resp.code}): $detail")
                }
                val ids: List<String> = if (provider == BrainProvider.GEMINI) {
                    json.decodeFromString<GeminiListResp>(raw).models
                        .map { it.name.removePrefix("models/") }
                } else {
                    json.decodeFromString<OpenAiListResp>(raw).data.map { it.id }
                }
                val clean = ids.filter { it.isNotBlank() }.distinct().sorted()
                if (clean.isEmpty()) {
                    throw IllegalStateException("${provider.label} returned an empty model list.")
                }
                clean
            }
        }
    }
}
