package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class EnhancedTrackDeduplicationTest {
    private fun track(id: String, title: String, vararg artists: String) =
        RadioMetadataCandidate(id, title, artists.toList(), null, 0)
    private fun key(t: RadioMetadataCandidate) = EnhancedRadioMetadataDiagnostics.strictSongKey(t)

    @Test
    fun strictIdentityKeepsDifferentVersionsAndNeedsArtist() {
        val original = track("one", "Song", "Artist", "Guest")
        assertEquals(key(original), key(track("two", "SONG (Official Video)", "Guest", "Artist")))
        assertNotEquals(key(original), key(track("three", "Song (Remix)", "Artist", "Guest")))
        assertNotEquals(key(original), key(track("four", "Other", "Artist", "Guest")))
        assertNull(key(track("unknown", "Song")))
    }

    @Test
    fun excludesOwnedAlternateIdsAndPreviousPicksBeforePageExpansionAndFinalSelection() = runBlocking {
        val owned = track("owned", "Owned", "Artist")
        val alternate = track("alt", "Owned (Official Video)", "Artist")
        val remix = track("remix", "Owned (Remix)", "Artist")
        val next = track("next", "New song", "Artist")
        val duplicate = track("dup", "New song (Official Audio)", "Artist")
        val fallback = track("fallback", "Different", "Artist")
        val seeds = (1..6).map { track("seed$it", "Seed $it", "Seed artist") }
        val picked = mutableListOf<String?>()
        val calls = mutableListOf<String>()
        emitPagedEnhancedPairs(
            seeds = seeds,
            excludedIds = seeds.map { it.videoId }.toSet() + owned.videoId,
            id = { it.videoId },
            excludedSongKeys = setOf(key(owned)!!),
            songKey = ::key,
            fetchPage = { seed, token ->
                if (token != null) {
                    calls.add(seed.videoId)
                    EnhancedRadioPage(if (seed.videoId in listOf("seed1","seed2")) listOf(remix) else emptyList())
                } else EnhancedRadioPage(when (seed.videoId) {
                    "seed1", "seed2" -> listOf(alternate)
                    "seed3", "seed4" -> listOf(next)
                    else -> listOf(duplicate, fallback)
                }, "next")
            },
        ) { _, recommendation -> picked.add(recommendation?.videoId) }
        assertEquals(listOf("remix", "next", "fallback"), picked)
        assertEquals(setOf("seed1","seed2"), calls.toSet())
    }
}
