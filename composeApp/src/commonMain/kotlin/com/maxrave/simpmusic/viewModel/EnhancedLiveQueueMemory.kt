package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track

/**
 * Volatile metadata for a LIVE Media3 queue, not persistent storage.
 * The process owns both this object and the player. On force-stop, reboot,
 * or process eviction it disappears automatically, so old sessions cannot
 * be resurrected.
 */
internal data class EnhancedLiveQueueSession(
    val sourceId: String,
    val sourceName: String,
    val sourceTracks: List<Track>,
    val shuffledUpcoming: List<Track>,
    val nextSourceIndex: Int,
    val queueVideoIds: List<String>,
    val recommendedIds: Set<String>,
    val usedRecommendationIds: Set<String>,
) {
    val label: String get() = "$sourceName · Enhanced 2+1"
}

internal object EnhancedLiveQueueMemory {
    @Volatile var session: EnhancedLiveQueueSession? = null

    fun clear() {
        session = null
    }
}

/** Only the same in-process queue can get its Enhanced UI metadata back. */
internal fun matchesLiveEnhancedQueue(
    session: EnhancedLiveQueueSession,
    playlistName: String?,
    actualIds: List<String>,
    currentId: String?,
): Boolean {
    if (session.sourceId.isBlank() || playlistName != session.label ||
        actualIds.isEmpty() || currentId.isNullOrBlank()) return false
    if (actualIds != session.queueVideoIds) return false
    if (session.nextSourceIndex !in 0..session.shuffledUpcoming.size) return false
    return actualIds.any { it == currentId || "Video$it" == currentId }
}
