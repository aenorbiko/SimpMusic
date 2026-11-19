package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnhancedLiveQueueMemoryTest {
    private fun track(id: String) = Track(
        album = null,
        artists = null,
        duration = null,
        durationSeconds = 210,
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails = null,
        title = id,
        videoId = id,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
    )

    private fun session() = EnhancedLiveQueueSession(
        sourceId = "LM",
        sourceName = "Liked Music",
        sourceTracks = listOf("A", "B").map(::track),
        shuffledUpcoming = listOf("A", "B").map(::track),
        nextSourceIndex = 2,
        queueVideoIds = listOf("A", "B", "RADIO1"),
        recommendedIds = setOf("RADIO1"),
        usedRecommendationIds = setOf("RADIO1"),
    )

    @Test
    fun canRejoinExactLiveEnhancedQueueWithoutSerializing() {
        val snap = session()
        assertTrue(matchesLiveEnhancedQueue(snap, snap.label, listOf("A", "B", "RADIO1"), "VideoB"))
        assertTrue(matchesLiveEnhancedQueue(snap, snap.label, listOf("A", "B", "RADIO1"), "RADIO1"))
    }

    @Test
    fun cannotMistakeNewSourceOrChangedSongOrderForOriginalEnhancedSession() {
        val snap = session()
        assertFalse(matchesLiveEnhancedQueue(snap, "Other playlist · Enhanced 2+1", snap.queueVideoIds, "B"))
        assertFalse(matchesLiveEnhancedQueue(snap, snap.label, listOf("B", "A", "RADIO1"), "B"))
        assertFalse(matchesLiveEnhancedQueue(snap, snap.label, snap.queueVideoIds, "UNRELATED"))
        assertFalse(matchesLiveEnhancedQueue(snap, snap.label, emptyList(), "B"))
    }

    @Test
    fun memoryStateIsExplicitlyClearedOnPlaylistOrModeChange() {
        EnhancedLiveQueueMemory.session = session()
        assertTrue(EnhancedLiveQueueMemory.session != null)
        EnhancedLiveQueueMemory.clear()
        assertTrue(EnhancedLiveQueueMemory.session == null)
    }
}
