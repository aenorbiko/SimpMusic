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
     * Prefer tracks recommended by BOTH preceding liked songs, then fall back to
     * either individual radio. This is a proxy, not a true two-song similarity score.
     */
    fun <T> selectForPair(
        firstRadio: List<T>,
        secondRadio: List<T>,
        usedIds: Set<String>,
        id: (T) -> String,
        preferSecondOnFallback: Boolean = false,
    ): T? {
        val a = firstRadio.filter { id(it) !in usedIds }.distinctBy(id)
        val b = secondRadio.filter { id(it) !in usedIds }.distinctBy(id)
        val secondIds = b.mapTo(hashSetOf(), id)
        val intersection = a.filter { id(it) in secondIds }
        if (intersection.isNotEmpty()) return intersection.first()

        val preferred = if (preferSecondOnFallback) b else a
        val alternative = if (preferSecondOnFallback) a else b
        return preferred.firstOrNull() ?: alternative.firstOrNull()
    }

    /**
     * Do not silently skip a 2+1 slot when both radios for that pair are empty
     * (or only contain already-liked tracks). Prefer the pair's own radio;
     * otherwise borrow an UNUSED recommendation from the nearest subsequent
     * pair's radio. This preserves the 2+1 cadence whenever any candidate exists.
     *
     * If every fetched radio is empty, no candidate can safely be invented.
     */
    fun <T> selectForAllPairs(
        radioPairs: List<Pair<List<T>, List<T>>>,
        excludedIds: Set<String>,
        id: (T) -> String,
    ): List<T?> {
        val used = excludedIds.toMutableSet()
        return radioPairs.mapIndexed { index, (first, second) ->
            var candidate = selectForPair(
                firstRadio = first,
                secondRadio = second,
                usedIds = used,
                id = id,
                preferSecondOnFallback = index % 2 == 1,
            )
            if (candidate == null) {
                val neighbours = radioPairs.indices
                    .filter { it != index }
                    .sortedWith(compareBy<Int> { kotlin.math.abs(it - index) }.thenBy { it })
                for (nearby in neighbours) {
                    val (otherFirst, otherSecond) = radioPairs[nearby]
                    candidate = selectForPair(
                        firstRadio = otherFirst,
                        secondRadio = otherSecond,
                        usedIds = used,
                        id = id,
                        preferSecondOnFallback = index % 2 == 1,
                    )
                    if (candidate != null) break
                }
            }
            candidate?.also { used.add(id(it)) }
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
