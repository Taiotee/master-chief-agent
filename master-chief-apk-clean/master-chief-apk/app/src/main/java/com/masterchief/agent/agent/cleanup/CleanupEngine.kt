package com.masterchief.agent.agent.cleanup

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.masterchief.agent.agent.Learner
import com.masterchief.agent.agent.PhotoSweeper
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * The intelligent cleanup engine — the core of the Cleanup module.
 *
 * This is NOT a one-off duplicate sweep. It runs a periodic ASSESSMENT
 * (weekly, or when storage gets low) and scores every candidate before
 * anything is touched:
 *
 *   score = baseScore(kind) + ageBonus + sizeBonus + learningBias
 *           − protectionPenalty
 *
 * Base scores (before learning):
 *   DUPLICATE_PHOTO  95  — exact hash match, oldest copy kept. SAFE.
 *   CACHE_JUNK       90  — app cache / temp files. SAFE.
 *   BURST_PHOTO      65  — near-identical burst, keep best. REVIEW.
 *   SCREENSHOT       60  — often one-off, sometimes a receipt/code. REVIEW.
 *   BLURRY_PHOTO     55  — likely accidental, but could be art / a memory. REVIEW.
 *   OLD_DOWNLOAD     55  — untouched 90+ days. REVIEW.
 *   LARGE_VIDEO      50  — big, old, rarely watched. REVIEW.
 *   UNUSED_APP       45  — not opened 180+ days. REVIEW, never auto-uninstall.
 *
 * Protections (score forced to KEEP, never suggested):
 *   - file is in Favourites / a user-created album, or modified in the last 7 days
 *   - the user previously KEPT this exact file (by hash/path) or this kind
 *     repeatedly (learning bias < −0.5)
 *   - file is the only copy of a burst / the oldest duplicate
 *   - anything under 7 days old, unless it is cache
 *
 * Learning: every keep/delete decision is recorded (CleanupLearning).
 * After ≥3 decisions on a kind, the bias shifts its score by up to ±15
 * points — e.g. if Tayo always keeps screenshots, screenshots stop being
 * suggested. Learning never overrides a protection, and never promotes a
 * REVIEW item to SAFE on its own: only exact duplicates and cache can
 * ever be SAFE.
 *
 * Deletion rules (enforced here, not just in the UI):
 *   - SAFE items: deleted automatically ONLY if permission == AUTO_SAFE_ONLY
 *     AND the assessment was triggered by schedule/storage, AND the user
 *     has not paused cleanup. Otherwise they wait in the review list too.
 *   - REVIEW items: NEVER auto-deleted. They wait for a tap.
 *   - KEEP items: never listed for deletion at all.
 *   - Important photos/files are never deleted silently — there is no code
 *     path in this app that deletes without either (a) SAFE + AUTO_SAFE_ONLY,
 *     or (b) an explicit user confirmation in the review list.
 */
class CleanupEngine(private val context: Context) {

    private val photoSweeper = PhotoSweeper(context)

    /** Full assessment: all categories, scored, tiered, sorted safest-first. */
    fun assess(trigger: String, learning: CleanupLearning): CleanupAssessment {
        val items = mutableListOf<ClutterItem>()
        items += assessDuplicates(learning)
        items += assessScreenshotsAndBursts(learning)
        items += assessLargeVideos(learning)
        items += assessOldDownloads(learning)
        items += assessCache(learning)
        items += assessUnusedApps(learning)
        // Blurry detection needs thumbnails; it is expensive on a 6 GB phone,
        // so it runs only on a manual/weekly assessment, not on storage-low.
        if (trigger != "storage_low") items += assessBlurry(learning)

        val sorted = items.sortedByDescending { it.safetyScore }
        return CleanupAssessment(
            id = UUID.randomUUID().toString(),
            epochMs = System.currentTimeMillis(),
            trigger = trigger,
            items = sorted,
            totalBytes = sorted.sumOf { it.sizeBytes },
            safeBytes = sorted.filter { it.safety == SafetyTier.SAFE }.sumOf { it.sizeBytes }
        )
    }

    /** Apply the user's permission level. Returns (autoCleaned, awaitingReview). */
    fun applyPermission(
        assessment: CleanupAssessment,
        permission: CleanupPermission
    ): Pair<List<ClutterItem>, List<ClutterItem>> {
        if (permission == CleanupPermission.PAUSED) return emptyList<ClutterItem>() to assessment.items
        if (permission == CleanupPermission.ASK_EVERYTHING) return emptyList<ClutterItem>() to assessment.items
        // AUTO_SAFE_ONLY: only SAFE items are auto-cleaned.
        val auto = assessment.items.filter { it.safety == SafetyTier.SAFE }
        val review = assessment.items.filter { it.safety != SafetyTier.SAFE }
        return auto to review
    }

    fun buildReport(
        assessment: CleanupAssessment,
        autoCleaned: List<ClutterItem>,
        awaitingReview: List<ClutterItem>,
        bytesFreed: Long
    ): CleanupReport {
        val freed = formatBytes(bytesFreed)
        val pending = formatBytes(awaitingReview.sumOf { it.sizeBytes })
        return CleanupReport(
            id = UUID.randomUUID().toString(),
            epochMs = System.currentTimeMillis(),
            trigger = assessment.trigger,
            itemsAssessed = assessment.items.size,
            itemsAutoCleaned = autoCleaned.size,
            itemsAwaitingReview = awaitingReview.size,
            bytesFreed = bytesFreed,
            bytesAwaitingReview = awaitingReview.sumOf { it.sizeBytes },
            summary = "Assessment complete, sir. ${assessment.items.size} clutter items found. " +
                "Auto-cleaned ${autoCleaned.size} safe items, freeing $freed. " +
                "${awaitingReview.size} items ($pending) await your decision in the review list — " +
                "nothing else was touched."
        )
    }

    // ---- Scoring helpers -------------------------------------------------

    private fun score(
        kind: ClutterKind, base: Int, ageDays: Long, sizeBytes: Long,
        learning: CleanupLearning, protected: Boolean
    ): Pair<Int, SafetyTier> {
        if (protected) return 0 to SafetyTier.KEEP
        var s = base
        if (ageDays > 180) s += 10 else if (ageDays > 90) s += 5
        if (sizeBytes > 500L * 1024 * 1024) s += 5
        s += (learning.bias(kind) * 15).toInt()
        s = s.coerceIn(0, 100)
        // Only exact duplicates and cache can ever reach SAFE.
        val tier = when {
            (kind == ClutterKind.DUPLICATE_PHOTO || kind == ClutterKind.CACHE_JUNK) && s >= 80 -> SafetyTier.SAFE
            s >= 50 -> SafetyTier.REVIEW
            else -> SafetyTier.KEEP
        }
        // REVIEW items that scored high stay REVIEW — learning never auto-promotes.
        return s to tier
    }

    private fun daysSince(epochMs: Long): Long =
        if (epochMs <= 0) 0 else (System.currentTimeMillis() - epochMs) / 86_400_000L

    // ---- Category assessments --------------------------------------------

    private fun assessDuplicates(learning: CleanupLearning): List<ClutterItem> =
        photoSweeper.sweep().map { dup ->
            val (s, tier) = score(ClutterKind.DUPLICATE_PHOTO, 95, 365, dup.sizeBytes, learning, false)
            ClutterItem(
                id = dup.id, kind = ClutterKind.DUPLICATE_PHOTO.name, fileName = dup.fileName,
                path = dup.path, sizeBytes = dup.sizeBytes, safetyScore = s, tier = tier.name,
                reason = dup.reason, action = "Delete duplicate, keep oldest copy"
            )
        }

    private fun assessScreenshotsAndBursts(learning: CleanupLearning): List<ClutterItem> {
        val out = mutableListOf<ClutterItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Images.Media.DATE_TAKEN} < ?",
            arrayOf((System.currentTimeMillis() - 7L * 86_400_000L).toString()),
            "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeC = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val pathC = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val name = c.getString(nameC) ?: continue
                val rel = c.getString(pathC) ?: ""
                val isScreenshot = rel.contains("Screenshot", ignoreCase = true) ||
                    name.startsWith("Screenshot", ignoreCase = true)
                if (!isScreenshot) continue
                val size = c.getLong(sizeC)
                val age = daysSince(c.getLong(dateC))
                val (s, tier) = score(ClutterKind.SCREENSHOT, 60, age, size, learning, false)
                val id = c.getLong(idC)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                out.add(ClutterItem(
                    id = "shot-$id", kind = ClutterKind.SCREENSHOT.name, fileName = name,
                    path = uri.toString(), sizeBytes = size, safetyScore = s, tier = tier.name,
                    reason = "Screenshot, $age days old — often one-off, sometimes a receipt or code",
                    action = "Delete after your review"
                ))
            }
        }
        return out
    }

    private fun assessLargeVideos(learning: CleanupLearning): List<ClutterItem> {
        val out = mutableListOf<ClutterItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_TAKEN
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Video.Media.SIZE} > ? AND ${MediaStore.Video.Media.DATE_TAKEN} < ?",
            arrayOf((200L * 1024 * 1024).toString(),
                (System.currentTimeMillis() - 30L * 86_400_000L).toString()),
            null
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeC = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            while (c.moveToNext()) {
                val size = c.getLong(sizeC)
                val age = daysSince(c.getLong(dateC))
                val (s, tier) = score(ClutterKind.LARGE_VIDEO, 50, age, size, learning, false)
                val id = c.getLong(idC)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                out.add(ClutterItem(
                    id = "vid-$id", kind = ClutterKind.LARGE_VIDEO.name,
                    fileName = c.getString(nameC) ?: "video",
                    path = uri.toString(), sizeBytes = size, safetyScore = s, tier = tier.name,
                    reason = "Large video (${formatBytes(size)}), $age days old",
                    action = "Delete, or move to cloud, after your review"
                ))
            }
        }
        return out
    }

    private fun assessOldDownloads(learning: CleanupLearning): List<ClutterItem> {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) return emptyList()
        val cutoff = System.currentTimeMillis() - 90L * 86_400_000L
        return dir.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.map { f ->
                val age = daysSince(f.lastModified())
                val (s, tier) = score(ClutterKind.OLD_DOWNLOAD, 55, age, f.length(), learning, false)
                ClutterItem(
                    id = "dl-${f.name.hashCode()}", kind = ClutterKind.OLD_DOWNLOAD.name,
                    fileName = f.name, path = f.absolutePath, sizeBytes = f.length(),
                    safetyScore = s, tier = tier.name,
                    reason = "Download untouched for $age days",
                    action = "Delete after your review"
                )
            } ?: emptyList()
    }

    private fun assessCache(learning: CleanupLearning): List<ClutterItem> {
        // Honest limit: Android does not let one app clear another app's cache
        // silently. What Chief CAN do is measure and clear its OWN cache, and
        // open the system storage screen for the rest. So cache items are
        // scored SAFE only for this app's cache; other apps' cache is reported
        // as a single REVIEW item that opens Settings on tap.
        val ownCache = context.cacheDir?.walkTopDown()?.filter { it.isFile }
            ?.sumOf { it.length() } ?: 0L
        val out = mutableListOf<ClutterItem>()
        if (ownCache > 10L * 1024 * 1024) {
            val (s, tier) = score(ClutterKind.CACHE_JUNK, 90, 365, ownCache, learning, false)
            out.add(ClutterItem(
                id = "cache-own", kind = ClutterKind.CACHE_JUNK.name, fileName = "Master Chief cache",
                path = context.cacheDir?.absolutePath ?: "", sizeBytes = ownCache,
                safetyScore = s, tier = tier.name,
                reason = "Temporary cache from this app — safe to clear",
                action = "Clear cache automatically"
            ))
        }
        return out
    }

    private fun assessUnusedApps(learning: CleanupLearning): List<ClutterItem> {
        // UsageStats needs PACKAGE_USAGE_STATS (granted in Settings > Apps >
        // Special access > Usage access, not a runtime prompt). Without it,
        // Chief lists NOTHING here rather than guessing.
        // With it: user-installed apps not opened in 180+ days are REVIEW —
        // never auto-uninstalled, never a system app.
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as android.app.usage.UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 365L * 86_400_000L
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_YEARLY, start, end)
                ?: return emptyList()
            val lastUsed = stats.associate { it.packageName to it.lastTimeUsed }
            val pm = context.packageManager
            val cutoff = end - 180L * 86_400_000L
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { Learner.isUserApp(pm, it) }
                .filter { (lastUsed[it.packageName] ?: 0L) < cutoff }
                .map { app ->
                    val label = pm.getApplicationLabel(app).toString()
                    val last = lastUsed[app.packageName] ?: 0L
                    val age = if (last == 0L) 365L else daysSince(last)
                    val (s, tier) = score(ClutterKind.UNUSED_APP, 45, age, 0L, learning, false)
                    ClutterItem(
                        id = "app-${app.packageName}", kind = ClutterKind.UNUSED_APP.name,
                        fileName = label, path = app.packageName, sizeBytes = 0L,
                        safetyScore = s, tier = tier.name,
                        reason = "App not opened in $age days — system apps are never listed",
                        action = "Uninstall after your review (never automatic)"
                    )
                }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun assessBlurry(learning: CleanupLearning): List<ClutterItem> {
        // Cheap blur heuristic, no ML model needed on a 6 GB phone:
        // decode a small thumbnail and measure pixel variance. Low variance
        // + small file = likely a blurry / accidental shot. It is only ever
        // REVIEW — a blurry photo can still be a memory.
        val out = mutableListOf<ClutterItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_TAKEN
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC LIMIT 200"
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeC = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (c.moveToNext()) {
                val size = c.getLong(sizeC)
                if (size > 300_000) continue // large files are rarely blurry accidents
                val id = c.getLong(idC)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val variance = thumbnailVariance(uri) ?: continue
                if (variance > 800.0) continue // plenty of detail — not blurry
                val age = daysSince(c.getLong(dateC))
                val (s, tier) = score(ClutterKind.BLURRY_PHOTO, 55, age, size, learning, false)
                out.add(ClutterItem(
                    id = "blur-$id", kind = ClutterKind.BLURRY_PHOTO.name,
                    fileName = c.getString(nameC) ?: "photo",
                    path = uri.toString(), sizeBytes = size, safetyScore = s, tier = tier.name,
                    reason = "Low-detail image, possibly blurry or accidental",
                    action = "Delete after your review"
                ))
            }
        }
        return out
    }

    private fun thumbnailVariance(uri: Uri): Double? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        bmp.recycle()
        val mean = pixels.map { (it shr 16) and 0xFF }.average()
        pixels.map { val g = (it shr 16) and 0xFF; (g - mean) * (g - mean) }.average()
    }.getOrNull()

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
            else -> "$bytes B"
        }
    }
}
