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

internal data class RadioMetadataReport(
    val sameTitleCount: Int,
    val versionCount: Int,
    val sameTitleExamples: List<RadioMetadataMatch>,
    val versionExamples: List<RadioMetadataMatch>,
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

    /** Count distinct A/B ID pairs with eligible tracks, excluding exact-ID matches.
     * Categories are disjoint. Results are diagnostic only, not recommendations.
     */
    fun compare(a: List<RadioMetadataCandidate>, b: List<RadioMetadataCandidate>): RadioMetadataReport {
        val first = a.distinctBy { it.videoId }.mapNotNull(::indexed)
        val second = b.distinctBy { it.videoId }.mapNotNull(::indexed)
        val byBase = second.groupBy { it.base }
        val strict = mutableListOf<RadioMetadataMatch>()
        val versions = mutableListOf<RadioMetadataMatch>()
        for (left in first) {
            for (right in byBase[left.base].orEmpty()) {
                if (left.track.videoId == right.track.videoId) continue
                val match = RadioMetadataMatch(left.track, right.track)
                if (left.strict == right.strict) strict.add(match)
                else versions.add(match)
            }
        }
        val order = compareBy<RadioMetadataMatch> { it.a.rank + it.b.rank }
            .thenBy { it.b.rank }.thenBy { it.a.rank }
            .thenBy { it.a.videoId }.thenBy { it.b.videoId }
        return RadioMetadataReport(
            sameTitleCount = strict.size,
            versionCount = versions.size,
            sameTitleExamples = strict.sortedWith(order).take(EXAMPLE_LIMIT),
            versionExamples = versions.sortedWith(order).take(EXAMPLE_LIMIT),
        )
    }
}
