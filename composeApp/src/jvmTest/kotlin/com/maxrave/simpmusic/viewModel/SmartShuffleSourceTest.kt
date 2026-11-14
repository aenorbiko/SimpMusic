package com.maxrave.simpmusic.viewModel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartShuffleSourceTest {
    @Test
    fun youtubePlaylistMatchesItsOriginalQueue() {
        assertTrue(smartShuffleMatchesOriginalQueue("playlist:PLabc", "PLabc"))
        assertFalse(smartShuffleMatchesOriginalQueue("playlist:PLabc", "PLother"))
    }

    @Test
    fun albumMatchesItsOriginalQueue() {
        assertTrue(smartShuffleMatchesOriginalQueue("album:OLAKabc", "OLAKabc"))
        assertFalse(smartShuffleMatchesOriginalQueue("album:OLAKabc", "PLabc"))
    }

    @Test
    fun localPlaylistMatchesItsOriginalQueue() {
        assertTrue(smartShuffleMatchesOriginalQueue("local:LOCAL_31", "LOCAL_31"))
        assertFalse(smartShuffleMatchesOriginalQueue("local:LOCAL_31", "LOCAL_13"))
    }

    @Test
    fun otherLibraryListsNeverAccidentallyMatchAnUnrelatedQueue() {
        assertFalse(smartShuffleMatchesOriginalQueue("dynamic:TopTracks", "TopTracks"))
        assertFalse(smartShuffleMatchesOriginalQueue("playlist:abc", null))
        assertFalse(smartShuffleMatchesOriginalQueue("album:abc", ""))
    }

    @Test
    fun likedPlaylistRemainsBackwardCompatible() {
        assertTrue(smartShuffleMatchesOriginalQueue("LM", "LM"))
        assertFalse(smartShuffleMatchesOriginalQueue("LM", "not-liked"))
    }
}
