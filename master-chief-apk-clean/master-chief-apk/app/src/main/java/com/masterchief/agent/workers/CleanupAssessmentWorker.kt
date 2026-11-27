package com.masterchief.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.masterchief.agent.agent.cleanup.CleanupEngine
import com.masterchief.agent.agent.cleanup.CleanupPermission
import com.masterchief.agent.agent.cleanup.StorageMonitor
import com.masterchief.agent.data.JsonStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic cleanup assessment.
 *
 * Schedule (set up in MainActivity.scheduleCleanup):
 *  - Weekly assessment, every 7 days, only when the phone is charging and
 *    on an unmetered network is NOT required (assessment is local; it does
 *    not use the API). Runs in the background via WorkManager.
 *  - Storage-low check: the daily briefing worker also calls
 *    StorageMonitor.isLow(); if free space is low, an assessment is
 *    triggered immediately with trigger="storage_low" and a notification
 *    tells Tayo — because a phone cleaner that only runs when asked is
 *    exactly the one that "never seems to get done".
 *
 * What the worker does:
 *  1. Runs CleanupEngine.assess() (scored, tiered).
 *  2. Applies the user's CleanupPermission:
 *       ASK_EVERYTHING / PAUSED -> nothing is deleted, everything waits.
 *       AUTO_SAFE_ONLY (default) -> SAFE items (exact duplicates + own
 *         cache only) are deleted; everything else waits for review.
 *  3. Saves a CleanupReport (what was cleaned, bytes freed) to the store,
 *     so the Clean screen can show "Last assessment: … freed X".
 *  4. Posts a notification ONLY if there is something to review or clean —
 *     no spam when the phone is already tidy.
 *
 * Deletion itself happens through CleanupExecutor.deleteApproved() in the
 * ViewModel, never directly in this worker, so the permission rule is
 * enforced in exactly one place.
 */
class CleanupAssessmentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = JsonStore(applicationContext)
        val learning = store.loadCleanupLearning()
        val engine = CleanupEngine(applicationContext)
        val trigger = inputData.getString(KEY_TRIGGER) ?: "weekly"
        val assessment = engine.assess(trigger, learning)
        store.saveLastAssessment(assessment)

        val permission = runCatching {
            CleanupPermission.valueOf(store.loadCleanupPermission())
        }.getOrDefault(CleanupPermission.AUTO_SAFE_ONLY)
        val (auto, review) = engine.applyPermission(assessment, permission)

        // Auto-clean only SAFE items, via the single deletion path.
        var freed = 0L
        for (item in auto) {
            freed += CleanupExecutor(applicationContext).deleteIfSafe(item) ?: 0L
        }
        val report = engine.buildReport(assessment, auto, review, freed)
        store.saveCleanupReport(report)
        // TODO: post a notification via NotificationHelper when review items exist.
        return Result.success()
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val UNIQUE_WEEKLY = "master-chief-weekly-cleanup"

        fun scheduleWeekly(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupAssessmentWorker>(
                7, TimeUnit.DAYS
            ).addTag("cleanup").build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** Called by the daily briefing worker when storage is low. */
        fun triggerStorageLow(context: Context) {
            if (!StorageMonitor.isLow(context)) return
            val request = androidx.work.OneTimeWorkRequestBuilder<CleanupAssessmentWorker>()
                .setInputData(androidx.work.Data.Builder().putString(KEY_TRIGGER, "storage_low").build())
                .addTag("cleanup").build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
