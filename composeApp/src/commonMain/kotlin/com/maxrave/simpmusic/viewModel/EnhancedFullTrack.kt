package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

internal const val ENHANCED_SHORT_TRACK_SECONDS = 90
private const val FULL_TRACK_SEARCH_TIMEOUT_MS = 10_000L

internal enum class FullTrackOutcome { REPLACED, NO_MATCH, TIMEOUT, ERROR, MISSING_METADATA }

internal data class FullTrackDiagnostic(
    val originalSeconds: Int,
    val replacementSeconds: Int?,
    val outcome: FullTrackOutcome,
    val elapsedMs: Long,
    val requestMs: Long?,
) {
    fun asText(): String = buildString {
        appendLine("Full Track resolution")
        appendLine("Original duration: ${originalSeconds}s")
        appendLine("Replacement duration: ${replacementSeconds?.let { "${it}s" } ?: "—"}")
        appendLine("Search time including slot wait: ${elapsedMs} ms")
        appendLine("Search request time: ${requestMs?.let { "${it} ms" } ?: "not started"}")
        append("Result: ${outcome.name}")
    }
}

internal data class EnhancedResolvedRecommendation<T>(
    val track: T,
    val fullTrack: FullTrackDiagnostic? = null,
)

internal fun <T> selectEnhancedFullTrack(
    original: T,
    candidates: List<T>,
    excludedIds: Set<String>,
    metadata: (T) -> RadioMetadataCandidate,
): T {
    val source = metadata(original)
    val seconds = source.durationSeconds ?: return original
    if (seconds !in 1 until ENHANCED_SHORT_TRACK_SECONDS) return original
    val key = EnhancedRadioMetadataDiagnostics.strictSongKey(source) ?: return original
    return candidates.firstOrNull { candidate ->
        val item = metadata(candidate)
        val duration = item.durationSeconds
        item.videoId.isNotBlank() && item.videoId != source.videoId &&
            item.videoId !in excludedIds &&
            duration != null && duration.toLong() * 4 >= seconds.toLong() * 5 &&
            EnhancedRadioMetadataDiagnostics.strictSongKey(item) == key
    } ?: original
}

/** Only selected short recommendations reach this lookup; normal tracks never search.
 * Use the Songs search, not videos; preserve exact title and complete artist identity.
 * Search failure, timeout, or ambiguous/missing metadata leaves the original intact.
 */
internal suspend fun resolveEnhancedFullTrack(
    original: Track,
    excludedIds: Set<String>,
    searchRepository: SearchRepository,
    requestSlots: Semaphore,
): EnhancedResolvedRecommendation<Track> {
    val duration = original.durationSeconds ?: return EnhancedResolvedRecommendation(original)
    if (duration !in 1 until ENHANCED_SHORT_TRACK_SECONDS) return EnhancedResolvedRecommendation(original)
    if (enhancedSongKey(original) == null) return EnhancedResolvedRecommendation(
        original, FullTrackDiagnostic(duration, null, FullTrackOutcome.MISSING_METADATA, 0, null),
    )
    val start = TimeSource.Monotonic.markNow()
    var requestStart: kotlin.time.TimeMark? = null
    var requestMs: Long? = null
    var outcome = FullTrackOutcome.NO_MATCH
    val query = (listOf(original.title) + original.artists.orEmpty().map { it.name }).joinToString(" ")
    val replacement = try {
        val found = withTimeoutOrNull(FULL_TRACK_SEARCH_TIMEOUT_MS) {
            requestSlots.withPermit {
                requestStart = TimeSource.Monotonic.markNow()
                val result = try {
                    searchRepository.getSearchDataSong(query)
                        .firstOrNull { it is Resource.Success || it is Resource.Error }
                } finally {
                    requestMs = requestStart?.elapsedNow()?.inWholeMilliseconds
                }
                if (result !is Resource.Success) {
                    outcome = FullTrackOutcome.ERROR
                    return@withPermit original
                }
                val songs = result.data.orEmpty()
                val candidates = songs.filter {
                    // The Songs endpoint may omit videoType; never accept an explicitly non-audio result.
                    it.videoType == null || it.videoType == "MUSIC_VIDEO_TYPE_ATV"
                }.mapNotNull { song ->
                    val title = song.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Track(
                        album = song.album, artists = song.artists,
                        duration = song.duration, durationSeconds = song.durationSeconds,
                        isAvailable = true, isExplicit = song.isExplicit == true, likeStatus = null,
                        thumbnails = song.thumbnails, title = title, videoId = song.videoId,
                        videoType = song.videoType, category = song.category,
                        feedbackTokens = song.feedbackTokens, resultType = song.resultType,
                    )
                }
                selectEnhancedFullTrack(original, candidates, excludedIds) { enhancedRadioMetadata(it, 0) }
            }
        }
        if (found == null) {
            outcome = FullTrackOutcome.TIMEOUT
            original
        } else found
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        outcome = FullTrackOutcome.ERROR
        original
    }
    val changed = replacement.videoId != original.videoId
    if (changed) outcome = FullTrackOutcome.REPLACED
    return EnhancedResolvedRecommendation(
        replacement,
        FullTrackDiagnostic(duration, replacement.durationSeconds.takeIf { changed }, outcome,
            start.elapsedNow().inWholeMilliseconds, requestMs),
    )
}
