package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EnhancedRadioRequestStatisticsTest {
    @Test
    fun repeatWindowsAreCumulativeAndContinuationIsNotARepeat() = runBlocking {
        var saved: String? = null
        val start = 100_000_000L
        val stats = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { start + 86_400_000L })
        stats.record("PRIVATE_ID_123", false, true, start)
        stats.record("PRIVATE_ID_123", true, true, start + 1000)
        stats.record("PRIVATE_ID_123", false, true, start + 10 * 60_000L)
        stats.record("PRIVATE_ID_123", false, true, start + 40 * 60_000L)
        stats.record("PRIVATE_ID_123", false, true, start + 120 * 60_000L)
        val report = stats.report()
        assertTrue(report.contains("First-page radio requests: 4"))
        assertTrue(report.contains("Continuation page requests: 1"))
        assertTrue(report.contains("Repeat within 20 minutes: 1 (25.0%)"))
        assertTrue(report.contains("Repeat within 1 hour: 2 (50.0%)"))
        assertTrue(report.contains("Repeat within 24 hours: 3 (75.0%)"))
        assertFalse(report.contains("PRIVATE_ID_123"))
    }

    @Test
    fun historySurvivesRecreationAndFailedPagesDoNotCreateCacheEvidence() = runBlocking {
        var saved: String? = null
        val start = 100_000_000L
        fun store() = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { start + 1000 })
        store().record("a", false, false, start)
        store().record("a", false, true, start + 100)
        store().record("a", false, false, start + 200)
        store().record("b", false, true, start + 300)
        val report = store().report()
        assertTrue(report.contains("First-page radio requests: 4"))
        assertTrue(report.contains("Successful non-empty first pages: 2"))
        assertTrue(report.contains("Failed or empty first pages: 2"))
        assertTrue(report.contains("Repeat within 20 minutes: 1 (25.0%)"))
    }

    @Test
    fun expiredHistoryAndConcurrentWritesAreSafe() = runBlocking {
        var saved: String? = null
        val start = 100_000_000L
        val now = start + 86_400_000L
        val stats = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { now })
        stats.record("old", false, true, start)
        stats.record("old", false, true, now)
        (1..28).map { n -> async { stats.record("new$n", false, true, now) } }.awaitAll()
        val report = stats.report()
        assertTrue(report.contains("First-page radio requests: 30"))
        assertTrue(report.contains("Repeat within 24 hours: 0 (0.0%)"))
    }

    @Test
    fun storageFailureDoesNotThrowIntoPlaybackAndIsReported() = runBlocking {
        val stats = EnhancedRadioRequestStatistics({ null }, { error("disk error") }, { 1000L })
        stats.record("a", false, true, 1000L)
        assertTrue(stats.report().contains("Unrecorded events this process: 1"))
    }

    @Test
    fun resetClearsCountersAndHistoryAndIgnoresOldPendingEvents() = runBlocking {
        var saved: String? = null
        var now = 100_000_000L
        val stats = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { now })
        stats.record("old", false, true, now)
        now += 1000
        assertTrue(stats.reset())
        stats.record("old", false, true, now - 1)
        assertTrue(stats.report().contains("First-page radio requests: 0"))
        now += 1000
        stats.record("old", false, true, now)
        assertTrue(stats.report().contains("First-page radio requests: 1"))
        assertTrue(stats.report().contains("Repeat within 24 hours: 0 (0.0%)"))
    }
}
