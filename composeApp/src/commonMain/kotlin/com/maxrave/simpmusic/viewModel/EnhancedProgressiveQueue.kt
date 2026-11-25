package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

internal const val ENHANCED_RADIO_MAX_TRACKS = 100
internal const val ENHANCED_RADIO_MAX_PARALLEL_PAIRS = 4
internal const val ENHANCED_RADIO_MAX_PARALLEL_REQUESTS = ENHANCED_RADIO_MAX_PARALLEL_PAIRS * 2

private val enhancedRadioRequestSlots = Semaphore(ENHANCED_RADIO_MAX_PARALLEL_REQUESTS)

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
    val pairAdmissionWaitMs: Long = 0,
    val orderedDecisionWaitMs: Long = 0,
    val eligibleRadioA: List<T> = emptyList(),
    val eligibleRadioB: List<T> = emptyList(),
)

private data class EnhancedPairResult<T>(
    val decision: EnhancedShufflePlanner.RadioSelection<T>,
    val timing: EnhancedPairTiming<T>,
)

/** Four nearest pairs own their slots until their selection finishes. Each
 * pair can run two radio requests at once, including continuation requests.
 * If no overlap exists even before earlier recommendations are excluded, the
 * second pages can be fetched immediately in parallel with the other pairs.
 * A first-page match may be consumed by an earlier pair, so recheck against
 * the exact ordered exclusions before deciding whether more pages are needed.
 * Decisions and emission remain in playlist order, with no duplicate picks.
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
    val pairSlots = Semaphore(ENHANCED_RADIO_MAX_PARALLEL_PAIRS)
    val orderedExclusions = List(pairs.size + 1) { CompletableDeferred<Set<String>>() }
    orderedExclusions.first().complete(excludedIds.toSet())

    val workers = pairs.mapIndexed { index, pair ->
        // Enter the fair pair-slot queue in playback order, before launching later work.
        async(start = CoroutineStart.UNDISPATCHED) {
            pairSlots.withPermit {
                val admissionMs = started.elapsedNow().inWholeMilliseconds
                if (pair.size != 2) {
                    orderedExclusions[index + 1].complete(orderedExclusions[index].await())
                    null
                } else {
                    val begin = TimeSource.Monotonic.markNow()
                    val (a, b) = coroutineScope {
                        val first = async { fetchPage(pair[0], null) }
                        val second = async { fetchPage(pair[1], null) }
                        first.await() to second.await()
                    }
                    val initialMs = begin.elapsedNow().inWholeMilliseconds
                    val firstA = a.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
                    val firstB = b.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
                    var extraA: EnhancedRadioPage<T>? = null
                    var extraB: EnhancedRadioPage<T>? = null
                    var extensionMs = 0L
                    var extensionAttempted = false

                    suspend fun loadExtra() {
                        if (extensionAttempted) return
                        extensionAttempted = true
                        val tokenA = a.continuation?.takeIf { it.isNotBlank() && firstA.size < ENHANCED_RADIO_MAX_TRACKS }
                        val tokenB = b.continuation?.takeIf { it.isNotBlank() && firstB.size < ENHANCED_RADIO_MAX_TRACKS }
                        if (tokenA == null && tokenB == null) return
                        val extensionStart = TimeSource.Monotonic.markNow()
                        coroutineScope {
                            val requestA = tokenA?.let { token -> async { fetchPage(pair[0], token) } }
                            val requestB = tokenB?.let { token -> async { fetchPage(pair[1], token) } }
                            extraA = requestA?.await()
                            extraB = requestB?.await()
                        }
                        extensionMs = extensionStart.elapsedNow().inWholeMilliseconds
                    }

                    // Exclusions only grow. If no match exists with the original
                    // exclusions, later decisions cannot introduce a match.
                    val potential = EnhancedShufflePlanner.analyzePair(firstA, firstB, excludedIds, id)
                    if (potential.matches.isEmpty()) loadExtra()

                    val orderStart = TimeSource.Monotonic.markNow()
                    val used = orderedExclusions[index].await()
                    val orderWaitMs = orderStart.elapsedNow().inWholeMilliseconds
                    val firstDecision = EnhancedShufflePlanner.analyzePair(firstA, firstB, used, id)
                    if (firstDecision.matches.isEmpty()) loadExtra()

                    val allA = (firstA + extraA?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS)
                    val allB = (firstB + extraB?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS)
                    val decision = EnhancedShufflePlanner.analyzePair(allA, allB, used, id)
                    val recommendation = decision.recommendation
                    orderedExclusions[index + 1].complete(
                        if (recommendation == null) used else used + id(recommendation),
                    )
                    EnhancedPairResult(
                        decision,
                        EnhancedPairTiming(
                            a, b, extraA, extraB, firstDecision.matches.size, initialMs,
                            extensionMs, started.elapsedNow().inWholeMilliseconds,
                            pairAdmissionWaitMs = admissionMs,
                            orderedDecisionWaitMs = orderWaitMs,
                            eligibleRadioA = allA.filter { id(it) !in used }.distinctBy(id),
                            eligibleRadioB = allB.filter { id(it) !in used }.distinctBy(id),
                        ),
                    )
                }
            }
        }
    }

    for (index in pairs.indices) {
        val pair = pairs[index]
        val result = workers[index].await()
        if (result != null) onDecision(pair, result.decision, result.timing)
        onChunk(pair, result?.decision?.recommendation)
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
    val semaphore = enhancedRadioRequestSlots
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
