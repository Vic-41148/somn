package dev.vic41148.somn.core.data.retention

import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import java.util.concurrent.TimeUnit

/**
 * The rule deciding which sleep-talk recordings have outlived their welcome.
 *
 * Kept as a pure function separate from [ClipRetentionWorker] because it encodes a promise made
 * to users in PRIVACY.md — recordings expire by default — and a promise that nothing tests is a
 * promise that quietly stops being true.
 */
object ClipRetentionPolicy {

    /**
     * Timestamp before which clips should be deleted, or `null` when retention is disabled and
     * nothing should ever be pruned.
     *
     * Zero and negative values both mean "keep forever": zero is the documented sentinel, and
     * negatives can only arrive from a corrupted preference, where deleting everything would be
     * the worst possible interpretation.
     */
    fun cutoffMillis(nowMillis: Long, retentionDays: Int): Long? {
        if (retentionDays <= SomnPreferencesRepository.CLIP_RETENTION_KEEP_FOREVER) return null
        return nowMillis - TimeUnit.DAYS.toMillis(retentionDays.toLong())
    }
}
