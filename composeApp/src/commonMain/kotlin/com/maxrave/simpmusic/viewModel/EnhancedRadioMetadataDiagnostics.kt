package com.maxrave.simpmusic.viewModel

/** Read-only diagnostic input: retain original titles and artists for review. */
internal data class RadioMetadataCandidate(
    val videoId: String,
    val title: String,
    val artists: List<String>,
    val durationSeconds: Int?,
    val rank: Int,
)

internal data class RadioMetadataMatch(
    val a: RadioMetadataCandidate,
    val b: RadioMetadataCandidate,
) {
    fun asText(): String {
        fun describe(track: RadioMetadataCandidate): String =
            "#" + track.rank + " " + track.title + " — " + track.artists.joinToString(", ") +
                " [" + (track.durationSeconds?.let { (it / 60).toString() + ":" + (it % 60).toString().padStart(2, '0') } ?: "?") + "]"
        val difference = if (a.durationSeconds != null && b.durationSeconds != null)
            "; duration difference " + kotlin.math.abs(a.durationSeconds - b.durationSeconds) + " s"
        else ""
        return "A " + describe(a) + " | B " + describe(b) + difference
    }
}

/** Counts distinct eligible video IDs for each normalized artist, not pair combinations. */
internal data class RadioArtistMatch(
    val name: String,
    val tracksA: List<RadioMetadataCandidate>,
    val tracksB: List<RadioMetadataCandidate>,
    val bestDifferentSong: RadioMetadataMatch?,
) {
    fun asText(): String {
        fun examples(tracks: List<RadioMetadataCandidate>): String =
            tracks.take(3).joinToString("; ") { "#" + it.rank + " " + it.title }
        val different = bestDifferentSong?.let {
            " | different titles: A #" + it.a.rank + " " + it.a.title +
                " / B #" + it.b.rank + " " + it.b.title
        } ?: " | no distinct base-title pair"
        return name + " — A: " + tracksA.size + " tracks [" + examples(tracksA) +
            "] | B: " + tracksB.size + " tracks [" + examples(tracksB) + "]" + different
    }
}

internal data class RadioMetadataReport(
    val sameTitleCount: Int,
    val versionCount: Int,
    val sameTitleExamples: List<RadioMetadataMatch>,
    val versionExamples: List<RadioMetadataMatch>,
    val commonArtistCount: Int,
    val commonArtistDifferentSongCount: Int,
    val commonArtistExamples: List<RadioArtistMatch>,
    val bestDifferentSongArtistExample: RadioArtistMatch?,
)

internal object EnhancedRadioMetadataDiagnostics {
    private const val EXAMPLE_LIMIT = 5
    // Only remove explicit trailing presentation labels, never remix names here.
    private const val PRESENTATION = """(?:official\s+(?:music\s+)?(?:video|audio|visualizer)|(?:official\s+)?lyric(?:s|\s+video)|music\s+video|visualizer|hd|4k|hq)"""
    private const val VERSION = """(?:[^()\[\]]*\b(?:remix|remaster(?:ed)?|version|edit|mix|acoustic|live|instrumental|radio\s+edit|sped\s+up|slowed(?:\s+down)?|rework|bootleg)\b[^()\[\]]*)"""
    private fun suffix(label: String) = Regex(
        """(?:\s*\(\s*""" + label + """\s*\)|\s*\[\s*""" + label +
            """\s*\]|\s+[-–—|]\s*""" + label + """)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val presentationSuffix = suffix(PRESENTATION)
    private val versionSuffix = suffix(VERSION)
    private fun stripSuffixes(title: String, pattern: Regex): String {
        var value = title.trim()
        while (true) {
            val next = pattern.replace(value, "").trim()
            if (next == value) return value
            value = next
        }
    }
    private fun normalized(value: String): String = buildString {
        value.lowercase().forEach { char ->
            when {
                char.isLetterOrDigit() -> append(char)
                char == '\'' || char == '’' -> Unit
                else -> append(' ')
            }
        }
    }.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ")

    private data class Identity(val title: String, val artists: List<String>)
    private data class Indexed(
        val track: RadioMetadataCandidate,
        val strict: Identity,
        val base: Identity,
    )
    private fun indexed(candidate: RadioMetadataCandidate): Indexed? {
        val artists = candidate.artists.map(::normalized).filter { it.isNotBlank() }.distinct().sorted()
        if (artists.isEmpty()) return null // Never infer artist identity from title alone.
        val presentationClean = stripSuffixes(candidate.title, presentationSuffix)
        val strict = normalized(presentationClean)
        val base = normalized(stripSuffixes(presentationClean, versionSuffix))
        if (strict.isBlank() || base.isBlank()) return null
        return Indexed(candidate, Identity(strict, artists), Identity(base, artists))
    }

    /** Artist names are matched individually, including collaborators explicitly
     * listed in metadata. Do not split channel names or guess from video titles.
     */
    private fun artistGroups(tracks: List<RadioMetadataCandidate>): Map<String, List<RadioMetadataCandidate>> =
        tracks.distinctBy { it.videoId }.sortedBy { it.rank }
            .flatMap { track -> track.artists.map(::normalized).filter { it.isNotBlank() }.distinct().map { it to track } }
            .groupBy({ it.first }, { it.second })

    private fun baseTitle(track: RadioMetadataCandidate): String =
        normalized(stripSuffixes(stripSuffixes(track.title, presentationSuffix), versionSuffix))

    private fun sharedArtists(a: List<RadioMetadataCandidate>, b: List<RadioMetadataCandidate>): List<RadioArtistMatch> {
        val groupsA = artistGroups(a)
        val groupsB = artistGroups(b)
        val baseTitlesA = a.associate { it.videoId to baseTitle(it) }
        val baseTitlesB = b.associate { it.videoId to baseTitle(it) }
        val pairOrder = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
            .thenBy { it.b.rank }.thenBy { it.a.rank }
        return groupsA.keys.intersect(groupsB.keys).map { key ->
            val left = groupsA.getValue(key)
            val right = groupsB.getValue(key)
            val displayName = left.first().artists.first { normalized(it) == key }
            var best: RadioMetadataMatch? = null
            for (trackA in left) {
                val titleA = baseTitlesA[trackA.videoId].orEmpty()
                if (titleA.isBlank()) continue
                for (trackB in right) {
                    val titleB = baseTitlesB[trackB.videoId].orEmpty()
                    if (trackA.videoId == trackB.videoId || titleB.isBlank() || titleA == titleB) continue
                    val candidate = RadioMetadataMatch(trackA, trackB)
                    if (best?.let { pairOrder.compare(candidate, it) < 0 } != false) best = candidate
                }
            }
            RadioArtistMatch(displayName, left, right, best)
        }.sortedWith(compareBy<RadioArtistMatch> { it.tracksA.first().rank + it.tracksB.first().rank }
            .thenBy { it.tracksB.first().rank }.thenBy { it.tracksA.first().rank }.thenBy { normalized(it.name) })
    }

    /** Count distinct A/B ID pairs with eligible tracks, excluding exact-ID matches.
     * Categories are disjoint. Results are diagnostic only, not recommendations.
     */
    fun compare(
        a: List<RadioMetadataCandidate>,
        b: List<RadioMetadataCandidate>,
        metadataPairFilter: (RadioMetadataMatch) -> Boolean = { true },
    ): RadioMetadataReport {
        val first = a.distinctBy { it.videoId }.mapNotNull(::indexed)
        val second = b.distinctBy { it.videoId }.mapNotNull(::indexed)
        val byBase = second.groupBy { it.base }
        val strict = mutableListOf<RadioMetadataMatch>()
        val versions = mutableListOf<RadioMetadataMatch>()
        for (left in first) {
            for (right in byBase[left.base].orEmpty()) {
                if (left.track.videoId == right.track.videoId) continue
                val match = RadioMetadataMatch(left.track, right.track)
                if (!metadataPairFilter(match)) continue
                if (left.strict == right.strict) strict.add(match)
                else versions.add(match)
            }
        }
        val order = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
            .thenBy { it.b.rank }.thenBy { it.a.rank }
            .thenBy { it.a.videoId }.thenBy { it.b.videoId }
        val artists = sharedArtists(a, b)
        val differentSongArtists = artists.filter { it.bestDifferentSong != null }
        val bestDifferentSongArtist = differentSongArtists.minWithOrNull(
            compareBy<RadioArtistMatch> { it.bestDifferentSong!!.a.rank + it.bestDifferentSong.b.rank }
                .thenBy { it.bestDifferentSong!!.b.rank }
                .thenBy { it.bestDifferentSong!!.a.rank }
                .thenBy { normalized(it.name) },
        )
        return RadioMetadataReport(
            sameTitleCount = strict.size,
            versionCount = versions.size,
            sameTitleExamples = strict.sortedWith(order).take(EXAMPLE_LIMIT),
            versionExamples = versions.sortedWith(order).take(EXAMPLE_LIMIT),
            commonArtistCount = artists.size,
            commonArtistDifferentSongCount = differentSongArtists.size,
            commonArtistExamples = artists.take(EXAMPLE_LIMIT),
            bestDifferentSongArtistExample = bestDifferentSongArtist,
        )
    }

    /**
     * Inspect the already fetched pages before the queue exclusions.
     * Raw positions are positions in those pages, not filtered eligible ranks.
     * An excluded ID can be an owned/source track or an earlier recommendation.
     * No result from this method is used for actual playback.
     */
    fun beforeExclusions(
        rawA: List<RadioMetadataCandidate>,
        rawB: List<RadioMetadataCandidate>,
        eligibleA: List<RadioMetadataCandidate>,
        eligibleB: List<RadioMetadataCandidate>,
        eligibleReport: RadioMetadataReport,
        eligibleExactCount: Int,
    ): List<String> {
        val a = rawA.distinctBy { it.videoId }
        val b = rawB.distinctBy { it.videoId }
        val eligibleAIds = eligibleA.map { it.videoId }.toSet()
        val eligibleBIds = eligibleB.map { it.videoId }.toSet()
        val raw = compare(a, b)
        val removed = compare(a, b) { match ->
            match.a.videoId !in eligibleAIds || match.b.videoId !in eligibleBIds
        }
        val bById = b.associateBy { it.videoId }
        val exact = a.mapNotNull { left -> bById[left.videoId]?.let { RadioMetadataMatch(left, it) } }
        val matchOrder = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
            .thenBy { it.b.rank }.thenBy { it.a.rank }
        val removedExact = exact.filter { it.a.videoId !in eligibleAIds || it.b.videoId !in eligibleBIds }
            .sortedWith(matchOrder)
        val rawArtists = sharedArtists(a, b)
        val eligibleArtistKeys = artistGroups(eligibleA).keys.intersect(artistGroups(eligibleB).keys)
        val lostArtists = rawArtists.filter { normalized(it.name) !in eligibleArtistKeys }

        fun marked(track: RadioMetadataCandidate, ids: Set<String>): String =
            "#" + track.rank + " " + track.title + " — " + track.artists.joinToString(", ") +
                if (track.videoId in ids) " [eligible]" else " [excluded]"
        fun markedMatch(match: RadioMetadataMatch): String =
            "A " + marked(match.a, eligibleAIds) + " | B " + marked(match.b, eligibleBIds)

        // A previously excluded track can be useful evidence for an artist.
        // The candidate must itself still be eligible in B (the existing fallback side).
        // Count all such artists, and show up to five, using raw ranks for both sides.
        val bridges = lostArtists.mapNotNull { artist ->
            val candidateB = artist.tracksB.firstOrNull { it.videoId in eligibleBIds } ?: return@mapNotNull null
            val evidenceA = artist.tracksA.first()
            Triple(artist, evidenceA, candidateB)
        }.sortedWith(compareBy<Triple<RadioArtistMatch, RadioMetadataCandidate, RadioMetadataCandidate>> {
            it.second.rank + it.third.rank
        }.thenBy { it.third.rank }.thenBy { it.second.rank }.thenBy { normalized(it.first.name) })
        val bridgesAOnly = lostArtists.count { artist ->
            artist.tracksA.any { it.videoId in eligibleAIds } && artist.tracksB.none { it.videoId in eligibleBIds }
        }
        return buildList {
            add("Before exclusions (already fetched pages; original radio ranks):")
            add("Distinct returned IDs A: ${a.size}; B: ${b.size}")
            add("Excluded IDs A: ${a.count { it.videoId !in eligibleAIds }}; B: ${b.count { it.videoId !in eligibleBIds }}")
            add("Common videoIds before / after: ${exact.size} / $eligibleExactCount; removed: ${removedExact.size}")
            removedExact.take(EXAMPLE_LIMIT).forEach { add("Removed exact: " + markedMatch(it)) }
            add("Same title + artist(s) before / after: ${raw.sameTitleCount} / ${eligibleReport.sameTitleCount}; removed: ${removed.sameTitleCount}")
            removed.sameTitleExamples.forEach { add("Removed title match: " + markedMatch(it)) }
            add("Possible versions before / after: ${raw.versionCount} / ${eligibleReport.versionCount}; removed: ${removed.versionCount}")
            removed.versionExamples.forEach { add("Removed version match: " + markedMatch(it)) }
            add("Shared artists before / after: ${raw.commonArtistCount} / ${eligibleReport.commonArtistCount}; lost: ${lostArtists.size}")
            add("Shared artists with different base titles before / after: ${raw.commonArtistDifferentSongCount} / ${eligibleReport.commonArtistDifferentSongCount}")
            lostArtists.take(EXAMPLE_LIMIT).forEach { artist ->
                val newA = artist.tracksA.count { it.videoId in eligibleAIds }
                val newB = artist.tracksB.count { it.videoId in eligibleBIds }
                add("Lost artist: ${artist.name} — A: ${artist.tracksA.size} total / $newA eligible; B: ${artist.tracksB.size} total / $newB eligible")
                add("  A " + artist.tracksA.take(3).joinToString("; ") { marked(it, eligibleAIds) })
                add("  B " + artist.tracksB.take(3).joinToString("; ") { marked(it, eligibleBIds) })
            }
            add("Lost shared artists with eligible B track: ${bridges.size}; eligible A only: $bridgesAOnly")
            bridges.take(EXAMPLE_LIMIT).forEach { (artist, evidenceA, candidateB) ->
                add("Potential B candidate (diagnostic only): ${artist.name} — A evidence " +
                    marked(evidenceA, eligibleAIds) + " | B candidate " + marked(candidateB, eligibleBIds))
            }
            add("Up to 5 removed matches/artists/candidates per category. Excluded means source/owned or previously recommended ID; not necessarily a Liked Music track.")
        }
    }
}
