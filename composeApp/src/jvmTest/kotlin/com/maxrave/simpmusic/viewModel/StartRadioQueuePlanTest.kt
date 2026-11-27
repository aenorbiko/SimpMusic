package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.SelectedRadioRequest
import com.maxrave.domain.mediaservice.handler.planStartRadioQueue
import com.maxrave.domain.mediaservice.handler.startRadioRequestIsCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartRadioQueuePlanTest {
    private fun track(id: String) = Track(
        album = null, artists = null, duration = null, durationSeconds = null,
        isAvailable = true, isExplicit = false, likeStatus = null, thumbnails = null,
        title = id, videoId = id, videoType = null, category = null,
        feedbackTokens = null, resultType = null,
    )

    @Test
    fun preservesHistoryAndCurrentAndRadioContinuation() {
        val old = listOf("played", "seed", "old-next").map(::track)
        val radio = listOf("r1", "seed", "r2").map(::track)
        val plan = planStartRadioQueue(old, old.map { it.videoId }, 1, "seed", "Seed", radio, "next")!!
        assertEquals(old.take(2), plan.preservedQueue.listTracks)
        assertEquals(old[1], plan.preservedQueue.firstPlayedTrack)
        assertEquals("RDAMVMseed", plan.preservedQueue.playlistId)
        assertEquals(PlaylistType.RADIO, plan.preservedQueue.playlistType)
        assertEquals("next", plan.preservedQueue.continuation)
        assertEquals(listOf("r1", "r2"), plan.upcoming.map { it.videoId })
    }

    @Test
    fun rejectsStaleOrderWrongIndexAndEmptyRadio() {
        val old = listOf("history", "seed", "next").map(::track)
        val radio = listOf(track("r1"))
        assertNull(planStartRadioQueue(old, listOf("seed", "next", "history"), 1, "seed", "Seed", radio, null))
        assertNull(planStartRadioQueue(old, old.map { it.videoId }, 0, "seed", "Seed", radio, null))
        assertNull(planStartRadioQueue(old, old.map { it.videoId }, 1, "seed", "Seed", listOf(track("seed")), null))
    }

    @Test
    fun usesExactOccurrenceOfDuplicateSeedInPlaybackOrder() {
        val old = listOf("seed", "played", "seed", "next").map(::track)
        val plan = planStartRadioQueue(old, old.map { it.videoId }, 2, "seed", "Seed", listOf(track("r")), null)!!
        assertEquals(old.take(3), plan.preservedQueue.listTracks)
    }

    @Test
    fun repeatedRadioRequestChangedTrackOrNewShuffleInvalidatesOldResponse() {
        assertTrue(startRadioRequestIsCurrent(1, 1, "seed", "seed", 5, 5))
        assertFalse(startRadioRequestIsCurrent(1, 2, "seed", "seed", 5, 5))
        assertFalse(startRadioRequestIsCurrent(1, 1, "seed", "other", 5, 5))
        assertFalse(startRadioRequestIsCurrent(1, 1, "seed", "seed", 5, 6))
    }

    @Test
    fun selectedRadioTicketRejectsLaterPlaybackAndOtherRadioRequests() {
        val request = SelectedRadioRequest(4, "A", 2)
        assertTrue(request.isCurrent(4, "A", 2))
        assertFalse(request.isCurrent(4, "C", 2))
        assertFalse(request.isCurrent(5, "A", 2))
        assertFalse(request.isCurrent(4, "A", 3))
        // Returning to A after selecting C must not revive the old request.
        assertFalse(request.isCurrent(6, "A", 2))
    }

    @Test
    fun selectedRadioCanStartFromEmptyPlayerButNotOverwriteNewPlayback() {
        val request = SelectedRadioRequest(1, null, 0)
        assertTrue(request.isCurrent(1, null, 0))
        assertFalse(request.isCurrent(2, null, 0))
        assertFalse(request.isCurrent(1, "C", 0))
    }
}
