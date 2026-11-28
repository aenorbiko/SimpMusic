package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.SearchRepository
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
    val fromCache: Boolean = false,
    val retryFirst: Boolean = false,
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
    val fullTrack: FullTrackDiagnostic? = null,
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
    chooseFinal: (List<T>, List<T>, Set<String>, (T) -> String) -> EnhancedShufflePlanner.RadioSelection<T> =
        { a, b, used, key -> EnhancedShufflePlanner.analyzePair(a, b, used, key) },
    excludedSongKeys: Set<String> = emptySet(),
    songKey: (T) -> String? = { null },
    onDecision: (List<T>, EnhancedShufflePlanner.RadioSelection<T>, EnhancedPairTiming<T>) -> Unit = { _, _, _ -> },
    resolveRecommendation: suspend (T, Set<String>) -> EnhancedResolvedRecommendation<T> = { selected, _ -> EnhancedResolvedRecommendation(selected) },
    refreshFirst: (suspend (T) -> EnhancedRadioPage<T>)? = null,
    onChunk: suspend (sourcePair: List<T>, recommended: T?) -> Unit,
) = coroutineScope {
    val pairs = seeds.chunked(2)
    val started = TimeSource.Monotonic.markNow()
    val pairSlots = Semaphore(ENHANCED_RADIO_MAX_PARALLEL_PAIRS)
    val orderedExclusions = List(pairs.size + 1) { CompletableDeferred<Pair<Set<String>, Set<String>>>() }
    orderedExclusions.first().complete(excludedIds.toSet() to excludedSongKeys.toSet())

    // Translate strict metadata exclusions to IDs for each fetched list. This
    // preserves the existing rank calculation and raw-page diagnostics.
    fun blockedIds(a: List<T>, b: List<T>, ids: Set<String>, keys: Set<String>): Set<String> {
        if (keys.isEmpty()) return ids
        return ids + (a + b).filter { songKey(it) in keys }.map(id)
    }

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
                    val initial = coroutineScope {
                        val first = async { fetchPage(pair[0], null) }
                        val second = async { fetchPage(pair[1], null) }
                        first.await() to second.await()
                    }
                    var a = initial.first
                    var b = initial.second
                    val initialMs = begin.elapsedNow().inWholeMilliseconds
                    var firstA = a.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
                    var firstB = b.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
                    var extraA: EnhancedRadioPage<T>? = null
                    var extraB: EnhancedRadioPage<T>? = null
                    var extensionMs = 0L
                    var attemptedA = false
                    var attemptedB = false

                    suspend fun requestMissingPages() = coroutineScope {
                        val tokenA = a.continuation?.takeIf { !attemptedA && it.isNotBlank() && firstA.size < ENHANCED_RADIO_MAX_TRACKS }
                        val tokenB = b.continuation?.takeIf { !attemptedB && it.isNotBlank() && firstB.size < ENHANCED_RADIO_MAX_TRACKS }
                        val requestA = tokenA?.let { token -> async { fetchPage(pair[0], token) } }
                        val requestB = tokenB?.let { token -> async { fetchPage(pair[1], token) } }
                        if (requestA != null) { attemptedA = true; extraA = requestA.await() }
                        if (requestB != null) { attemptedB = true; extraB = requestB.await() }
                    }

                    suspend fun loadExtra(ids: Set<String> = excludedIds, keys: Set<String> = excludedSongKeys) {
                        val extensionStart = TimeSource.Monotonic.markNow()
                        requestMissingPages()
                        val refresh = refreshFirst
                        if (refresh != null) {
                            // Only a failed deferred token may trigger recovery. Fresh-token
                            // failures retain the usual fallback, with no recursive retries.
                            val recoverA = extraA?.retryFirst == true
                            val recoverB = extraB?.retryFirst == true
                            if (recoverA || recoverB) {
                                val fresh = coroutineScope {
                                    val ra = if (recoverA) async { refresh(pair[0]) } else null
                                    val rb = if (recoverB) async { refresh(pair[1]) } else null
                                    ra?.await() to rb?.await()
                                }
                                if (recoverA) {
                                    extraA = null
                                    val page = fresh.first
                                    if (page != null && page.status == "OK" && page.tracks.isNotEmpty()) {
                                        a = page
                                        firstA = a.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
                                        attemptedA = false
                                    }
                                }
                                if (recoverB) {
                                    extraB = null
                                    val page = fresh.second
                                    if (page != null && page.status == "OK" && page.tracks.isNotEmpty()) {
                                        b = page
                                        firstB = b.tracks.take(ENHANCED_RADIO_MAX_TRACKS)
                                        attemptedB = false
                                    }
                                }
                                // Recheck the new first pages before fetching new continuations.
                                // If refresh fails, retain the original first page for this pair only.
                                val refreshed = EnhancedShufflePlanner.analyzePair(firstA, firstB, blockedIds(firstA, firstB, ids, keys), id)
                                if (refreshed.matches.isEmpty()) requestMissingPages()
                            }
                        }
                        extensionMs += extensionStart.elapsedNow().inWholeMilliseconds
                    }

                    // Exclusions only grow. If no match exists with the original
                    // exclusions, later decisions cannot introduce a match.
                    val potential = EnhancedShufflePlanner.analyzePair(firstA, firstB, blockedIds(firstA, firstB, excludedIds, excludedSongKeys), id)
                    if (potential.matches.isEmpty()) loadExtra()

                    val orderStart = TimeSource.Monotonic.markNow()
                    val (usedIds, usedKeys) = orderedExclusions[index].await()
                    val orderWaitMs = orderStart.elapsedNow().inWholeMilliseconds
                    var firstDecision = EnhancedShufflePlanner.analyzePair(firstA, firstB, blockedIds(firstA, firstB, usedIds, usedKeys), id)
                    if (firstDecision.matches.isEmpty()) {
                        loadExtra(usedIds, usedKeys)
                        firstDecision = EnhancedShufflePlanner.analyzePair(firstA, firstB, blockedIds(firstA, firstB, usedIds, usedKeys), id)
                    }

                    val allA = (firstA + extraA?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS)
                    val allB = (firstB + extraB?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS)
                    val used = blockedIds(allA, allB, usedIds, usedKeys)
                    val decision = chooseFinal(allA, allB, used, id)
                    val original = decision.recommendation
                    val resolution = original?.let { resolveRecommendation(it, usedIds) }
                    val recommendation = resolution?.track
                    val resolvedDecision = if (recommendation != null && original != null && id(recommendation) != id(original)) {
                        decision.copy(
                            recommendation = recommendation,
                            metadataEvidence = listOfNotNull(decision.metadataEvidence, "Full track replacement: ${id(original)} → ${id(recommendation)}").joinToString("; "),
                        )
                    } else decision
                    orderedExclusions[index + 1].complete(
                        if (recommendation == null) usedIds to usedKeys else
                            (usedIds + id(recommendation) + listOfNotNull(original?.let(id))) to (usedKeys + listOfNotNull(songKey(recommendation), original?.let(songKey))),
                    )
                    EnhancedPairResult(
                        resolvedDecision,
                        EnhancedPairTiming(
                            a, b, extraA, extraB, firstDecision.matches.size, initialMs,
                            extensionMs, started.elapsedNow().inWholeMilliseconds,
                            pairAdmissionWaitMs = admissionMs,
                            orderedDecisionWaitMs = orderWaitMs,
                            eligibleRadioA = allA.filter { id(it) !in used }.distinctBy(id),
                            eligibleRadioB = allB.filter { id(it) !in used }.distinctBy(id),
                            fullTrack = resolution?.fullTrack,
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
    onRadioPageCompleted: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    excludedSongKeys: Set<String> = emptySet(),
    searchRepository: SearchRepository? = null,
    onFullTrackCompleted: (FullTrackDiagnostic) -> Unit = {},
    radioCache: EnhancedRadioCache<Track>? = null,
    onChunkReady: suspend (EnhancedBatch) -> Unit,
) {
    val semaphore = enhancedRadioRequestSlots
    val cacheBatch = radioCache?.newBatch()
    var diagnostic: EnhancedRadioDiagnostic? = null
    emitPagedEnhancedPairs(
        seeds = likedBatch,
        excludedIds = excludedIds,
        id = { it.videoId },
        fetchPage = { track, token ->
            val network: suspend () -> EnhancedRadioPage<Track> = {
                val page = fetchEnhancedRadio(track, songRepository, semaphore, token)
                onRadioPageCompleted(track.videoId, token != null, page.status == "OK" && page.tracks.isNotEmpty())
                page
            }
            if (cacheBatch == null) network() else cacheBatch.load(track.videoId, token, network)
        },
        refreshFirst = cacheBatch?.let { batch ->
            { track: Track ->
                batch.refreshFirst(track.videoId) {
                    val page = fetchEnhancedRadio(track, songRepository, semaphore, null)
                    onRadioPageCompleted(track.videoId, false, page.status == "OK" && page.tracks.isNotEmpty())
                    page
                }
            }
        },
        chooseFinal = { a, b, used, id -> EnhancedShufflePlanner.analyzePairWithMetadata(a, b, used, id, ::enhancedRadioMetadata) },
        excludedSongKeys = excludedSongKeys,
        songKey = ::enhancedSongKey,
        resolveRecommendation = { selected, used ->
            if (searchRepository == null) EnhancedResolvedRecommendation(selected) else
                resolveEnhancedFullTrack(selected, used, searchRepository, semaphore).also { resolved ->
                    resolved.fullTrack?.let(onFullTrackCompleted)
                }
        },
        onDecision = { pair, decision, timing -> diagnostic = enhancedRadioDiagnostic(pair, decision, timing) },
    ) { pair, recommendation ->
        val tracks = if (recommendation == null) pair else pair + recommendation
        val recommendationIds = recommendation?.let { setOf(it.videoId) }.orEmpty()
        val details = diagnostic?.let { d -> recommendation?.let { mapOf(it.videoId to d) } }.orEmpty()
        diagnostic = null
        onChunkReady(EnhancedBatch(tracks, recommendationIds, details))
    }
}
