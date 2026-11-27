package com.maxrave.domain.mediaservice.handler

import com.maxrave.domain.data.model.browse.album.Track

data class StartRadioQueuePlan(val preservedQueue: QueueData.Data, val upcoming: List<Track>)

/** Compare actual playback order, not just a potentially stale displayed index. */
fun planStartRadioQueue(
    existing: List<Track>,
    playerOrder: List<String>,
    activeIndex: Int,
    seedId: String,
    seedTitle: String,
    radioTracks: List<Track>,
    continuation: String?,
): StartRadioQueuePlan? {
    if (activeIndex !in existing.indices || existing[activeIndex].videoId != seedId) return null
    if (existing.map { it.videoId } != playerOrder) return null
    val upcoming = radioTracks.filterNot { it.videoId == seedId }
    if (upcoming.isEmpty()) return null
    return StartRadioQueuePlan(
        QueueData.Data(
            listTracks = existing.take(activeIndex + 1),
            firstPlayedTrack = existing[activeIndex],
            playlistId = "RDAMVM$seedId",
            playlistName = "\"$seedTitle\" Radio",
            playlistType = PlaylistType.RADIO,
            continuation = continuation,
        ),
        upcoming,
    )
}

fun startRadioRequestIsCurrent(
    requestedRevision: Long,
    currentRevision: Long,
    requestedMediaId: String,
    currentMediaId: String?,
    requestedShuffleIntent: Long,
    currentShuffleIntent: Long,
): Boolean = requestedRevision == currentRevision &&
    requestedMediaId == currentMediaId && requestedShuffleIntent == currentShuffleIntent
