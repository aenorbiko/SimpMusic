package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.replaceUpcomingKeepingCurrent
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpcomingQueueMutationTest {
    private fun track(id: String) = Track(
        album = null, artists = null, duration = null, durationSeconds = null,
        isAvailable = true, isExplicit = false, likeStatus = null, thumbnails = null,
        title = id, videoId = id, videoType = null, category = null,
        feedbackTokens = null, resultType = null,
    )

    private class Fake(initial: List<Track>, var mediaId: String) {
        val calls = mutableListOf<String>()
        val state = MutableStateFlow<QueueData?>(QueueData(data = QueueData.Data(listTracks = initial)))
        val handler = Proxy.newProxyInstance(
            MediaPlayerHandler::class.java.classLoader,
            arrayOf(MediaPlayerHandler::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> GenericMediaItem.EMPTY.copy(mediaId = mediaId)
                "getQueueData" -> state
                "removeMediaItem" -> {
                    val index = args!![0] as Int
                    calls.add("remove:$index")
                    val q = state.value!!
                    state.value = q.removeTrackWithIndex(index)
                    Unit
                }
                "setQueueData" -> {
                    calls.add("metadata")
                    state.value = state.value!!.copy(data = args!![0] as QueueData.Data)
                    Unit
                }
                "loadMoreCatalog" -> {
                    calls.add("append")
                    assertEquals(false, args!![1])
                    @Suppress("UNCHECKED_CAST")
                    val tracks = args[0] as List<Track>
                    state.value = state.value!!.addTrackList(tracks)
                    Unit
                }
                else -> error("Unexpected player operation: ${method.name}")
            }
        } as MediaPlayerHandler
    }

    @Test
    fun preservesCurrentAndHistoryAndAppendsAfterMetadataAndBadges() = runBlocking {
        val old = listOf("played", "current", "old-next", "old-last").map(::track)
        val next = listOf("new", "recommendation").map(::track)
        val fake = Fake(old, "current")
        val meta = QueueData.Data(listTracks = old.take(2), playlistType = PlaylistType.RADIO,
            playlistId = "RDAMVMcurrent", playlistName = "Radio", continuation = "next-page")
        assertTrue(fake.handler.replaceUpcomingKeepingCurrent("current", old, 1, meta, next) {
            fake.calls.add("badges")
        })
        assertEquals(listOf("remove:3", "remove:2", "metadata", "badges", "append"), fake.calls)
        assertEquals(meta.copy(listTracks = old.take(2) + next), fake.state.value!!.data)
        assertEquals("current", fake.mediaId)
    }

    @Test
    fun staleTrackOrQueueOrInvalidPrefixIsRejectedWithoutSideEffects() = runBlocking {
        val old = listOf("played", "current", "old-next").map(::track)
        val meta = QueueData.Data(listTracks = old.take(2))
        val wrongTrack = Fake(old, "other")
        val wrongQueue = Fake(old.dropLast(1), "current")
        val wrongPrefix = Fake(old, "current")
        assertFalse(wrongTrack.handler.replaceUpcomingKeepingCurrent("current", old, 1, meta, emptyList()))
        assertFalse(wrongQueue.handler.replaceUpcomingKeepingCurrent("current", old, 1, meta, emptyList()))
        assertFalse(wrongPrefix.handler.replaceUpcomingKeepingCurrent("current", old, 1, meta.copy(listTracks = old), emptyList()))
        assertEquals(emptyList(), wrongTrack.calls)
        assertEquals(emptyList(), wrongQueue.calls)
        assertEquals(emptyList(), wrongPrefix.calls)
    }

    @Test
    fun emptyTailNeverLoadsOrRestartsCurrentTrack() = runBlocking {
        val old = listOf("current", "old-next").map(::track)
        val fake = Fake(old, "Videocurrent")
        val meta = QueueData.Data(listTracks = old.take(1))
        assertTrue(fake.handler.replaceUpcomingKeepingCurrent("Videocurrent", old, 0, meta, emptyList()))
        assertEquals(listOf("remove:1", "metadata"), fake.calls)
        assertEquals(meta, fake.state.value!!.data)
    }
}
