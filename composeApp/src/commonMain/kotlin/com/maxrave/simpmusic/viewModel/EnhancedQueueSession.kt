package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * The Media3 service persists the PLAYABLE queue separately. That snapshot is
 * insufficient to resume Enhanced: it does not contain the source order/cursor,
 * queue ownership or recommendation membership.
 *
 * Save these settings locally, never authentication tokens. Restoring requires
 * checking the real Media3 queue below, not simply trusting the old playlist name.
 */
internal const val ENHANCED_QUEUE_SESSION_KEY = "enhanced_queue_session_v1"

@Serializable
internal data class EnhancedQueueSession(
    val schemaVersion: Int = 1,
    val sourceId: String,
    val sourceName: String,
    val sourceTracks: List<Track>,
    val shuffledUpcoming: List<Track>,
    val queueVideoIds: List<String>,
    val nextSourceIndex: Int,
    val recommendedVideoIds: Set<String>,
    val usedRecommendationIds: Set<String>,
) {
    val playlistLabel: String get() = "$sourceName · Enhanced 2+1"
}

internal object EnhancedQueueSessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(snapshot: EnhancedQueueSession): String = json.encodeToString(snapshot)

    fun decode(value: String?): EnhancedQueueSession? {
        if (value.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<EnhancedQueueSession>(value) }
            .getOrNull()
            ?.takeIf {
                it.schemaVersion == 1 &&
                    it.sourceId.isNotBlank() &&
                    it.sourceName.isNotBlank() &&
                    it.queueVideoIds.isNotEmpty() &&
                    it.sourceTracks.isNotEmpty() &&
                    it.shuffledUpcoming.size <= it.sourceTracks.size &&
                    it.nextSourceIndex in 0..it.shuffledUpcoming.size
            }
    }
}

internal data class EnhancedRestorationPlan(
    val nextSourceIndex: Int,
    val recommendedIds: Set<String>,
    val usedRecommendationIds: Set<String>,
)

/**
 * Only activate the session when the actual restored player queue is the same
 * one we saved, and the currently playing item really belongs to that queue.
 *
 * In the audio service, saved queue rows can lag behind our recent metadata
 * (queue saving is asynchronous). Therefore accept a strict *prefix*, not an
 * arbitrary overlap. This still rejects two different queues with the same
 * user-visible title. Do not infer Enhanced solely from 'Enhanced 2+1' text.
 */
internal fun planEnhancedRestoration(
    session: EnhancedQueueSession,
    playlistName: String?,
    actualQueueVideoIds: List<String>,
    currentVideoId: String?,
): EnhancedRestorationPlan? {
    if (playlistName != session.playlistLabel) return null
    if (actualQueueVideoIds.isEmpty() || currentVideoId.isNullOrBlank()) return null
    if (actualQueueVideoIds.none { currentVideoId == it || currentVideoId.endsWith(it) }) return null

    val expected = session.queueVideoIds
    val sharedPrefix = minOf(expected.size, actualQueueVideoIds.size)
    if (expected.take(sharedPrefix) != actualQueueVideoIds.take(sharedPrefix)) return null

    val sourceIds = session.sourceTracks.mapTo(hashSetOf()) { it.videoId }
    if (sourceIds.isEmpty()) return null

    val queuedIds = actualQueueVideoIds.toHashSet()
    // If a crash happened mid-progressive first batch, start refilling after
    // the last *actually persisted* source track, not the planned 14-track batch.
    val loadedSourcePrefix = session.shuffledUpcoming
        .takeWhile { it.videoId in queuedIds }.size

    return EnhancedRestorationPlan(
        nextSourceIndex = loadedSourcePrefix,
        recommendedIds = session.recommendedVideoIds.intersect(queuedIds),
        usedRecommendationIds = session.usedRecommendationIds + session.recommendedVideoIds +
            (queuedIds - sourceIds),
    )
}
