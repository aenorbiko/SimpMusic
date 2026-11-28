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
    fun defaultTtlIsOneHourAndHitsDoNotRenewIt() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(nowMs = { now })
        cache.newBatch().load("a", null) { page("original") }
        now = 20 * 60_000L
        assertTrue(cache.newBatch().load("a", null) { error("unexpected network at 20 minutes") }.fromCache)
        now = 60 * 60_000L - 1
        assertTrue(cache.newBatch().load("a", null) { error("unexpected network before one hour") }.fromCache)
        now = 60 * 60_000L
        assertFalse(cache.newBatch().load("a", null) { page("fresh") }.fromCache)
        assertTrue(cache.report().contains("capacity: 64; TTL: 60 min"))
    }

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

    @Test
    fun unrelatedExpiredEntriesArePrunedBeforeCapacityEviction() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(ttlMs = 100, maxEntries = 3, nowMs = { now })
        val old = cache.newBatch()
        old.load("expired", null) { page("one", "two", next = "oldToken") }
        old.load("expired", "oldToken") { page("three") }
        now = 50
        cache.newBatch().load("live1", null) { page("live1") }
        cache.newBatch().load("live2", null) { page("live2") }
        now = 99
        assertTrue(cache.newBatch().load("expired", null) { error("still valid") }.fromCache)
        now = 100
        cache.newBatch().load("fresh", null) { page("fresh") }
        val report = cache.report()
        assertTrue(report.contains("Expired entries pruned: 1; track records removed: 3"))
        assertTrue(report.contains("Capacity evictions: 0"))
        assertTrue(report.contains("Resident cache entries: 3; live: 3; expired resident: 0; stored track records: 3"))
        assertTrue(cache.newBatch().load("live1", null) { error("live LRU must survive") }.fromCache)
        assertFalse(cache.newBatch().load("expired", null) { page("new") }.fromCache)
    }

    @Test
    fun reportingPrunesExpiredEntriesButPinnedContinuationSurvives() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(ttlMs = 100, nowMs = { now })
        val first = cache.newBatch()
        first.load("a", null) { page("one", next = "token") }
        first.load("a", "token") { page("two") }
        val active = cache.newBatch()
        assertTrue(active.load("a", null) { error("expected hit") }.fromCache)
        now = 100
        val report = cache.report()
        assertTrue(report.contains("Expired entries pruned: 1; track records removed: 2"))
        assertTrue(report.contains("Resident cache entries: 0; live: 0; expired resident: 0; stored track records: 0"))
        assertEquals(listOf("two"), active.load("a", "token") { error("pinned continuation must survive") }.tracks)
        assertFalse(cache.newBatch().load("a", null) { page("fresh") }.fromCache)
    }

    @Test
    fun expiryPruningCannotResurrectDeferredSnapshotOrRemoveFreshReplacement() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(ttlMs = 100, nowMs = { now })
        cache.newBatch().load("a", null) { page("old", next = "token") }
        val active = cache.newBatch()
        assertTrue(active.load("a", null) { error("expected hit") }.fromCache)
        now = 100
        cache.report()
        cache.newBatch().load("a", null) { page("fresh", next = "freshToken") }
        assertEquals(listOf("late"), active.load("a", "token") { page("late") }.tracks)
        val next = cache.newBatch()
        assertEquals(listOf("fresh"), next.load("a", null) { error("expected fresh hit") }.tracks)
        assertFalse(next.load("a", "freshToken") { page("freshSecond") }.fromCache)
        cache.resetCounters()
        assertTrue(cache.report().contains("Expired entries pruned: 0; track records removed: 0"))
    }

    @Test
    fun directExpiredLookupsAreSeparateFromProactivePruning() = runBlocking {
        var now = 0L
        val cache = EnhancedRadioCache<String>(ttlMs = 100, nowMs = { now })
        cache.newBatch().load("a", null) { page("a") }
        cache.newBatch().load("b", null) { page("b") }
        now = 100
        cache.newBatch().load("a", null) { page("fresh") }
        val report = cache.report()
        assertTrue(report.contains("expired lookups: 1"))
        assertTrue(report.contains("Expired entries pruned: 1; track records removed: 1"))
    }
}
