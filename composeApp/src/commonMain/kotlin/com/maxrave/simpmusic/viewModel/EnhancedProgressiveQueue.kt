package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.TimeSource

internal const val ENHANCED_RADIO_MAX_TRACKS = 100

/** A single returned radio page. The continuation is never exposed in diagnostics. */
internal data class EnhancedRadioPage<T>(
    val tracks: List<T>,
    val continuation: String? = null,
    val requestMs: Long = 0,
    val semaphoreWaitMs: Long = 0,
    val status: String = "OK",
)

internal data class EnhancedPairTiming<T>(
    val firstA: EnhancedRadioPage<T>,
    val firstB: EnhancedRadioPage<T>,
    val secondA: EnhancedRadioPage<T>?,
    val secondB: EnhancedRadioPage<T>?,
    val firstMatches: Int,
    val initialPairMs: Long,
    val extensionMs: Long,
    val elapsedMs: Long,
)

/** Initial requests run concurrently. In queue order, use the current exclusion
 * set to check overlap; only then fetch at most one extra page per seed.
 * Keeping one ordered consumer preserves deduplication and progressive emission.
 */
internal suspend fun <T> emitPagedEnhancedPairs(
    seeds: List<T>,
    excludedIds: Set<String>,
    id: (T) -> String,
    fetchPage: suspend (T, String?) -> EnhancedRadioPage<T>,
    onDecision: (List<T>, EnhancedShufflePlanner.RadioSelection<T>, EnhancedPairTiming<T>) -> Unit = { _, _, _ -> },
    onChunk: suspend (sourcePair: List<T>, recommended: T?) -> Unit,
) = coroutineScope {
    val pairs = seeds.chunked(2)
    val started = TimeSource.Monotonic.markNow()
    val radios = pairs.map { pair ->
        async {
            if (pair.size != 2) null else coroutineScope {
                val begin = TimeSource.Monotonic.markNow()
                val first = async { fetchPage(pair[0], null) }
                val second = async { fetchPage(pair[1], null) }
                val a = first.await()
                val b = second.await()
                Triple(a, b, begin.elapsedNow().inWholeMilliseconds)
            }
        }
    }
    val used = excludedIds.toMutableSet()
    for (index in pairs.indices) {
        val pair = pairs[index]
        val initial = radios[index].await()
        if (initial == null) {
            onChunk(pair, null)
            continue
        }
        val (a, b, initialMs) = initial
        val firstA = a.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
        val firstB = b.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
        val firstDecision = EnhancedShufflePlanner.analyzePair(firstA, firstB, used, id)
        var extraA: EnhancedRadioPage<T>? = null
        var extraB: EnhancedRadioPage<T>? = null
        var extensionMs = 0L
        if (firstDecision.matches.isEmpty()) {
            val tokenA = a.continuation?.takeIf { it.isNotBlank() && firstA.size < ENHANCED_RADIO_MAX_TRACKS }
            val tokenB = b.continuation?.takeIf { it.isNotBlank() && firstB.size < ENHANCED_RADIO_MAX_TRACKS }
            if (tokenA != null || tokenB != null) {
                val begin = TimeSource.Monotonic.markNow()
                coroutineScope {
                    val requestA = tokenA?.let { token -> async { fetchPage(pair[0], token) } }
                    val requestB = tokenB?.let { token -> async { fetchPage(pair[1], token) } }
                    extraA = requestA?.await()
                    extraB = requestB?.await()
                }
                extensionMs = begin.elapsedNow().inWholeMilliseconds
            }
        }
        val allA = (firstA + extraA?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS)
        val allB = (firstB + extraB?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS)
        val decision = EnhancedShufflePlanner.analyzePair(allA, allB, used, id)
        onDecision(pair, decision, EnhancedPairTiming(
            a, b, extraA, extraB, firstDecision.matches.size, initialMs,
            extensionMs, started.elapsedNow().inWholeMilliseconds,
        ))
        val recommendation = decision.recommendation
        recommendation?.let { used.add(id(it)) }
        onChunk(pair, recommendation)
    }
}

/** Keep the unpaged test harness on the same ordered selection implementation. */
internal suspend fun <T> emitEnhancedPairsProgressively(
    seeds: List<T>,
    excludedIds: Set<String>,
    id: (T) -> String,
    fetchRadio: suspend (T) -> List<T>,
    onDecision: (List<T>, EnhancedShufflePlanner.RadioSelection<T>) -> Unit = { _, _ -> },
    onChunk: suspend (sourcePair: List<T>, recommended: T?) -> Unit,
) = emitPagedEnhancedPairs(
    seeds = seeds,
    excludedIds = excludedIds,
    id = id,
    fetchPage = { seed, _ -> EnhancedRadioPage(fetchRadio(seed)) },
    onDecision = { pair, decision, _ -> onDecision(pair, decision) },
    onChunk = onChunk,
)

internal suspend fun buildEnhancedBatchProgressively(
    likedBatch: List<Track>,
    songRepository: SongRepository,
    excludedIds: Set<String>,
    onChunkReady: suspend (EnhancedBatch) -> Unit,
) {
    val semaphore = Semaphore(4)
    var diagnostic: EnhancedRadioDiagnostic? = null
    emitPagedEnhancedPairs(
        seeds = likedBatch,
        excludedIds = excludedIds,
        id = { it.videoId },
        fetchPage = { track, token -> fetchEnhancedRadio(track, songRepository, semaphore, token) },
        onDecision = { pair, decision, timing -> diagnostic = enhancedRadioDiagnostic(pair, decision, timing) },
    ) { pair, recommendation ->
        val tracks = if (recommendation == null) pair else pair + recommendation
        val recommendationIds = recommendation?.let { setOf(it.videoId) }.orEmpty()
        val details = diagnostic?.let { d -> recommendation?.let { mapOf(it.videoId to d) } }.orEmpty()
        diagnostic = null
        onChunkReady(EnhancedBatch(tracks, recommendationIds, details))
    }
}
