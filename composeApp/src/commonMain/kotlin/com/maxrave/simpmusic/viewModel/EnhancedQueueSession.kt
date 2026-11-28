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
    val usedRecommendationSongKeys: Set<String> = emptySet(),
) {
    val playlistLabel: String get() = "$sourceName · Enhanced 2+1"
}

/**
 * The wire format is separate from the in-memory session. V2 stores the
 * source metadata once and represents shuffledUpcoming by ordered video IDs.
 * The key and restoration invariants remain identical. Legacy V1 payloads
 * can still be decoded; a successful subsequent checkpoint migrates them.
 */
@Serializable
private data class EnhancedQueueSessionWire(
    val schemaVersion: Int = 1,
    val sourceId: String,
    val sourceName: String,
    val sourceTracks: List<Track>,
    val shuffledUpcoming: List<Track>? = null,
    val shuffledUpcomingIds: List<String>? = null,
    val queueVideoIds: List<String>,
    val nextSourceIndex: Int,
    val recommendedVideoIds: Set<String>,
    val usedRecommendationIds: Set<String>,
    val usedRecommendationSongKeys: Set<String> = emptySet(),
)

internal object EnhancedQueueSessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Do not collapse conflicting metadata for the same video ID. Unusual
    // sources with ambiguous IDs or changed upcoming metadata keep V1.
    private fun sourceIndex(tracks: List<Track>): Map<String, Track>? {
        val index = HashMap<String, Track>(tracks.size)
        for (track in tracks) {
            val old = index.put(track.videoId, track)
            if (old != null && old != track) return null
        }
        return index
    }

    fun encode(snapshot: EnhancedQueueSession): String {
        if (snapshot.schemaVersion != 1) return json.encodeToString(snapshot)
        val index = sourceIndex(snapshot.sourceTracks)
        if (index == null || snapshot.shuffledUpcoming.any { index[it.videoId] != it }) {
            return json.encodeToString(snapshot)
        }
        return json.encodeToString(
            EnhancedQueueSessionWire(
                schemaVersion = 2,
                sourceId = snapshot.sourceId,
                sourceName = snapshot.sourceName,
                sourceTracks = snapshot.sourceTracks,
                shuffledUpcomingIds = snapshot.shuffledUpcoming.map { it.videoId },
                queueVideoIds = snapshot.queueVideoIds,
                nextSourceIndex = snapshot.nextSourceIndex,
                recommendedVideoIds = snapshot.recommendedVideoIds,
                usedRecommendationIds = snapshot.usedRecommendationIds,
                usedRecommendationSongKeys = snapshot.usedRecommendationSongKeys,
            )
        )
    }

    fun decode(value: String?): EnhancedQueueSession? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val wire = json.decodeFromString<EnhancedQueueSessionWire>(value)
            val upcoming = when (wire.schemaVersion) {
                1 -> {
                    if (wire.shuffledUpcomingIds != null) return null
                    wire.shuffledUpcoming ?: return null
                }
                2 -> {
                    if (wire.shuffledUpcoming != null) return null
                    val ids = wire.shuffledUpcomingIds ?: return null
                    if (ids.size > wire.sourceTracks.size) return null
                    val index = sourceIndex(wire.sourceTracks) ?: return null
                    ids.map { index[it] ?: return null }
                }
                else -> return null
            }
            EnhancedQueueSession(
                sourceId = wire.sourceId,
                sourceName = wire.sourceName,
                sourceTracks = wire.sourceTracks,
                shuffledUpcoming = upcoming,
                queueVideoIds = wire.queueVideoIds,
                nextSourceIndex = wire.nextSourceIndex,
                recommendedVideoIds = wire.recommendedVideoIds,
                usedRecommendationIds = wire.usedRecommendationIds,
                usedRecommendationSongKeys = wire.usedRecommendationSongKeys,
            )
        }.getOrNull()?.takeIf {
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
    // Media3 prefixes some video IDs with the literal "Video" marker.
    // Never use endsWith(): an unrelated ID may coincidentally end in a
    // short/overlapping ID and resurrect a completely different queue.
    if (actualQueueVideoIds.none { currentVideoId == it || currentVideoId == "Video$it" }) return null

    val expected = session.queueVideoIds
    val sourceIds = session.sourceTracks.mapTo(hashSetOf()) { it.videoId }
    if (sourceIds.isEmpty()) return null

    // The media service persists the queue separately. A hard stop may restore
    // a shorter prefix OR trim already-played history, leaving an ordered tail
    // of the Enhanced session. Require an exact ordered overlap, not just
    // matching track membership or a shared playlist title.
    fun matchesOrderedSlice(actual: List<String>): Boolean {
        if (actual.isEmpty()) return false
        val start = expected.indexOf(actual.first())
        if (start < 0) return false
        val overlap = minOf(expected.size - start, actual.size)
        // For a previously loaded prefix, even one item is safe if the saved
        // session has an identical label + currently playing track. For a
        // shifted tail, require at least three exact sequential IDs.
        if (start > 0 && overlap < 3) return false
        return expected.subList(start, start + overlap) == actual.take(overlap)
    }

    val ordered = matchesOrderedSlice(actualQueueVideoIds)
    // A resumed track sometimes gets prepended by Media3 if the queue save
    // lagged behind the recent-media/position save. Never accept an arbitrary
    // extra track: the head must be the current track from our saved session.
    val insertedCurrent = actualQueueVideoIds.size > 3 &&
        (currentVideoId == actualQueueVideoIds.first() ||
            currentVideoId == "Video${actualQueueVideoIds.first()}") &&
        (actualQueueVideoIds.first() in expected ||
            actualQueueVideoIds.first() in sourceIds ||
            actualQueueVideoIds.first() in session.recommendedVideoIds) &&
        matchesOrderedSlice(actualQueueVideoIds.drop(1))
    if (!ordered && !insertedCurrent) return null

    val queuedIds = actualQueueVideoIds.toHashSet()
    // A trimmed queue can omit the old seeds. Refill after the furthest
    // source track still present, rather than replaying the missing history.
    val loadedSourcePrefix = session.shuffledUpcoming.indexOfLast {
        it.videoId in queuedIds
    } + 1

    return EnhancedRestorationPlan(
        nextSourceIndex = loadedSourcePrefix,
        recommendedIds = session.recommendedVideoIds.intersect(queuedIds),
        usedRecommendationIds = session.usedRecommendationIds + session.recommendedVideoIds +
            (queuedIds - sourceIds),
    )
}
