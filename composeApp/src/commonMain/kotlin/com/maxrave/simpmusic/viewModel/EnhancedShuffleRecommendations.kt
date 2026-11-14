package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal const val ENHANCED_LIKED_BATCH_SIZE = 14
internal const val ENHANCED_PREFETCH_REMAINING = 14

// Reuse a radio's results within a short session, not cached audio.
// Store at most 40 tracks per radio and 64 radios; no disk writes.
private val enhancedRadioCache = EnhancedRadioCache<Track>()


/** Called on actual track transitions, not on rarely-updated index state. */
internal fun shouldPrefetchEnhanced(remainingTracks: Int): Boolean =
    remainingTracks in 0..ENHANCED_PREFETCH_REMAINING

internal data class EnhancedBatch(
    val tracks: List<Track>,
    val recommendedIds: Set<String>,
)

/**
 * One temporary queue block: 14 liked songs, up to 7 new recommendations.
 *
 * For every pair we query radio for both songs, preferring overlap between the
 * two radios. Calls run at most 4 concurrently; failed radios do not interrupt
 * already playing music. Do not treat this as a YouTube Music two-song API.
 */
internal suspend fun buildEnhancedBatch(
    likedBatch: List<Track>,
    songRepository: SongRepository,
    excludedIds: Set<String>,
): EnhancedBatch {
    if (likedBatch.isEmpty()) return EnhancedBatch(emptyList(), emptySet())
    val pairs = likedBatch.chunked(2)
    val radioSemaphore = Semaphore(4)

    suspend fun fetchRadio(song: Track): List<Track> =
        radioSemaphore.withPermit {
            val cached = enhancedRadioCache.get(song.videoId)
            if (cached != null) return@withPermit cached
            val tracks = try {
                withTimeoutOrNull(8_000L) {
                    songRepository.getRadioFromEndpoint(
                        YouTubeWatchEndpoint(
                            videoId = song.videoId,
                            playlistId = "RDAMVM${song.videoId}",
                        ),
                    ).firstOrNull { it is Resource.Success }?.data?.first.orEmpty()
                }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (tracks.isNotEmpty()) enhancedRadioCache.put(song.videoId, tracks.take(40))
            tracks
        }

    val radioPairs = coroutineScope {
        pairs.map { pair ->
            async {
                if (pair.size != 2) {
                    emptyList<Track>() to emptyList<Track>()
                } else {
                    coroutineScope {
                        val first = async { fetchRadio(pair[0]) }
                        val second = async { fetchRadio(pair[1]) }
                        first.await() to second.await()
                    }
                }
            }
        }.awaitAll()
    }

    // Radios sometimes return no usable results for a particular pair.
    // Do not turn the opening A -> B -> recommendation slot into A -> B -> C -> D:
    // borrow a still-unused candidate from a nearby pair's already-fetched radio.
    // The pair's own suggestions always take priority when available.
    val selected = EnhancedShufflePlanner.selectForAllPairs(
        radioPairs = radioPairs,
        excludedIds = excludedIds,
        id = { it.videoId },
    )
    val recommendations = hashSetOf<String>()
    val result = ArrayList<Track>(likedBatch.size + pairs.size)
    for ((index, pair) in pairs.withIndex()) {
        result.addAll(pair)
        if (pair.size != 2) continue
        selected[index]?.let { pick ->
            result.add(pick)
            recommendations.add(pick.videoId)
        }
    }
    return EnhancedBatch(result, recommendations)
}
