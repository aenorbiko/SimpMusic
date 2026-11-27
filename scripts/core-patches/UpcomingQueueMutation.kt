package com.maxrave.domain.mediaservice.handler

import com.maxrave.domain.data.model.browse.album.Track

/**
 * Apply a prepared queue tail without recreating, seeking or pausing the active item.
 * The caller owns request cancellation, recommendation policy and queue metadata.
 * Like the existing Enhanced path, run on the playback owner's dispatcher.
 * A snapshot mismatch aborts before any mutation. This is not a transaction across
 * asynchronous player operations or metadata loading; callers must serialize changes.
 */
suspend fun MediaPlayerHandler.replaceUpcomingKeepingCurrent(
    expectedMediaId: String,
    expectedTracks: List<Track>,
    activeIndex: Int,
    preservedQueue: QueueData.Data,
    upcoming: List<Track>,
    removeUpcomingItem: (Int) -> Unit = { removeMediaItem(it) },
    beforeAppend: () -> Unit = {},
): Boolean {
    if (activeIndex !in expectedTracks.indices || expectedMediaId.isBlank()) return false
    if (getCurrentMediaItem()?.mediaId != expectedMediaId) return false
    if (queueData.value?.data?.listTracks != expectedTracks) return false
    if (preservedQueue.listTracks != expectedTracks.take(activeIndex + 1)) return false
    val currentId = expectedTracks[activeIndex].videoId
    if (expectedMediaId != currentId && !expectedMediaId.endsWith(currentId)) return false

    for (position in expectedTracks.lastIndex downTo activeIndex + 1) {
        removeUpcomingItem(position)
    }
    setQueueData(preservedQueue)
    // Enhanced badges must be published before Media3 converts the upcoming items.
    beforeAppend()
    if (upcoming.isNotEmpty()) {
        loadMoreCatalog(upcoming.toCollection(arrayListOf<Track>()), isAddToQueue = false)
    }
    return true
}
