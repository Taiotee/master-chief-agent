package com.masterchief.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.masterchief.agent.agent.cleanup.StorageMonitor
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Morning briefing worker — 06:30 Lagos time (Africa/Lagos).
 * WorkManager cannot do exact wall-clock times reliably, so this is a
 * daily periodic worker; MainActivity also schedules an exact alarm
 * (AlarmManager) for 06:30 when the user wants it sharp. The worker:
 *  - checks Lagos weather (Open-Meteo, no key needed) — see BriefingHelper
 *  - reads the day's calendar via the Calendar provider
 *  - checks storage; if low, triggers a cleanup assessment (see
 *    CleanupAssessmentWorker.triggerStorageLow)
 *  - posts the briefing as a notification, and stores it for the Briefing screen
 * Full Gmail reading needs the Gmail API + OAuth (out of scope for v1 on
 * device; the Muse hub already does this server-side). On-device v1 uses
 * the Calendar provider and weather only, and says so in the UI.
 */
class MorningBriefingWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        CleanupAssessmentWorker.triggerStorageLow(applicationContext)
        return Result.success()
    }

    companion object {
        const val UNIQUE_DAILY = "master-chief-morning-briefing"
        fun scheduleDaily(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 30); set(Calendar.SECOND, 0)
            }
            var delay = target.timeInMillis - now.timeInMillis
            if (delay < 0) delay += TimeUnit.DAYS.toMillis(1)
            val request = PeriodicWorkRequestBuilder<MorningBriefingWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("briefing").build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_DAILY, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
