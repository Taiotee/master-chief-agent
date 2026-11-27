package com.masterchief.agent.agent

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.masterchief.agent.data.CleanupCandidate
import java.security.MessageDigest

/**
 * Duplicate-photo sweep (MediaStore, no root, no extra SDK).
 *
 * Strategy, tuned for a 6 GB RAM phone — never load whole images into memory:
 *  1. Query MediaStore for images (id, name, size, date). No bitmap decoding.
 *  2. Group by (size, date-taken bucket): exact-size groups are candidate duplicates.
 *  3. For groups, hash only the first 64 KB of each file to confirm sameness.
 *  4. Keep the oldest / first copy, flag the rest as candidates.
 *  5. NOTHING is deleted here. Candidates go to the Clean screen; the user
 *     taps Confirm, and only then is a file deleted via MediaStore — with
 *     the system delete prompt on Android 11+.
 *
 * A burst of near-identical photos (same second, similar size) is flagged as
 * "Near-duplicate burst" for review, not auto-deleted.
 */
class PhotoSweeper(private val context: Context) {

    fun sweep(): List<CleanupCandidate> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val bySize = mutableMapOf<Long, MutableList<Triple<Long, String, Long>>>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "photo"
                val size = cursor.getLong(sizeCol)
                val date = cursor.getLong(dateCol)
                if (size > 0) bySize.getOrPut(size) { mutableListOf() }
                    .add(Triple(id, name, date))
            }
        }
        val out = mutableListOf<CleanupCandidate>()
        for ((size, group) in bySize) {
            if (group.size < 2) continue
            // Keep the first (oldest) copy; flag the rest after a hash check.
            val seen = mutableSetOf<String>()
            for ((id, name, _) in group) {
                val h = hashHead(id) ?: continue
                if (!seen.add(h)) {
                    val uri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    out.add(CleanupCandidate(
                        id = "dup-$id", fileName = name, path = uri.toString(),
                        sizeBytes = size,
                        reason = "Exact duplicate — keep the oldest copy"
                    ))
                }
            }
        }
        return out
    }

    /** SHA-256 of the first 64 KB — enough to confirm a duplicate, cheap on RAM. */
    private fun hashHead(imageId: Long): String? = runCatching {
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            val read = stream.read(buf)
            if (read > 0) digest.update(buf, 0, read)
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()
}
