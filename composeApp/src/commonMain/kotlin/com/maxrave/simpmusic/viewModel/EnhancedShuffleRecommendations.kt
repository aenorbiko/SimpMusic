package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

/** No Enhanced-level timeout in this diagnostic Lab; transport timeouts and
 * coroutine cancellation remain in effect. One semaphore covers every page. */
internal suspend fun fetchEnhancedRadio(
    song: Track,
    songRepository: SongRepository,
    radioSemaphore: Semaphore,
    continuation: String? = null,
): EnhancedRadioPage<Track> {
    val queued = TimeSource.Monotonic.markNow()
    return radioSemaphore.withPermit {
        val waited = queued.elapsedNow().inWholeMilliseconds
        val started = TimeSource.Monotonic.markNow()
        try {
            val result = songRepository.getRadioFromEndpoint(
                YouTubeWatchEndpoint(
                    videoId = song.videoId,
                    playlistId = "RDAMVM${song.videoId}",
                ),
                anonymous = true,
                continuation = continuation,
            ).firstOrNull { it is Resource.Success || it is Resource.Error }
            val data = if (result is Resource.Success) result.data else null
            EnhancedRadioPage(
                tracks = data?.first.orEmpty(),
                continuation = data?.second,
                requestMs = started.elapsedNow().inWholeMilliseconds,
                semaphoreWaitMs = waited,
                status = if (result is Resource.Success) "OK" else "Error or no response",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            EnhancedRadioPage(
                tracks = emptyList(),
                requestMs = started.elapsedNow().inWholeMilliseconds,
                semaphoreWaitMs = waited,
                status = "Request failed",
            )
        }
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

internal fun enhancedRadioMetadata(track: Track, rank: Int): RadioMetadataCandidate =
    RadioMetadataCandidate(
        videoId = track.videoId,
        title = track.title,
        artists = track.artists.orEmpty().map { it.name },
        durationSeconds = track.durationSeconds,
        rank = rank,
    )

internal fun enhancedSongKey(track: Track): String? =
    EnhancedRadioMetadataDiagnostics.strictSongKey(enhancedRadioMetadata(track, 0))

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
    val pageDetails: List<String>,
    val firstPageMatches: Int,
    val initialPairMs: Long,
    val extensionMs: Long,
    val elapsedMs: Long,
    val pairAdmissionWaitMs: Long = 0,
    val orderedDecisionWaitMs: Long = 0,
    val metadataSameTitleCount: Int = 0,
    val metadataVersionCount: Int = 0,
    val metadataSameTitleExamples: List<String> = emptyList(),
    val metadataVersionExamples: List<String> = emptyList(),
    val commonArtistCount: Int = 0,
    val commonArtistDifferentSongCount: Int = 0,
    val commonArtistExamples: List<String> = emptyList(),
    val bestDifferentSongArtistExample: String? = null,
    val beforeExclusionsDetails: List<String> = emptyList(),
    val selectionEvidence: String? = null,
    val fullTrackDetails: String? = null,
) {
    fun asText(): String = buildString {
        appendLine("Enhanced recommendation")
        appendLine("A: $songA")
        appendLine("B: $songB")
        pageDetails.forEach { appendLine(it) }
        appendLine("Radio A: $receivedA considered / $eligibleA eligible")
        appendLine("Radio B: $receivedB considered / $eligibleB eligible")
        appendLine("Common after page 1: $firstPageMatches")
        appendLine("Common after optional page 2: ${commonSongs.size}")
        appendLine("Common with old top 30: $oldTop30Matches")
        appendLine("Pair admission wait: ${pairAdmissionWaitMs} ms")
        appendLine("Initial A+B phase: ${initialPairMs} ms")
        appendLine("Extra page phase: ${extensionMs} ms")
        appendLine("Ordered decision wait: ${orderedDecisionWaitMs} ms")
        appendLine("Decision since batch start: ${elapsedMs} ms")
        appendLine("Source: $source")
        appendLine("Selected: $selectedSong")
        selectionEvidence?.let { appendLine("Selection evidence: $it") }
        fullTrackDetails?.let { appendLine(it) }
        appendLine("Eligible rank A: ${selectedRankA ?: "—"} / B: ${selectedRankB ?: "—"}")
        appendLine("Common songs (eligible ranks):")
        if (commonSongs.isEmpty()) appendLine("None") else commonSongs.forEach { appendLine(it) }
        appendLine("Additional metadata matches (different videoIds; eligible A/B pairs):")
        appendLine("Same normalized title + artist(s): $metadataSameTitleCount")
        metadataSameTitleExamples.forEach { appendLine(it) }
        appendLine("Possible different versions + same artist(s): $metadataVersionCount")
        metadataVersionExamples.forEach { appendLine(it) }
        appendLine("Up to 5 examples per category, ordered by combined eligible rank. Different-version matches remain diagnostic only.")
        appendLine("Shared artists (eligible tracks, individually matched names): $commonArtistCount")
        appendLine("Shared artists with different base song titles: $commonArtistDifferentSongCount")
        commonArtistExamples.forEach { appendLine(it) }
        bestDifferentSongArtistExample?.let { appendLine("Best different-song artist candidate: $it") }
        appendLine("Up to 5 shared artists; up to 3 ranked tracks per radio per artist.")
        beforeExclusionsDetails.forEach { appendLine(it) }
    }.trimEnd()
}

internal fun enhancedRadioDiagnostic(
    pair: List<Track>,
    decision: EnhancedShufflePlanner.RadioSelection<Track>,
    timing: EnhancedPairTiming<Track>,
): EnhancedRadioDiagnostic? {
    val selected = decision.recommendation ?: return null
    if (pair.size != 2) return null
    fun metadata(tracks: List<Track>) = tracks.mapIndexed { index, track -> enhancedRadioMetadata(track, index + 1) }
    val eligibleA = metadata(timing.eligibleRadioA)
    val eligibleB = metadata(timing.eligibleRadioB)
    val metadataReport = EnhancedRadioMetadataDiagnostics.compare(eligibleA, eligibleB)
    val rawA = metadata((timing.firstA.tracks + timing.secondA?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS))
    val rawB = metadata((timing.firstB.tracks + timing.secondB?.tracks.orEmpty()).take(ENHANCED_RADIO_MAX_TRACKS))
    val beforeExclusions = EnhancedRadioMetadataDiagnostics.beforeExclusions(
        rawA, rawB, eligibleA, eligibleB, metadataReport, decision.matches.size,
    )
    fun page(label: String, p: EnhancedRadioPage<Track>?): String = if (p == null) "$label: not requested" else "$label: ${p.tracks.size} received; ${p.requestMs} ms request + ${p.semaphoreWaitMs} ms wait; ${p.status}${if (p.fromCache) " (cache)" else ""}; next page available: ${!p.continuation.isNullOrBlank()}"
    return EnhancedRadioDiagnostic(
        songA = pair[0].title.orEmpty(),
        songB = pair[1].title.orEmpty(),
        selectedSong = selected.title.orEmpty(),
        source = decision.source,
        selectionEvidence = decision.metadataEvidence,
        fullTrackDetails = timing.fullTrack?.asText(),
        receivedA = decision.receivedA,
        receivedB = decision.receivedB,
        eligibleA = decision.eligibleA,
        eligibleB = decision.eligibleB,
        oldTop30Matches = decision.oldTop30Matches,
        selectedRankA = decision.selectedRankA,
        selectedRankB = decision.selectedRankB,
        commonSongs = decision.matches.map { "${it.track.title} (A #${it.rankA}, B #${it.rankB})" },
        pageDetails = listOf(page("A page 1", timing.firstA), page("B page 1", timing.firstB), page("A page 2", timing.secondA), page("B page 2", timing.secondB)),
        firstPageMatches = timing.firstMatches,
        initialPairMs = timing.initialPairMs,
        extensionMs = timing.extensionMs,
        elapsedMs = timing.elapsedMs,
        pairAdmissionWaitMs = timing.pairAdmissionWaitMs,
        orderedDecisionWaitMs = timing.orderedDecisionWaitMs,
        metadataSameTitleCount = metadataReport.sameTitleCount,
        metadataVersionCount = metadataReport.versionCount,
        metadataSameTitleExamples = metadataReport.sameTitleExamples.map { it.asText() },
        metadataVersionExamples = metadataReport.versionExamples.map { it.asText() },
        beforeExclusionsDetails = beforeExclusions,
        commonArtistCount = metadataReport.commonArtistCount,
        commonArtistDifferentSongCount = metadataReport.commonArtistDifferentSongCount,
        commonArtistExamples = metadataReport.commonArtistExamples.map { it.asText() },
        bestDifferentSongArtistExample = metadataReport.bestDifferentSongArtistExample?.let { artist ->
            artist.bestDifferentSong?.let { artist.name + " — " + it.asText() }
        },
    )
}

/**
 * One temporary queue block: 14 liked songs, up to 7 new recommendations.
 *
 * For every pair we query radio for both songs, preferring overlap between the
 * two radios. Calls run at most 8 concurrently; failed radios do not interrupt
 * already playing music. Do not treat this as a YouTube Music two-song API.
 */
internal suspend fun buildEnhancedBatch(
    likedBatch: List<Track>,
    songRepository: SongRepository,
    excludedIds: Set<String>,
    onRadioPageCompleted: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    excludedSongKeys: Set<String> = emptySet(),
    searchRepository: SearchRepository? = null,
    onFullTrackCompleted: (FullTrackDiagnostic) -> Unit = {},
    radioCache: EnhancedRadioCache<Track>? = null,
    performance: EnhancedPerformanceDiagnostics? = null,
): EnhancedBatch {
    val result = mutableListOf<Track>()
    val recommendations = mutableSetOf<String>()
    val diagnostics = mutableMapOf<String, EnhancedRadioDiagnostic>()
    // The same paged selection path as startup; collect the completed prefetch batch.
    buildEnhancedBatchProgressively(likedBatch, songRepository, excludedIds, onRadioPageCompleted, excludedSongKeys, searchRepository, onFullTrackCompleted, radioCache, performance) { chunk ->
        result.addAll(chunk.tracks)
        recommendations.addAll(chunk.recommendedIds)
        diagnostics.putAll(chunk.diagnostics)
    }
    return EnhancedBatch(result, recommendations, diagnostics)
}
