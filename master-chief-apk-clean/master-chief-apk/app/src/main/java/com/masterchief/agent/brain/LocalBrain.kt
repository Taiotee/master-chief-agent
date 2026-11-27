package com.masterchief.agent.brain

import android.content.Context
import android.os.BatteryManager
import com.masterchief.agent.data.SpartanProfile

/**
 * The LOCAL brain — runs entirely on the phone, no network, no cost.
 *
 * Honest design note (OPPO A74 5G: Snapdragon 480, 6 GB RAM):
 * a full LLM does NOT fit comfortably here. A 7B model needs ~4–8 GB and
 * would be slow, hot and battery-hungry. So the local brain is two layers:
 *
 * 1. RuleBrain (this file, always available): instant, offline handling of
 *    simple tasks — time/date, weather cache, alarms, settings, opening apps,
 *    cleaning, and short factual answers from the on-device profile.
 *    This is what AUTO mode uses for SIMPLE tasks.
 * 2. LocalLlm (see LocalLlm.kt): an optional small quantised model
 *    (e.g. Qwen2.5-0.5B / Gemma-2B, ~0.5–1.5 GB, .task format) loaded with
 *    MediaPipe LLM Inference when the user has downloaded one. If no model
 *    file is present, LocalLlm reports unavailable and the router escalates
 *    MEDIUM/HEAVY tasks to Gemini instead of pretending.
 *
 * Local is therefore a fast fallback for simple/offline work — not a
 * replacement for the API on heavy thinking. That is the whole point of
 * the auto-switcher.
 */
class LocalBrain(private val context: Context) {

    fun answer(text: String, profile: SpartanProfile): String? {
        val t = text.lowercase().trim()
        return when {
            t.contains("what time") || t == "time" -> {
                val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                "Time is $now hours, sir. Lagos time."
            }
            t.contains("what day") || t.contains("what date") -> {
                val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date())
                "Today is $now, sir."
            }
            t.contains("battery") -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                "Battery at $pct percent, sir."
            }
            t.contains("who am i") || t.contains("my name") ->
                "You are ${profile.callsign}, sir. Spartan profile on file."
            t.contains("my mood") ->
                if (profile.mood.isBlank()) "No mood logged yet, sir. Tell me how you feel and I will remember it."
                else "Your logged mood is: ${profile.mood}, sir."
            t.contains("my music") || t.contains("music taste") ->
                if (profile.musicTaste.isBlank()) "No music taste logged yet, sir."
                else "Your music: ${profile.musicTaste}, sir."
            t.contains("status") || t.contains("report") ->
                "All systems nominal, sir. Local brain online. Awaiting orders."
            else -> null // Not a simple local task — router escalates.
        }
    }
}
