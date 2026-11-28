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
    private val whitespace = Regex("""\s+""")
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
    }.trim().split(whitespace).filter { it.isNotEmpty() }.joinToString(" ")

    private data class Identity(val title: String, val artists: List<String>)
    private data class Indexed(
        val track: RadioMetadataCandidate,
        val strict: Identity,
        val base: Identity,
    )

    // Local to one comparison; reuse normalized artist names and titles in the
    // exact same ranking paths. Preserve the first original spelling for display.
    private data class Prepared(
        val track: RadioMetadataCandidate,
        val artistNames: List<Pair<String, String>>,
        val strictTitle: String,
        val baseTitle: String,
    ) {
        fun indexed(): Indexed? {
            val artists = artistNames.map { it.first }.distinct().sorted()
            if (artists.isEmpty() || strictTitle.isBlank() || baseTitle.isBlank()) return null
            return Indexed(track, Identity(strictTitle, artists), Identity(baseTitle, artists))
        }
    }

    private fun prepare(candidate: RadioMetadataCandidate): Prepared {
        val artists = candidate.artists.map { normalized(it) to it }.filter { it.first.isNotBlank() }
        val clean = stripSuffixes(candidate.title, presentationSuffix)
        return Prepared(candidate, artists, normalized(clean), normalized(stripSuffixes(clean, versionSuffix)))
    }

    /** Same strict identity as the title-match tier; remix/version labels stay intact.
     * Normalized components cannot contain |, so this key is unambiguous.
     * Missing artist/title metadata never excludes a track by title alone.
     */
    fun strictSongKey(candidate: RadioMetadataCandidate): String? =
        prepare(candidate).indexed()?.strict?.let { (listOf(it.title) + it.artists).joinToString("|") }

    /** Artist names are matched individually, including collaborators explicitly
     * listed in metadata. Do not split channel names or guess from video titles.
     */
    private fun artistGroups(tracks: List<Prepared>): Map<String, List<Prepared>> =
        tracks.distinctBy { it.track.videoId }.sortedBy { it.track.rank }
            .flatMap { track -> track.artistNames.map { it.first }.distinct().map { it to track } }
            .groupBy({ it.first }, { it.second })

    private fun artistKeys(tracks: List<RadioMetadataCandidate>): Set<String> =
        tracks.distinctBy { it.videoId }.flatMap { it.artists.map(::normalized) }.filter { it.isNotBlank() }.toSet()

    private fun sharedArtists(a: List<Prepared>, b: List<Prepared>): List<RadioArtistMatch> {
        val groupsA = artistGroups(a)
        val groupsB = artistGroups(b)
        // As before, artist grouping uses first occurrence, base-title lookup
        // uses the last occurrence of an ID in the input.
        val baseTitlesA = a.associate { it.track.videoId to it.baseTitle }
        val baseTitlesB = b.associate { it.track.videoId to it.baseTitle }
        val pairOrder = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
            .thenBy { it.b.rank }.thenBy { it.a.rank }
        return groupsA.keys.intersect(groupsB.keys).map { key ->
            val left = groupsA.getValue(key)
            val right = groupsB.getValue(key)
            val displayName = left.first().artistNames.first { it.first == key }.second
            var best: RadioMetadataMatch? = null
            for (trackA in left) {
                val titleA = baseTitlesA[trackA.track.videoId].orEmpty()
                if (titleA.isBlank()) continue
                for (trackB in right) {
                    val titleB = baseTitlesB[trackB.track.videoId].orEmpty()
                    if (trackA.track.videoId == trackB.track.videoId || titleB.isBlank() || titleA == titleB) continue
                    val candidate = RadioMetadataMatch(trackA.track, trackB.track)
                    if (best?.let { pairOrder.compare(candidate, it) < 0 } != false) best = candidate
                }
            }
            RadioArtistMatch(displayName, left.map { it.track }, right.map { it.track }, best)
        }.sortedWith(compareBy<RadioArtistMatch> { it.tracksA.first().rank + it.tracksB.first().rank }
            .thenBy { it.tracksB.first().rank }.thenBy { it.tracksA.first().rank }.thenBy { normalized(it.name) })
    }

    private data class TitleMatches(
        val strict: List<RadioMetadataMatch>,
        val versions: List<RadioMetadataMatch>,
    )
    private val titleOrder = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
        .thenBy { it.b.rank }.thenBy { it.a.rank }
        .thenBy { it.a.videoId }.thenBy { it.b.videoId }

    private fun titleMatches(
        a: List<Prepared>,
        b: List<Prepared>,
        metadataPairFilter: (RadioMetadataMatch) -> Boolean = { true },
    ): TitleMatches {
        val first = a.distinctBy { it.track.videoId }.mapNotNull { it.indexed() }
        val second = b.distinctBy { it.track.videoId }.mapNotNull { it.indexed() }
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
        return TitleMatches(strict, versions)
    }

    /** Count distinct A/B ID pairs with eligible tracks, excluding exact-ID matches.
     * Categories are disjoint. The final selector may use the best strict title/artist
     * and different-song shared-artist results; looser version matches remain diagnostic.
     */
    fun compare(
        a: List<RadioMetadataCandidate>,
        b: List<RadioMetadataCandidate>,
        metadataPairFilter: (RadioMetadataMatch) -> Boolean = { true },
    ): RadioMetadataReport {
        val preparedA = a.map(::prepare)
        val preparedB = b.map(::prepare)
        val titles = titleMatches(preparedA, preparedB, metadataPairFilter)
        val artists = sharedArtists(preparedA, preparedB)
        val differentSongArtists = artists.filter { it.bestDifferentSong != null }
        val bestDifferentSongArtist = differentSongArtists.minWithOrNull(
            compareBy<RadioArtistMatch> { it.bestDifferentSong!!.a.rank + it.bestDifferentSong.b.rank }
                .thenBy { it.bestDifferentSong!!.b.rank }
                .thenBy { it.bestDifferentSong!!.a.rank }
                .thenBy { normalized(it.name) },
        )
        return RadioMetadataReport(
            sameTitleCount = titles.strict.size,
            versionCount = titles.versions.size,
            sameTitleExamples = titles.strict.sortedWith(titleOrder).take(EXAMPLE_LIMIT),
            versionExamples = titles.versions.sortedWith(titleOrder).take(EXAMPLE_LIMIT),
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
        val preparedA = a.map(::prepare)
        val preparedB = b.map(::prepare)
        val rawTitles = titleMatches(preparedA, preparedB)
        fun removed(match: RadioMetadataMatch) =
            match.a.videoId !in eligibleAIds || match.b.videoId !in eligibleBIds
        val removedStrict = rawTitles.strict.filter(::removed)
        val removedVersions = rawTitles.versions.filter(::removed)
        val bById = b.associateBy { it.videoId }
        val exact = a.mapNotNull { left -> bById[left.videoId]?.let { RadioMetadataMatch(left, it) } }
        val matchOrder = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
            .thenBy { it.b.rank }.thenBy { it.a.rank }
        val removedExact = exact.filter { it.a.videoId !in eligibleAIds || it.b.videoId !in eligibleBIds }
            .sortedWith(matchOrder)
        val rawArtists = sharedArtists(preparedA, preparedB)
        val eligibleArtistKeys = artistKeys(eligibleA).intersect(artistKeys(eligibleB))
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
            add("Same title + artist(s) before / after: ${rawTitles.strict.size} / ${eligibleReport.sameTitleCount}; removed: ${removedStrict.size}")
            removedStrict.sortedWith(titleOrder).take(EXAMPLE_LIMIT).forEach { add("Removed title match: " + markedMatch(it)) }
            add("Possible versions before / after: ${rawTitles.versions.size} / ${eligibleReport.versionCount}; removed: ${removedVersions.size}")
            removedVersions.sortedWith(titleOrder).take(EXAMPLE_LIMIT).forEach { add("Removed version match: " + markedMatch(it)) }
            add("Shared artists before / after: ${rawArtists.size} / ${eligibleReport.commonArtistCount}; lost: ${lostArtists.size}")
            add("Shared artists with different base titles before / after: ${rawArtists.count { it.bestDifferentSong != null }} / ${eligibleReport.commonArtistDifferentSongCount}")
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
