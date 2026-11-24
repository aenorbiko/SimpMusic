package com.maxrave.simpmusic.viewModel

/** Mix two liked tracks followed by one radio recommendation; never mutate the playlist. */
internal object EnhancedShufflePlanner {
    /**
     * When Enhanced is enabled mid-song, the playing liked song is the FIRST
     * member of the opening 2+1 pair. It is a radio seed, not a new queue item.
     * Keep the total number of liked seeds per block at [batchSize], so later
     * batches continue on complete two-song boundaries.
     */
    data class FirstBlock<T>(
        val radioSeeds: List<T>,
        val upcomingLikedCount: Int,
        val hasCurrentSeed: Boolean,
    )

    fun <T> firstBlock(
        currentLiked: T?,
        shuffledUpcoming: List<T>,
        batchSize: Int,
        id: (T) -> String,
    ): FirstBlock<T> {
        require(batchSize > 0)
        val upcoming = if (currentLiked == null) shuffledUpcoming
            else shuffledUpcoming.filterNot { id(it) == id(currentLiked) }
        val count = (batchSize - if (currentLiked != null) 1 else 0).coerceAtLeast(0)
        val firstUpcoming = upcoming.take(count)
        return FirstBlock(
            radioSeeds = if (currentLiked == null) firstUpcoming else listOf(currentLiked) + firstUpcoming,
            upcomingLikedCount = firstUpcoming.size,
            hasCurrentSeed = currentLiked != null,
        )
    }

    /**
     * Take one distinct suggestion per radio station per pass.
     * This avoids long runs of recommendations sourced from a single seed song.
     */
    fun <T> roundRobinRecommendations(
        pools: List<List<T>>,
        excludedIds: Set<String>,
        limit: Int,
        id: (T) -> String,
    ): List<T> {
        if (limit <= 0) return emptyList()
        val iterators = pools.map { it.iterator() }
        val seen = excludedIds.toMutableSet()
        val result = ArrayList<T>(limit)
        var found: Boolean
        do {
            found = false
            for (iterator in iterators) {
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (seen.add(id(candidate))) {
                        result.add(candidate)
                        found = true
                        break
                    }
                }
                if (result.size == limit) return result
            }
        } while (found)
        return result
    }

    /**
     * First remove source-collection songs, already used recommendations and
     * duplicate IDs, then take up to 15 remaining tracks from each radio.
     * If a song appears in both, prefer the smallest combined rank (ties
     * favour the radio of the second, most recently heard source song).
     *
     * When no common track exists, use the first unused candidate from the
     * second song's radio, not the first song's or another pair's radio.
     * No suitable candidate => skip this pair's recommendation.
     */
    private const val RADIO_CANDIDATE_LIMIT = 15

    fun <T> selectForPair(
        firstRadio: List<T>,
        secondRadio: List<T>,
        usedIds: Set<String>,
        id: (T) -> String,
    ): T? {
        val a = firstRadio
            .filter { id(it) !in usedIds }
            .distinctBy(id)
            .take(RADIO_CANDIDATE_LIMIT)
        val b = secondRadio
            .filter { id(it) !in usedIds }
            .distinctBy(id)
            .take(RADIO_CANDIDATE_LIMIT)
        val firstPosition = a.mapIndexed { index, candidate -> id(candidate) to index }.toMap()
        val overlap = b.mapIndexedNotNull { secondPosition, candidate ->
            val positionA = firstPosition[id(candidate)] ?: return@mapIndexedNotNull null
            Triple(candidate, positionA, secondPosition)
        }
        if (overlap.isNotEmpty()) {
            return overlap.minWithOrNull(
                compareBy<Triple<T, Int, Int>> { it.second + it.third }
                    .thenBy { it.third }
                    .thenBy { it.second },
            )?.first
        }
        return b.firstOrNull()
    }

    /**
     * Pick at most one new recommendation for each pair, never duplicating
     * a source song or another recommendation in the current batch.
     * A missing second-song radio yields no third song for that pair.
     */
    fun <T> selectForAllPairs(
        radioPairs: List<Pair<List<T>, List<T>>>,
        excludedIds: Set<String>,
        id: (T) -> String,
    ): List<T?> {
        val used = excludedIds.toMutableSet()
        return radioPairs.map { (first, second) ->
            selectForPair(
                firstRadio = first,
                secondRadio = second,
                usedIds = used,
                id = id,
            )?.also { used.add(id(it)) }
        }
    }

    fun <T> mix(
        liked: List<T>,
        recommended: List<T>,
        id: (T) -> String,
    ): List<T> {
        val likedUnique = liked.distinctBy(id)
        val existingIds = likedUnique.map(id).toHashSet()
        val recommendations = recommended.filter { existingIds.add(id(it)) }
        val result = ArrayList<T>(likedUnique.size + minOf(recommendations.size, likedUnique.size / 2))
        var recommendationIndex = 0
        likedUnique.forEachIndexed { index, track ->
            result.add(track)
            if ((index + 1) % 2 == 0 && recommendationIndex < recommendations.size) {
                result.add(recommendations[recommendationIndex++])
            }
        }
        return result
    }
}
