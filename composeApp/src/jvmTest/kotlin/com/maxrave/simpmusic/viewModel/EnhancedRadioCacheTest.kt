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
        assertTrue(cache.report().contains("Cache-served pages (gross avoided requests): 2"))
    }

    @Test
    fun firstPageIsImmediatelyReusableAndDeferredContinuationExtendsIt() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        val all = (1..50).map { "track$it" }
        cache.newBatch().load("a", null) { EnhancedRadioPage(all, "token") }
        val warm = cache.newBatch()
        val first = warm.load("a", null) { error("first network") }
        assertTrue(first.fromCache)
        assertEquals(all, first.tracks)
        assertFalse(warm.load("a", "token") { page("two") }.fromCache)
        val next = cache.newBatch()
        assertTrue(next.load("a", null) { error("first network") }.fromCache)
        assertEquals(listOf("two"), next.load("a", "token") { error("second network") }.tracks)
        assertTrue(cache.report().contains("Deferred continuation requests: 1; successful: 1; failed or empty: 0"))
    }

    @Test
    fun failedDeferredTokenInvalidatesOldSnapshotAndRefreshesOnlyOnce() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        cache.newBatch().load("a", null) { page("old", next = "oldToken") }
        val warm = cache.newBatch()
        assertTrue(warm.load("a", null) { error("first network") }.fromCache)
        val failed = warm.load("a", "oldToken") { EnhancedRadioPage(emptyList(), status = "Error") }
        assertTrue(failed.retryFirst)
        warm.refreshFirst("a") { page("new", next = "newToken") }
        val freshFailure = warm.load("a", "newToken") { EnhancedRadioPage(emptyList(), status = "Error") }
        assertFalse(freshFailure.retryFirst)
        assertEquals(listOf("new"), cache.newBatch().load("a", null) { error("first network") }.tracks)
        assertTrue(cache.report().contains("Deferred continuation requests: 1; successful: 0; failed or empty: 1"))
        assertTrue(cache.report().contains("Recovery first-page requests: 1"))
    }

    @Test
    fun failedAndEmptyFirstPagesAreNotCached() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        cache.newBatch().load("empty", null) { page() }
        assertFalse(cache.newBatch().load("empty", null) { page("new") }.fromCache)
        cache.newBatch().load("error", null) { EnhancedRadioPage(listOf("bad"), status = "Error") }
        assertFalse(cache.newBatch().load("error", null) { page("new") }.fromCache)
    }

    @Test
    fun lateContinuationCannotOverwriteNewFirstPage() = runBlocking {
        val cache = EnhancedRadioCache<String>()
        val old = cache.newBatch()
        old.load("a", null) { page("old", next = "oldToken") }
        cache.newBatch().refreshFirst("a") { page("new", next = "newToken") }
        old.load("a", "oldToken") { page("late") }
        val latest = cache.newBatch()
        assertEquals(listOf("new"), latest.load("a", null) { error("first network") }.tracks)
        assertEquals(listOf("correct"), latest.load("a", "newToken") { page("correct") }.tracks)
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
        assertTrue(cache.report().contains("Cache-served pages (gross avoided requests): 0"))
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
