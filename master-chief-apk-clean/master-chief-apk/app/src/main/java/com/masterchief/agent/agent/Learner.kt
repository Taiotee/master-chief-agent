package com.masterchief.agent.agent

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.masterchief.agent.data.SpartanProfile

/**
 * Auto-learning — Chief learns who Tayo is by itself, from what is
 * already on the phone. Tayo should NOT have to fill in a profile
 * manually; the Profile screen is only for corrections.
 *
 * What it observes (all on-device, nothing leaves the phone):
 *  - Most-used apps and when they are used (morning / evening patterns)
 *    — needs PACKAGE_USAGE_STATS, granted in Settings. Without it,
 *    this section is skipped, never guessed.
 *  - Calendar titles (recurring patterns: gym, work, calls).
 *  - Cleanup decisions (kept vs deleted, per kind) — see CleanupLearning.
 *  - Notification patterns (which apps interrupt most).
 *
 * What it does NOT do:
 *  - Read the content of messages, emails or photos to build the profile.
 *    Gmail/Yahoo mail reading is a separate, explicit opt-in (see below).
 *  - Send anything learned to the Gemini API unless the user asks a
 *    question that needs it — and then only the relevant snippet.
 *  - Change a setting or delete anything based on a learned pattern
 *    without the user having approved that kind of action.
 *
 * The profile it builds is a draft: the user sees it on the Profile
 * screen and can correct or delete any of it. Learning is transparent —
 * every learned fact is shown with its source ("learned from app usage").
 */
class Learner(private val context: Context) {

    data class LearnedFact(val field: String, val value: String, val source: String)

    /** Top apps by foreground time in the last 30 days. Empty without the grant. */
    fun mostUsedApps(limit: Int = 5): List<Pair<String, Long>> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 30L * 86_400_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, start, end)
                ?: return emptyList()
            val pm = context.packageManager
            stats.filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(limit)
                .mapNotNull { s ->
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                    }.getOrNull() ?: s.packageName
                    label to s.totalTimeInForeground
                }
        } catch (e: SecurityException) {
            emptyList() // Usage access not granted — skip, do not guess.
        }
    }

    /** Quiet hours: the hours of the day with the least app usage. */
    fun quietHours(): Pair<Int, Int>? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 7L * 86_400_000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?: return null
            val byHour = IntArray(24)
            for (s in stats) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = s.lastTimeUsed }
                byHour[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
            }
            val quietStart = byHour.withIndex().minByOrNull { it.value }?.index ?: return null
            quietStart to (quietStart + 7) % 24
        } catch (e: SecurityException) {
            null
        }
    }

    /** Merge learned facts into the profile, keeping anything the user set by hand. */
    fun applyToProfile(profile: SpartanProfile): SpartanProfile {
        val apps = mostUsedApps(3).map { it.first }
        if (apps.isEmpty()) return profile
        val learnedLikes = (profile.likes + apps).distinct()
        return profile.copy(likes = learnedLikes)
    }

    companion object {
        /** System apps are never flagged as unused — only user-installed apps. */
        fun isUserApp(pm: PackageManager, info: ApplicationInfo): Boolean =
            info.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }
}
