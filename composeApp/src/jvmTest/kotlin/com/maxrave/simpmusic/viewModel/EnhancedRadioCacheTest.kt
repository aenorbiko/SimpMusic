package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnhancedRadioCacheTest {
    @Test
    fun reusesTrackMetadataWithinTtlAndExpiresAtBoundary() = runBlocking {
        var now = 100L
        val cache = EnhancedRadioCache<String>(ttlMs = 1_000L, nowMs = { now })
        cache.put("A", listOf("rec1", "rec2"))
        assertEquals(listOf("rec1", "rec2"), cache.get("A"))
        now = 1_099L
        assertEquals(listOf("rec1", "rec2"), cache.get("A"))
        now = 1_100L
        assertNull(cache.get("A"))
    }

    @Test
    fun emptyResultsAreNotCachedAndOlderEntriesAreEvicted() = runBlocking {
        val cache = EnhancedRadioCache<String>(maxEntries = 2)
        cache.put("A", emptyList())
        assertNull(cache.get("A"))
        cache.put("A", listOf("a"))
        cache.put("B", listOf("b"))
        cache.put("C", listOf("c"))
        assertNull(cache.get("A"))
        assertEquals(listOf("b"), cache.get("B"))
        assertEquals(listOf("c"), cache.get("C"))
    }

    @Test
    fun entriesCannotBeMutatedThroughInputList() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        val songs = mutableListOf("r1")
        cache.put("X", songs)
        songs.add("r2")
        assertEquals(listOf("r1"), cache.get("X"))
    }
}
