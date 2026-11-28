package com.masterchief.agent.brain

import android.content.Context
import java.io.File

/**
 * What is actually on the phone for on-device AI — name, path, size.
 * `meetsMinSize` is false for files too small to be a real model.
 */
data class LocalModelInfo(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val meetsMinSize: Boolean
)

/**
 * Optional on-device LLM layer — file staging only, for now.
 *
 * HONEST STATE (OPPO A74 5G, 6 GB RAM, CPU-only):
 * - The MediaPipe LLM Inference engine is NOT wired in this build
 *   (ENGINE_WIRED = false). ask() always returns null, and the router
 *   treats that as "no local LLM" and escalates to the cloud brain.
 *   Do NOT present this as a working LLM.
 * - What DOES work: staging a model file. Settings > Brain > Local Model
 *   lets the user pick a .task file already on the phone and copies it to
 *   the app files dir as local_model.task. isAvailable() / modelInfo()
 *   report it honestly, and the file will be ready the day the engine is
 *   wired (MediaPipe dependency + LlmInference, see the skeleton in ask()).
 * - Only small quantised models are realistic here (~0.5B–2B parameters,
 *   .task format, 0.5–2.5 GB), and even those will be slow on this phone.
 *   Verified .task files: Qwen2.5-0.5B-Instruct q8 (~547 MB, free) and
 *   Gemma-2B-IT q8 (~2 GB, gated licence) from litert-community on
 *   Hugging Face. MediaPipe LLM Inference itself is now maintenance-only;
 *   Google's current stack is LiteRT-LM, which the next engine build
 *   should target.
 *
 * The rule-based LocalBrain (LocalBrain.kt) is what actually answers simple
 * tasks offline today. That is the "Local" that works.
 */
class LocalLlm(private val context: Context) {

    companion object {
        const val MODEL_FILE_NAME = "local_model.task"
        const val MIN_MODEL_BYTES = 100_000_000L

        /** Flip to true ONLY when the MediaPipe/LiteRT engine is enabled and
         *  ask() really generates answers. Until then it stays false. */
        const val ENGINE_WIRED = false
    }

    private val modelFile: File get() = File(context.filesDir, MODEL_FILE_NAME)

    fun isAvailable(): Boolean = modelFile.exists() && modelFile.length() > MIN_MODEL_BYTES

    /** Info about the staged file, or null when nothing is staged. */
    fun modelInfo(): LocalModelInfo? {
        if (!modelFile.exists()) return null
        val size = modelFile.length()
        return LocalModelInfo(
            fileName = modelFile.name,
            path = modelFile.absolutePath,
            sizeBytes = size,
            meetsMinSize = size > MIN_MODEL_BYTES
        )
    }

    /** Where a model file must land: app files dir / local_model.task. */
    fun expectedPath(): String = modelFile.absolutePath

    fun deleteModel(): Boolean {
        if (!modelFile.exists()) return false
        return runCatching { modelFile.delete() }.getOrDefault(false)
    }

    /** Returns null when no model is installed — the router then escalates.
     *  Also returns null when the engine is not wired, even if a file is
     *  staged: a staged file is not a working LLM. */
    fun ask(prompt: String): String? {
        if (!isAvailable()) return null
        if (!ENGINE_WIRED) return null
        // TODO: wire MediaPipe LlmInference here once the dependency is enabled:
        //   val options = LlmInference.LlmInferenceOptions.builder()
        //       .setModelPath(modelFile.absolutePath)
        //       .setMaxTokens(512)
        //       .build()
        //   LlmInference.createFromOptions(context, options).use { it.generateResponse(prompt) }
        return null
    }
}
