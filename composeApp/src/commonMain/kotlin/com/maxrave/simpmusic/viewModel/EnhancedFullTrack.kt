package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal const val ENHANCED_SHORT_TRACK_SECONDS = 90
private const val MIN_EXTRA_SECONDS = 30
private const val FULL_TRACK_SEARCH_TIMEOUT_MS = 4_000L

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
            duration != null && duration >= ENHANCED_SHORT_TRACK_SECONDS &&
            duration - seconds >= MIN_EXTRA_SECONDS &&
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
): Track {
    val duration = original.durationSeconds ?: return original
    if (duration !in 1 until ENHANCED_SHORT_TRACK_SECONDS) return original
    if (enhancedSongKey(original) == null) return original
    val query = (listOf(original.title) + original.artists.orEmpty().map { it.name }).joinToString(" ")
    return try {
        withTimeoutOrNull(FULL_TRACK_SEARCH_TIMEOUT_MS) {
            requestSlots.withPermit {
                val result = searchRepository.getSearchDataSong(query)
                    .firstOrNull { it is Resource.Success || it is Resource.Error }
                val songs = if (result is Resource.Success) result.data.orEmpty() else emptyList()
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
        } ?: original
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        original
    }
}
