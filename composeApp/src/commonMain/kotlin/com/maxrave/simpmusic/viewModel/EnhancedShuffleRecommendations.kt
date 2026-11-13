package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Builds a finite queue from liked songs and existing YouTube Music radio recommendations.
 * Up to eight seeds; on unavailable radio gracefully degrades to ordinary shuffle.
 */
internal suspend fun buildEnhancedQueue(
    liked: List<Track>,
    songRepository: SongRepository,
): List<Track> {
    val owned = liked.distinctBy { it.videoId }.shuffled()
    if (owned.size < 2) return owned

    val wanted = owned.size / 2
    val seen = owned.mapTo(mutableSetOf()) { it.videoId }
    val recommendations = ArrayList<Track>(wanted)

    for (seed in owned.shuffled().take(8)) {
        if (recommendations.size >= wanted) break
        val response = withTimeoutOrNull(8_000L) {
            songRepository.getRadioFromEndpoint(
                YouTubeWatchEndpoint(
                    videoId = seed.videoId,
                    playlistId = "RDAMVM${seed.videoId}",
                ),
            ).firstOrNull { it is Resource.Success }
        }
        response?.data?.first.orEmpty().forEach { candidate ->
            if (seen.add(candidate.videoId) && recommendations.size < wanted) {
                recommendations.add(candidate)
            }
        }
    }
    return EnhancedShufflePlanner.mix(owned, recommendations) { it.videoId }
}
