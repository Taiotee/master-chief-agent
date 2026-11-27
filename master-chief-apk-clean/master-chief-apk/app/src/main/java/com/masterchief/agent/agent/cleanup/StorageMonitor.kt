package com.masterchief.agent.agent.cleanup

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.masterchief.agent.agent.PhotoSweeper

/**
 * Storage monitor — the second trigger for a cleanup assessment.
 *
 * Assessment runs when EITHER:
 *  - the weekly schedule fires (see workers/CleanupAssessmentWorker), OR
 *  - free storage falls below the low-storage threshold.
 *
 * Thresholds (OPPO A74 5G, 128 GB): "low" is < 10% free OR < 2 GB free,
 * whichever is hit first. On a 128 GB phone 10% is ~12 GB, so the 2 GB
 * floor is the binding one in practice — Chief warns before the phone
 * actually starts failing to save photos.
 */
object StorageMonitor {
    const val LOW_FREE_BYTES: Long = 2L * 1024 * 1024 * 1024 // 2 GB
    const val LOW_FREE_FRACTION: Double = 0.10

    data class StorageState(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val freeFraction: Double,
        val isLow: Boolean
    )

    fun state(context: Context): StorageState {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val fraction = if (total > 0) free.toDouble() / total else 1.0
        return StorageState(
            totalBytes = total,
            freeBytes = free,
            usedBytes = total - free,
            freeFraction = fraction,
            isLow = free < LOW_FREE_BYTES || fraction < LOW_FREE_FRACTION
        )
    }

    fun isLow(context: Context): Boolean = state(context).isLow
}
