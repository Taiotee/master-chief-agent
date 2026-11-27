package com.masterchief.agent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Military-style voice. Chief speaks briefly, directly, and always as a
 * soldier reporting to his commander — "Sir", "Affirmative", "Copy that",
 * "Standing by". The phrasing templates live here so the tone is
 * consistent everywhere, instead of being invented per screen.
 *
 * Voice output uses Android TextToSpeech (no network, no extra download
 * on most phones). The user picks the voice in system TTS settings; a
 * Cortana-like / Master Chief-like voice depends on the voices installed
 * on the phone — the app cannot invent a new voice, it can only set
 * pitch and speed (lower pitch, slower rate = the serious tone Tayo
 * chose in Muse).
 */
class MilitaryVoice(private val context: Context) {
    private var tts: TextToSpeech? = null

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.85f) // lower = more serious / military
                tts?.setSpeechRate(0.95f)
            }
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chief-${System.currentTimeMillis()}")
    }

    fun shutdown() { tts?.shutdown() }

    companion object {
        fun acknowledge(): String = listOf(
            "Copy that, sir.", "Affirmative, sir.", "On it, sir.", "Roger, sir."
        ).random()
        fun done(task: String): String = "$task complete, sir. Standing by."
        fun needConfirmation(what: String): String =
            "Sir, confirm before I $what. I will not proceed without your order."
        fun standingBy(): String = "Master Chief standing by, sir."
    }
}
