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

internal suspend fun fetchEnhancedRadio(
    song: Track,
    songRepository: SongRepository,
    radioSemaphore: Semaphore,
): List<Track> =
        radioSemaphore.withPermit {
            try {
                withTimeoutOrNull(8_000L) {
                    songRepository.getRadioFromEndpoint(
                        YouTubeWatchEndpoint(
                            videoId = song.videoId,
                            playlistId = "RDAMVM${song.videoId}",
                        ),
                        anonymous = true,
                    ).firstOrNull { it is Resource.Success }?.data?.first.orEmpty()
                }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }


internal const val ENHANCED_LIKED_BATCH_SIZE = 14
internal const val ENHANCED_PREFETCH_REMAINING = 14

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

    val radioPairs = coroutineScope {
        pairs.map { pair ->
            async {
                if (pair.size != 2) {
                    emptyList<Track>() to emptyList<Track>()
                } else {
                    coroutineScope {
                        val first = async { fetchEnhancedRadio(pair[0], songRepository, radioSemaphore) }
                        val second = async { fetchEnhancedRadio(pair[1], songRepository, radioSemaphore) }
                        first.await() to second.await()
                    }
                }
            }
        }.awaitAll()
    }

    // Match only the first 15 tracks in each own pair's radio; if none match,
    // use only the second seed's radio (top 15). Missing candidates yield
    // a legitimate A -> B gap rather than borrowing from an unrelated pair.
    // This same selector is used for progressive startup and later prefetch.
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
