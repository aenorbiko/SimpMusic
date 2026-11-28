package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnhancedRadioCacheTest {
    private fun page(vararg tracks: String, next: String? = null) =
        EnhancedRadioPage(tracks.toList(), continuation = next, requestMs = 500)

    @Test
    fun completeRadioReusesExactPagesWithoutNetworkEvenAfterExpiryDuringBatch() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(ttlMs = 100, nowMs = { now })
        val cold = cache.newBatch()
        cold.load("a", null) { page("one", next = "token") }
        cold.load("a", "token") { page("two") }
        val warm = cache.newBatch()
        val first = warm.load("a", null) { error("first network") }
        assertTrue(first.fromCache)
        assertEquals(0L, first.requestMs)
        assertEquals("token", first.continuation)
        now = 100
        val second = warm.load("a", "token") { error("stale token sent") }
        assertTrue(second.fromCache)
        assertEquals(listOf("two"), second.tracks)
        assertFalse(cache.newBatch().load("a", null) { page("new") }.fromCache)
        assertTrue(cache.report().contains("Network requests avoided: 2"))
    }

    @Test
    fun incompleteFailedAndEmptyRadiosNeverReused() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        cache.newBatch().load("a", null) { page("one", next = "token") }
        val retry = cache.newBatch()
        assertFalse(retry.load("a", null) { page("one", next = "new") }.fromCache)
        retry.load("a", "new") { EnhancedRadioPage(emptyList(), status = "Request failed") }
        assertFalse(cache.newBatch().load("a", null) { page("again") }.fromCache)
        cache.newBatch().load("empty", null) { page() }
        assertFalse(cache.newBatch().load("empty", null) { page("new") }.fromCache)
    }

    @Test
    fun lruAndFixedExpiryAndCounterReset() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(ttlMs = 100, maxEntries = 2, nowMs = { now })
        cache.newBatch().load("a", null) { page("a") }
        cache.newBatch().load("b", null) { page("b") }
        now = 10
        assertTrue(cache.newBatch().load("a", null) { error("hit") }.fromCache)
        cache.newBatch().load("c", null) { page("c") }
        assertFalse(cache.newBatch().load("b", null) { page("b") }.fromCache)
        assertTrue(cache.report().contains("Capacity evictions: 2"))
        cache.newBatch().load("d", null) { page("d") }
        now = 50
        assertTrue(cache.newBatch().load("d", null) { error("hit") }.fromCache)
        now = 110
        assertFalse(cache.newBatch().load("d", null) { page("new") }.fromCache)
        cache.resetCounters()
        assertTrue(cache.report().contains("Network requests avoided: 0"))
    }

    @Test
    fun cancellationIsNotCached() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        try {
            cache.newBatch().load("a", null) { throw CancellationException("cancelled") }
            error("cancellation swallowed")
        } catch (_: CancellationException) {}
        assertFalse(cache.newBatch().load("a", null) { page("a") }.fromCache)
    }
}
