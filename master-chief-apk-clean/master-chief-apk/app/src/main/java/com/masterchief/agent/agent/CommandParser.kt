package com.masterchief.agent.agent

/** A parsed user order. The ViewModel decides: act on-device, or ask the brain. */
sealed class Command {
    data class OpenApp(val appName: String) : Command()
    data class Call(val who: String) : Command()
    data class Text(val who: String, val body: String) : Command()
    data class SetAlarm(val time: String) : Command()
    data class ToggleSetting(val setting: String, val on: Boolean) : Command()
    data class SetBrightness(val percent: Int) : Command()
    data class CleanPhotos(val confirm: Boolean = false) : Command()
    data class RefreshBriefing(val dummy: Boolean = false) : Command()
    data class SwitchBrain(val mode: String) : Command()
    data class PlayMusic(val query: String = "") : Command()
    data class Unknown(val raw: String) : Command()
}

object CommandParser {
    fun parse(text: String): Command {
        val t = text.trim().lowercase()
        return when {
            t.startsWith("open ") -> Command.OpenApp(text.trim().substringAfter("open ").trim())
            t.startsWith("call ") -> Command.Call(text.trim().substringAfter("call ").trim())
            t.startsWith("text ") || t.startsWith("message ") -> {
                val rest = text.trim().substringAfter(" ").trim()
                Command.Text(rest.substringBefore(":"), rest.substringAfter(":", ""))
            }
            t.contains("alarm") -> Command.SetAlarm(text)
            t.contains("turn on wifi") || t.contains("wifi on") -> Command.ToggleSetting("wifi", true)
            t.contains("turn off wifi") || t.contains("wifi off") -> Command.ToggleSetting("wifi", false)
            t.contains("do not disturb on") || t.contains("dnd on") -> Command.ToggleSetting("dnd", true)
            t.contains("do not disturb off") || t.contains("dnd off") -> Command.ToggleSetting("dnd", false)
            t.contains("brightness") -> {
                val pct = Regex("(\\d{1,3})").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 50
                Command.SetBrightness(pct.coerceIn(5, 100))
            }
            t.contains("clean") && t.contains("photo") -> Command.CleanPhotos()
            t.contains("briefing") || t.contains("my day") -> Command.RefreshBriefing()
            t.contains("switch to local") -> Command.SwitchBrain("LOCAL_ONLY")
            t.contains("switch to api") || t.contains("switch to gemini") -> Command.SwitchBrain("GEMINI_FREE")
            t.contains("play music") || t.startsWith("play ") -> Command.PlayMusic(text)
            else -> Command.Unknown(text)
        }
    }
}
