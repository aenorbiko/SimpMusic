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
 * The original recommendation selector (including nearest-radio fallback)
 * is deliberately unchanged; this feature only changes when chunks arrive.
 * Cancellation propagates to pending radio lookups and prevents late chunks.
 */
internal suspend fun <T> emitEnhancedPairsProgressively(
    seeds: List<T>,
    excludedIds: Set<String>,
    id: (T) -> String,
    fetchRadio: suspend (T) -> List<T>,
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
                recommendation = EnhancedShufflePlanner.selectForPair(
                    firstRadio = first,
                    secondRadio = second,
                    usedIds = used,
                    id = id,
                    preferSecondOnFallback = index % 2 == 1,
                )
                if (recommendation == null) {
                    // Keep the existing nearest-pair fallback. Only wait for
                    // another pair if the current pair itself had no candidate.
                    val neighbours = pairs.indices
                        .filter { it != index }
                        .sortedWith(compareBy<Int> { kotlin.math.abs(it - index) }.thenBy { it })
                    for (nearby in neighbours) {
                        val (otherFirst, otherSecond) = radios[nearby].await()
                        recommendation = EnhancedShufflePlanner.selectForPair(
                            firstRadio = otherFirst,
                            secondRadio = otherSecond,
                            usedIds = used,
                            id = id,
                            preferSecondOnFallback = index % 2 == 1,
                        )
                        if (recommendation != null) break
                    }
                }
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
    emitEnhancedPairsProgressively(
        seeds = likedBatch,
        excludedIds = excludedIds,
        id = { it.videoId },
        fetchRadio = { track -> fetchEnhancedRadio(track, songRepository, semaphore) },
    ) { pair, recommendation ->
        val tracks = if (recommendation == null) pair else pair + recommendation
        val recommendationIds = recommendation?.let { setOf(it.videoId) }.orEmpty()
        onChunkReady(EnhancedBatch(tracks, recommendationIds))
    }
}
