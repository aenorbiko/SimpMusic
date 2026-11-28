package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

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
        store().apply { record("a", false, false, start); flush() }
        store().apply { record("a", false, true, start + 100); flush() }
        store().apply { record("a", false, false, start + 200); flush() }
        store().apply { record("b", false, true, start + 300); flush() }
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
    fun delayedCompletionsPreserveObservationAndRepeatHistory() = runBlocking {
        var saved: String? = null
        val start = 100_000_000L
        var wall = start + 10 * 60_000L
        val stats = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { wall })
        stats.record("seed", false, true, start)
        stats.record("other", false, true, start + 5000)
        stats.record("late", false, true, start + 1000)
        stats.record("seed", false, true, start + 6000)
        val report = stats.report()
        assertTrue(report.contains("Observation period: 0 d 0 h 10 min"))
        assertTrue(report.contains("First-page radio requests: 4"))
        assertTrue(report.contains("Repeat within 20 minutes: 1 (25.0%)"))
        assertTrue(report.contains("clock resets: 0"))
        assertTrue(report.contains("Observation started (UTC):"))
    }

    @Test
    fun genuineWallClockRollbackKeepsCountersAndElapsedObservation() = runBlocking {
        var saved: String? = null
        val start = 100_000_000L
        var wall = start
        val stats = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { wall })
        stats.record("seed", false, true, wall)
        wall += 10 * 60_000L
        stats.record("other", false, true, wall)
        wall -= 60 * 60_000L
        stats.record("seed", false, true, wall)
        assertTrue(stats.report().contains("Observation period: 0 d 0 h 10 min"))
        assertTrue(stats.report().contains("First-page radio requests: 3"))
        assertTrue(stats.report().contains("Repeat within 24 hours: 0 (0.0%)"))
        assertTrue(stats.report().contains("clock resets: 1"))
        wall += 60_000L
        stats.record("seed", false, true, wall)
        assertTrue(stats.report().contains("Observation period: 0 d 0 h 11 min"))
        assertTrue(stats.report().contains("Repeat within 20 minutes: 1 (25.0%)"))
        assertTrue(stats.report().contains("clock resets: 1"))
    }

    @Test
    fun storageFailureDoesNotThrowIntoPlaybackAndIsReported() = runBlocking {
        val stats = EnhancedRadioRequestStatistics({ null }, { error("disk error") }, { 1000L })
        stats.record("a", false, true, 1000L)
        val report = stats.report()
        assertTrue(report.contains("Unrecorded events this process: 0"))
        assertTrue(report.contains("Pending statistics events: 1; failed flush attempts: 1"))
    }

    @Test
    fun fullTrackStatisticsPersistAndResetWithoutChangingRadioCounters() = runBlocking {
        var saved: String? = null
        val now = 100_000_000L
        fun store() = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { now })
        val original = store()
        original.record("seed", false, true, now)
        original.recordFullTrack(FullTrackDiagnostic(61, 177, FullTrackOutcome.REPLACED, 1200, 1000), now)
        original.recordFullTrack(FullTrackDiagnostic(40, null, FullTrackOutcome.NO_MATCH, 600, 500), now)
        original.recordFullTrack(FullTrackDiagnostic(50, null, FullTrackOutcome.TIMEOUT, 10000, null), now)
        original.recordFullTrack(FullTrackDiagnostic(60, null, FullTrackOutcome.ERROR, 200, 150), now)
        original.recordFullTrack(FullTrackDiagnostic(70, null, FullTrackOutcome.MISSING_METADATA, 0, null), now)
        assertTrue(original.flush())
        val report = store().report()
        assertTrue(report.contains("First-page radio requests: 1"))
        assertTrue(report.contains("Short recommendations: 5"))
        assertTrue(report.contains("Search attempts: 4"))
        assertTrue(report.contains("Replaced: 1"))
        assertTrue(report.contains("No suitable match: 1"))
        assertTrue(report.contains("Timeouts: 1"))
        assertTrue(report.contains("Errors: 1"))
        assertTrue(report.contains("Search incl. slot wait average/max: 3000 / 10000 ms"))
        assertTrue(report.contains("Search request average/max (3 started): 550 / 1000 ms"))
        assertTrue(store().reset())
        assertTrue(store().report().contains("Short recommendations: 0"))
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

    @Test
    fun batchesReadOnceAndFlushAtEightOrNextEventAfterFiveSeconds() = runBlocking {
        var saved: String? = null
        var reads = 0
        var writes = 0
        var mono = 0L
        val stats = EnhancedRadioRequestStatistics({ reads++; saved }, { writes++; saved = it }, { 100000L }, { mono })
        repeat(7) { stats.record("s$it", false, true, 100000) }
        assertEquals(0, writes)
        stats.recordPair(null, 100000)
        assertEquals(1, writes)
        mono = 4999
        stats.record("more", true, true, 100000)
        assertEquals(1, writes)
        mono = 5000
        stats.recordPair("Radio B empty", 100000)
        assertEquals(2, writes)
        assertTrue(stats.flush())
        assertEquals(2, writes)
        assertTrue(stats.report().contains("Pairs decided: 2; selected: 1; missing: 1"))
        assertEquals(1, reads)
        val reopened = EnhancedRadioRequestStatistics({ saved }, { saved = it }, { 100000L })
        assertTrue(reopened.report().contains("Radio B empty: 1"))
    }

    @Test
    fun failedFlushRetainsAggregateAndResetDoesNotResurrectIt() = runBlocking {
        var saved: String? = null
        var fail = true
        var wall = 100000L
        val stats = EnhancedRadioRequestStatistics({ saved }, { if (fail) error("disk") else saved = it }, { wall }, { 0L })
        repeat(8) { stats.record("seed", false, true, wall) }
        assertFalse(stats.flush())
        assertFalse(stats.reset())
        fail = false
        assertTrue(stats.flush())
        assertTrue(stats.report().contains("First-page radio requests: 8"))
        wall++
        assertTrue(stats.reset())
        stats.recordPair("Radio B empty", wall - 1)
        assertTrue(stats.report().contains("Pairs decided: 0; selected: 0; missing: 0"))
    }
}
