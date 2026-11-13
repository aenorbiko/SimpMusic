package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Creates a finite 2-liked + 1-recommended queue without modifying favorites.
 *
 * The seeds come from pairs spread across the *whole shuffled liked list*, rather
 * than the first few playing songs. Radio results are consumed round-robin instead
 * of in long blocks from a single station. This improves source diversity but does
 * NOT guarantee that every suggestion matches its immediately preceding liked pair.
 *
 * At most 12 YouTube Music radio requests are made, not one per liked pair.
 */
internal suspend fun buildEnhancedQueue(
    liked: List<Track>,
    songRepository: SongRepository,
): List<Track> {
    val owned = liked.distinctBy { it.videoId }.shuffled()
    val pairCount = owned.size / 2
    if (pairCount == 0) return owned

    val requested = pairCount
    val seedCount = minOf(12, pairCount)
    val pairs = owned.chunked(2)
    val seeds = (0 until seedCount).map { seedIndex ->
        val pairIndex = seedIndex * pairCount / seedCount
        // Pick one member of each well-spaced pair as a radio seed.
        pairs[pairIndex].random()
    }
    val ownedIds = owned.mapTo(hashSetOf()) { it.videoId }
    val pools = ArrayList<List<Track>>(seedCount)

    for (seed in seeds) {
        val response = withTimeoutOrNull(8_000L) {
            songRepository.getRadioFromEndpoint(
                YouTubeWatchEndpoint(
                    videoId = seed.videoId,
                    playlistId = "RDAMVM${seed.videoId}",
                ),
            ).firstOrNull { it is Resource.Success }
        }
        // Each station can contain a seed song or tracks already liked.
        val candidates = response?.data?.first.orEmpty()
            .asSequence()
            .filter { it.videoId !in ownedIds }
            .distinctBy { it.videoId }
            .toList()
            .shuffled()
        if (candidates.isNotEmpty()) pools.add(candidates)
    }

    val recommended = EnhancedShufflePlanner.roundRobinRecommendations(
        pools = pools,
        excludedIds = ownedIds,
        limit = requested,
        id = { it.videoId },
    )
    return EnhancedShufflePlanner.mix(owned, recommended) { it.videoId }
}
