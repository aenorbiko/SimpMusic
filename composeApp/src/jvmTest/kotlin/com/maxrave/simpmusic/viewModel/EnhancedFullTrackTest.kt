package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EnhancedFullTrackTest {
    private fun song(
        id: String, seconds: Int?, title: String = "Show Me Love",
        artists: List<String> = listOf("WizTheMc", "bees & honey"),
    ) = RadioMetadataCandidate(id, title, artists, seconds, 0)

    private val short = song("QM3vbdPNUjU", 61)
    private val full = song("full", 177)

    private fun select(original: RadioMetadataCandidate, candidates: List<RadioMetadataCandidate>, excluded: Set<String> = emptySet()) =
        selectEnhancedFullTrack(original, candidates, excluded) { it }

    @Test
    fun sameSongReplacesShortOfficialExcerpt() {
        assertEquals(full, select(short, listOf(full)))
        val reordered = full.copy(artists = full.artists.reversed())
        assertEquals(reordered, select(short, listOf(reordered)))
    }

    @Test
    fun normalUnknownAndZeroDurationsRemainUntouched() {
        for (duration in listOf(null, 0, 90, 177)) {
            val original = short.copy(durationSeconds = duration)
            assertEquals(original, select(original, listOf(full)))
        }
    }

    @Test
    fun avoidWrongArtistsVersionsDuplicatesAndMarginalIncreases() {
        val candidates = listOf(
            full.copy(artists = listOf("WizTheMc")),
            full.copy(title = "Show Me Love (Remix)"),
            full.copy(title = "Other Song"),
            full.copy(videoId = short.videoId),
            full.copy(durationSeconds = null),
            full.copy(durationSeconds = 90),
            full.copy(videoId = "owned"),
        )
        assertEquals(short, select(short, candidates, setOf("owned")))
        assertEquals(full, select(short, candidates + full, setOf("owned")))
        assertEquals(short, select(short, emptyList()))
        assertEquals(short.copy(artists = emptyList()), select(short.copy(artists = emptyList()), listOf(full)))
    }

    @Test
    fun resolvedIdAndOriginalIdAreExcludedForLaterPairs() = runBlocking {
        val emitted = mutableListOf<String?>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B", "C", "D"),
            excludedIds = setOf("A", "B", "C", "D"),
            id = { it },
            fetchPage = { seed, _ ->
                EnhancedRadioPage(if (seed in listOf("A", "B")) listOf("short") else listOf("full", "short", "fresh"))
            },
            resolveRecommendation = { selected, _ -> if (selected == "short") "full" else selected },
        ) { _, selected -> emitted.add(selected) }
        assertEquals(listOf<String?>("full", "fresh"), emitted)
    }
}
