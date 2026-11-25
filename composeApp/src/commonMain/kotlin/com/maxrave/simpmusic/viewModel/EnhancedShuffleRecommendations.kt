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
    val diagnostics: Map<String, EnhancedRadioDiagnostic> = emptyMap(),
)

/** Session-only diagnostic metadata; never includes cookies or account tokens. */
data class EnhancedRadioDiagnostic(
    val songA: String,
    val songB: String,
    val selectedSong: String,
    val source: String,
    val receivedA: Int,
    val receivedB: Int,
    val eligibleA: Int,
    val eligibleB: Int,
    val oldTop30Matches: Int,
    val selectedRankA: Int?,
    val selectedRankB: Int?,
    val commonSongs: List<String>,
)

internal fun enhancedRadioDiagnostic(
    pair: List<Track>,
    decision: EnhancedShufflePlanner.RadioSelection<Track>,
): EnhancedRadioDiagnostic? {
    val selected = decision.recommendation ?: return null
    if (pair.size != 2) return null
    return EnhancedRadioDiagnostic(
        songA = pair[0].title.orEmpty(),
        songB = pair[1].title.orEmpty(),
        selectedSong = selected.title.orEmpty(),
        source = decision.source,
        receivedA = decision.receivedA,
        receivedB = decision.receivedB,
        eligibleA = decision.eligibleA,
        eligibleB = decision.eligibleB,
        oldTop30Matches = decision.oldTop30Matches,
        selectedRankA = decision.selectedRankA,
        selectedRankB = decision.selectedRankB,
        commonSongs = decision.matches.map { "${it.track.title} (A #${it.rankA}, B #${it.rankB})" },
    )
}

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

    // Compare all eligible returned tracks; use B-only fallback if none match.
    // The progressive startup uses the same selector.
    val diagnostics = mutableMapOf<String, EnhancedRadioDiagnostic>()
    val selected = EnhancedShufflePlanner.selectForAllPairs(
        radioPairs = radioPairs,
        excludedIds = excludedIds,
        id = { it.videoId },
        onDecision = { index, decision ->
            val detail = enhancedRadioDiagnostic(pairs[index], decision)
            val recommendation = decision.recommendation
            if (detail != null && recommendation != null) diagnostics[recommendation.videoId] = detail
        },
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
    return EnhancedBatch(result, recommendations, diagnostics)
}
