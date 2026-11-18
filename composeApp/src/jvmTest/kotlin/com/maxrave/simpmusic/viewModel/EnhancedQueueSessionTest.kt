package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnhancedQueueSessionTest {
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

    private fun snapshot(
        queue: List<String> = listOf("X", "A", "B", "REC1", "C", "D", "REC2"),
        upcoming: List<String> = listOf("A", "B", "C", "D", "E", "F"),
    ) = EnhancedQueueSession(
        sourceId = "LM",
        sourceName = "Liked Music",
        sourceTracks = (listOf("X") + upcoming).map(::track),
        shuffledUpcoming = upcoming.map(::track),
        queueVideoIds = queue,
        nextSourceIndex = 6,
        recommendedVideoIds = setOf("REC1", "REC2"),
        usedRecommendationIds = setOf("REC1", "REC2", "OLD_REC"),
    )

    @Test
    fun jsonRoundTripPreservesSourceTracksAndCursorWithoutAccountSecrets() {
        val original = snapshot()
        val json = EnhancedQueueSessionCodec.encode(original)
        val decoded = assertNotNull(EnhancedQueueSessionCodec.decode(json))
        assertEquals(original, decoded)
        assertEquals("Liked Music · Enhanced 2+1", decoded.playlistLabel)
        assertTrue(json.contains("REC1"))
        assertTrue(!json.contains("cookie"))
        assertTrue(!json.contains("password"))
    }

    @Test
    fun restoringCompleteQueuePreservesStarsAndResumesAfterLastLoadedSource() {
        val session = snapshot()
        val restored = assertNotNull(
            planEnhancedRestoration(
                session = session,
                playlistName = session.playlistLabel,
                actualQueueVideoIds = session.queueVideoIds,
                currentVideoId = "C",
            )
        )
        // A-D are actually loaded, while E/F were only planned by the
        // cancelled first batch. Restart must refill at E, not skip to end.
        assertEquals(4, restored.nextSourceIndex)
        assertEquals(setOf("REC1", "REC2"), restored.recommendedIds)
        assertEquals(setOf("REC1", "REC2", "OLD_REC"), restored.usedRecommendationIds)
    }

    @Test
    fun acceptsEarlierSavedPrefixButOnlyMarksRestoredRecommendations() {
        val session = snapshot()
        val restored = assertNotNull(
            planEnhancedRestoration(
                session,
                playlistName = session.playlistLabel,
                actualQueueVideoIds = listOf("X", "A", "B", "REC1"),
                currentVideoId = "VideoB", // Android Media3 can prefix media IDs
            )
        )
        assertEquals(2, restored.nextSourceIndex)
        assertEquals(setOf("REC1"), restored.recommendedIds)
        assertEquals(true, "REC2" in restored.usedRecommendationIds)
    }

    @Test
    fun refusesSameTitleWithDifferentTrackOrderOrUnrelatedMedia() {
        val session = snapshot()
        assertNull(
            planEnhancedRestoration(
                session,
                session.playlistLabel,
                listOf("X", "B", "A", "REC1"),
                "B",
            )
        )
        assertNull(
            planEnhancedRestoration(
                session,
                session.playlistLabel,
                session.queueVideoIds,
                "UNRELATED",
            )
        )
        assertNull(
            planEnhancedRestoration(
                session,
                "Other playlist · Enhanced 2+1",
                session.queueVideoIds,
                "B",
            )
        )
        assertNull(planEnhancedRestoration(session, session.playlistLabel, emptyList(), "B"))
    }

    @Test
    fun rejectsUnsupportedOrMalformedPersistenceWithoutEnablingEnhanced() {
        assertNull(EnhancedQueueSessionCodec.decode(null))
        assertNull(EnhancedQueueSessionCodec.decode(""))
        assertNull(EnhancedQueueSessionCodec.decode("{invalid json"))
        val session = snapshot()
        assertNull(
            EnhancedQueueSessionCodec.decode(
                EnhancedQueueSessionCodec.encode(session.copy(schemaVersion = 99))
            )
        )
        assertNull(
            EnhancedQueueSessionCodec.decode(
                EnhancedQueueSessionCodec.encode(session.copy(queueVideoIds = emptyList()))
            )
        )
    }

    @Test
    fun previouslyLoadedRecommendationIdsRemainExcludedFromFutureRadioRequests() {
        val session = snapshot(
            queue = listOf("X", "A", "B", "REC1", "C", "D", "REC2"),
            upcoming = listOf("A", "B", "C", "D", "E", "F"),
        )
        val restored = assertNotNull(
            planEnhancedRestoration(
                session = session,
                playlistName = session.playlistLabel,
                actualQueueVideoIds = session.queueVideoIds,
                currentVideoId = "REC2",
            )
        )
        assertEquals(setOf("REC1", "REC2"), restored.recommendedIds)
        assertTrue("OLD_REC" in restored.usedRecommendationIds)
        assertTrue("REC2" in restored.usedRecommendationIds)
    }
}
