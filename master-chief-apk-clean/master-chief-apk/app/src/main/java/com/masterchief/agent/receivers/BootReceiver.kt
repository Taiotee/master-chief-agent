package com.masterchief.agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.masterchief.agent.workers.CleanupAssessmentWorker
import com.masterchief.agent.workers.MorningBriefingWorker

/** Re-schedule the daily briefing and weekly cleanup after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MorningBriefingWorker.scheduleDaily(context)
            CleanupAssessmentWorker.scheduleWeekly(context)
        }
    }
}
