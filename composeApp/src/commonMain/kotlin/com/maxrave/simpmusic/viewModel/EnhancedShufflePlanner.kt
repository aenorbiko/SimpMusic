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

    /** Compare every eligible track already returned by the two radio requests.
     * The caller may extend its own lists; preserve combined rank and B-only fallback.
     * Positions are one-based ranks after excluding owned/used tracks and duplicate IDs.
     */
    data class RadioMatch<T>(val track: T, val rankA: Int, val rankB: Int)
    data class RadioSelection<T>(
        val recommendation: T?,
        val receivedA: Int,
        val receivedB: Int,
        val eligibleA: Int,
        val eligibleB: Int,
        val matches: List<RadioMatch<T>>,
        val selectedRankA: Int?,
        val selectedRankB: Int?,
        val metadataMethod: String? = null,
        val metadataEvidence: String? = null,
        val metadataReport: RadioMetadataReport? = null,
    ) {
        val source: String get() = when {
            recommendation == null -> "None"
            metadataMethod != null -> metadataMethod
            selectedRankA != null && selectedRankB != null -> "A+B"
            else -> "B"
        }
        val oldTop30Matches: Int get() = matches.count { it.rankA <= 30 && it.rankB <= 30 }
    }

    fun <T> analyzePair(
        firstRadio: List<T>,
        secondRadio: List<T>,
        usedIds: Set<String>,
        id: (T) -> String,
    ): RadioSelection<T> {
        val a = firstRadio.filter { id(it) !in usedIds }.distinctBy(id)
        val b = secondRadio.filter { id(it) !in usedIds }.distinctBy(id)
        val firstPosition = a.mapIndexed { index, candidate -> id(candidate) to index + 1 }.toMap()
        val matches = b.mapIndexedNotNull { index, candidate ->
            val rankA = firstPosition[id(candidate)] ?: return@mapIndexedNotNull null
            RadioMatch(candidate, rankA, index + 1)
        }.sortedWith(compareBy<RadioMatch<T>> { it.rankA + it.rankB }.thenBy { it.rankB }.thenBy { it.rankA })
        val best = matches.firstOrNull()
        val pick = best?.track ?: b.firstOrNull()
        return RadioSelection(
            recommendation = pick,
            receivedA = firstRadio.size,
            receivedB = secondRadio.size,
            eligibleA = a.size,
            eligibleB = b.size,
            matches = matches,
            selectedRankA = best?.rankA,
            selectedRankB = best?.rankB ?: if (pick != null) 1 else null,
        )
    }

    /** Final choice after the existing page-loading policy has run.
     * The exact videoId overlap remains absolute priority. The two additional
     * tiers only consider eligible tracks, select the B-side track, and never
     * promote the looser different-version diagnostic category.
     */
    fun <T> analyzePairWithMetadata(
        firstRadio: List<T>,
        secondRadio: List<T>,
        usedIds: Set<String>,
        id: (T) -> String,
        metadata: (T, Int) -> RadioMetadataCandidate,
    ): RadioSelection<T> {
        val exact = analyzePair(firstRadio, secondRadio, usedIds, id)
        if (exact.matches.isNotEmpty() || exact.recommendation == null) return exact
        val a = firstRadio.filter { id(it) !in usedIds }.distinctBy(id)
        val b = secondRadio.filter { id(it) !in usedIds }.distinctBy(id)
        val report = EnhancedRadioMetadataDiagnostics.compare(
            a.mapIndexed { index, track -> metadata(track, index + 1) },
            b.mapIndexed { index, track -> metadata(track, index + 1) },
        )
        val analyzed = exact.copy(metadataReport = report)
        val titleMatch = report.sameTitleExamples.firstOrNull()
        val artist = report.bestDifferentSongArtistExample
        val match = titleMatch ?: artist?.bestDifferentSong ?: return analyzed
        val selected = b.firstOrNull { id(it) == match.b.videoId } ?: return analyzed
        return analyzed.copy(
            recommendation = selected,
            selectedRankA = match.a.rank,
            selectedRankB = match.b.rank,
            metadataMethod = if (titleMatch != null) "A+B (title + artist)" else "A+B (shared artist)",
            metadataEvidence = if (titleMatch != null) match.asText() else artist!!.name + " — " + match.asText(),
        )
    }

    fun <T> selectForPair(
        firstRadio: List<T>,
        secondRadio: List<T>,
        usedIds: Set<String>,
        id: (T) -> String,
    ): T? = analyzePair(firstRadio, secondRadio, usedIds, id).recommendation

    /**
     * Pick at most one new recommendation for each pair, never duplicating
     * a source song or another recommendation in the current batch.
     * A missing second-song radio yields no third song for that pair.
     */
    fun <T> selectForAllPairs(
        radioPairs: List<Pair<List<T>, List<T>>>,
        excludedIds: Set<String>,
        id: (T) -> String,
        onDecision: (Int, RadioSelection<T>) -> Unit = { _, _ -> },
    ): List<T?> {
        val used = excludedIds.toMutableSet()
        return radioPairs.mapIndexed { index, (first, second) ->
            val decision = analyzePair(first, second, used, id)
            onDecision(index, decision)
            decision.recommendation?.also { used.add(id(it)) }
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
