package com.masterchief.agent.agent.cleanup

import kotlinx.serialization.Serializable

/** What kind of clutter this is. */
enum class ClutterKind {
    DUPLICATE_PHOTO,   // exact hash duplicate — keep oldest copy
    BURST_PHOTO,       // near-identical burst — keep the best one
    BLURRY_PHOTO,      // low-detail / likely accidental shot
    SCREENSHOT,        // screenshot, often one-off and forgotten
    LARGE_VIDEO,       // big video, old or rarely watched
    CACHE_JUNK,        // app cache / temp files — safe to clear
    OLD_DOWNLOAD,      // download untouched for 90+ days
    UNUSED_APP         // app not opened for 180+ days
}

/**
 * How safe an item is to remove, decided by scoring — never by a single signal.
 *  SAFE   (score 80–100): only junk with no user value — e.g. exact duplicate,
 *                         app cache. May be auto-cleaned, and ONLY if the user
 *                         set permission level AUTO_SAFE_ONLY.
 *  REVIEW (score 50–79):  plausibly clutter, plausibly wanted — screenshots,
 *                         blurry photos, old downloads, large videos, unused
 *                         apps. ALWAYS needs a human tap in the review list.
 *  KEEP   (score 0–49):   do not suggest removal at all — favourites, recent
 *                         files, anything the user kept before, anything
 *                         flagged important.
 */
enum class SafetyTier { SAFE, REVIEW, KEEP }

@Serializable
data class ClutterItem(
    val id: String,
    val kind: String,
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    /** 0–100. Higher = safer to remove. */
    val safetyScore: Int,
    val tier: String,
    val reason: String,
    /** What would happen if the user approves it. */
    val action: String,
    /** Set when the user decides: "kept" or "deleted". Used for learning. */
    val decision: String = ""
) {
    val safety: SafetyTier get() = SafetyTier.valueOf(tier)
}

/** Permission level the user sets in Settings > Cleanup. Default: AUTO_SAFE_ONLY. */
enum class CleanupPermission {
    /** Every item, even safe cache, waits for a tap in the review list. */
    ASK_EVERYTHING,
    /** Safe junk (cache, exact duplicates) may be auto-cleaned on schedule.
     *  Everything else always asks first. */
    AUTO_SAFE_ONLY,
    /** No automatic assessment or cleaning. Manual sweeps only. */
    PAUSED
}

@Serializable
data class CleanupReport(
    val id: String,
    val epochMs: Long,
    val trigger: String, // "weekly", "storage_low", "manual"
    val itemsAssessed: Int,
    val itemsAutoCleaned: Int,
    val itemsAwaitingReview: Int,
    val bytesFreed: Long,
    val bytesAwaitingReview: Long,
    val summary: String
)

@Serializable
data class CleanupAssessment(
    val id: String,
    val epochMs: Long,
    val trigger: String,
    val items: List<ClutterItem>,
    val totalBytes: Long,
    val safeBytes: Long
)

/** Learning record: what Tayo kept vs deleted, per kind — drives future scores. */
@Serializable
data class CleanupLearning(
    val keptByKind: Map<String, Int> = emptyMap(),
    val deletedByKind: Map<String, Int> = emptyMap()
) {
    /** -1.0 (always keeps this kind) … +1.0 (always deletes it). */
    fun bias(kind: ClutterKind): Double {
        val k = keptByKind[kind.name] ?: 0
        val d = deletedByKind[kind.name] ?: 0
        val total = k + d
        if (total < 3) return 0.0 // not enough evidence yet — do not learn from noise
        return (d - k).toDouble() / total
    }
}
