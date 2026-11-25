package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore

/**
 * Resolve all radio pairs concurrently, but deliver ordered 2+1 blocks as
 * soon as each block is ready. Unlike awaitAll(), this never waits for the
 * LAST radio lookup before the FIRST recommendation can be displayed.
 *
 * Anonymous Radio compares all returned eligible tracks from its own pair,
 * falling back exclusively to the second song's radio.
 * No nearby-pair borrowing: this also preserves progressive responsiveness
 * when the current pair's radios return empty.
 * Cancellation propagates to pending radio lookups and prevents late chunks.
 */
internal suspend fun <T> emitEnhancedPairsProgressively(
    seeds: List<T>,
    excludedIds: Set<String>,
    id: (T) -> String,
    fetchRadio: suspend (T) -> List<T>,
    onDecision: (List<T>, EnhancedShufflePlanner.RadioSelection<T>) -> Unit = { _, _ -> },
    onChunk: suspend (sourcePair: List<T>, recommended: T?) -> Unit,
) {
    if (seeds.isEmpty()) return
    coroutineScope {
        val pairs = seeds.chunked(2)
        val radios = pairs.map { pair ->
            async {
                if (pair.size != 2) {
                    emptyList<T>() to emptyList<T>()
                } else {
                    coroutineScope {
                        val first = async { fetchRadio(pair[0]) }
                        val second = async { fetchRadio(pair[1]) }
                        first.await() to second.await()
                    }
                }
            }
        }

        val used = excludedIds.toMutableSet()
        for (index in pairs.indices) {
            val pair = pairs[index]
            var recommendation: T? = null
            if (pair.size == 2) {
                val (first, second) = radios[index].await()
                val decision = EnhancedShufflePlanner.analyzePair(first, second, used, id)
                onDecision(pair, decision)
                recommendation = decision.recommendation
            }
            recommendation?.let { used.add(id(it)) }
            onChunk(pair, recommendation)
        }
    }
}

internal suspend fun buildEnhancedBatchProgressively(
    likedBatch: List<Track>,
    songRepository: SongRepository,
    excludedIds: Set<String>,
    onChunkReady: suspend (EnhancedBatch) -> Unit,
) {
    val semaphore = Semaphore(4)
    var diagnostic: EnhancedRadioDiagnostic? = null
    emitEnhancedPairsProgressively(
        seeds = likedBatch,
        excludedIds = excludedIds,
        id = { it.videoId },
        fetchRadio = { track -> fetchEnhancedRadio(track, songRepository, semaphore) },
        onDecision = { pair, decision -> diagnostic = enhancedRadioDiagnostic(pair, decision) },
    ) { pair, recommendation ->
        val tracks = if (recommendation == null) pair else pair + recommendation
        val recommendationIds = recommendation?.let { setOf(it.videoId) }.orEmpty()
        val details = diagnostic?.let { d -> recommendation?.let { mapOf(it.videoId to d) } }.orEmpty()
        diagnostic = null
        onChunkReady(EnhancedBatch(tracks, recommendationIds, details))
    }
}
