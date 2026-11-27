package com.masterchief.agent.workers

import android.content.Context
import android.net.Uri
import com.masterchief.agent.agent.cleanup.ClutterItem
import com.masterchief.agent.agent.cleanup.SafetyTier
import java.io.File

/**
 * The SINGLE place in the app that deletes a file.
 *
 * Rules enforced here, so no screen or worker can bypass them:
 *  1. Only SAFE-tier items may be deleted without an explicit user tap,
 *     and only when the caller already checked the permission level
 *     (AUTO_SAFE_ONLY). This function itself re-checks the tier.
 *  2. REVIEW / KEEP items return null here — they can only be deleted from
 *     the review list after the user taps Confirm, which calls
 *     deleteConfirmed() instead.
 *  3. Deletion goes through MediaStore (photos/videos) or File.delete()
 *     (downloads/cache). On Android 11+ the system may show its own
 *     delete prompt; that is Android protecting the user, and Chief must
 *     not try to bypass it.
 *  4. Returns bytes freed, or null if nothing was deleted.
 */
class CleanupExecutor(private val context: Context) {

    /** Auto-clean path: SAFE items only. */
    fun deleteIfSafe(item: ClutterItem): Long? {
        if (item.safety != SafetyTier.SAFE) return null
        return delete(item)
    }

    /** Review-list path: user tapped Confirm on a specific item. */
    fun deleteConfirmed(item: ClutterItem): Long? = delete(item)

    private fun delete(item: ClutterItem): Long? = runCatching {
        if (item.path.startsWith("content://")) {
            val uri = Uri.parse(item.path)
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) item.sizeBytes else null
        } else {
            val file = File(item.path)
            if (file.delete()) item.sizeBytes
            else if (file.isDirectory) {
                // Cache dirs: delete contents, keep the dir itself.
                var freed = 0L
                file.walkTopDown().filter { it.isFile }.forEach {
                    freed += it.length(); it.delete()
                }
                freed.takeIf { it > 0 }
            } else null
        }
    }.getOrNull()
}
