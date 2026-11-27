package com.masterchief.agent.brain

import android.content.Context
import java.io.File

/**
 * Optional on-device LLM layer.
 *
 * This is a deliberately honest stub with a real integration point, not a
 * fake "local AI" claim:
 *
 * - On the OPPO A74 5G (6 GB RAM) a large model cannot run. Only small
 *   quantised models (~0.5B–2B parameters, .task format, 0.5–1.5 GB) are
 *   realistic, and even those are slow.
 * - To enable it: download a small model (e.g. Gemma-2B-it or Qwen2.5-0.5B
 *   in MediaPipe .task format) into the app's files dir as `local_model.task`,
 *   then uncomment the MediaPipe dependency in app/build.gradle.kts and
 *   wire LlmInference here (see the commented skeleton below).
 * - Until then, isAvailable() is false and the router never routes to it.
 *
 * The rule-based LocalBrain (LocalBrain.kt) is what actually answers simple
 * tasks offline today. Do not present this class as a working LLM.
 */
class LocalLlm(private val context: Context) {

    private val modelFile: File get() = File(context.filesDir, "local_model.task")

    fun isAvailable(): Boolean = modelFile.exists() && modelFile.length() > 100_000_000

    fun modelInfo(): String =
        if (isAvailable()) "Local model present: ${modelFile.name} (${modelFile.length() / 1_000_000} MB)"
        else "No local LLM model installed. Place a small .task model as local_model.task " +
            "in the app files dir, or use the API brain. See README § Local AI."

    /** Returns null when no model is installed — the router then escalates. */
    fun ask(prompt: String): String? {
        if (!isAvailable()) return null
        // TODO: wire MediaPipe LlmInference here once the dependency is enabled:
        //   val options = LlmInference.LlmInferenceOptions.builder()
        //       .setModelPath(modelFile.absolutePath)
        //       .setMaxTokens(512)
        //       .build()
        //   LlmInference.createFromOptions(context, options).use { it.generateResponse(prompt) }
        return null
    }
}
