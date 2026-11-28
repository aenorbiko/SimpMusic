package com.maxrave.simpmusic.viewModel

import kotlin.test.Test
import kotlin.test.assertTrue

class EnhancedPerformanceDiagnosticsTest {
    @Test fun countsAndBoundsMissingPairsAndReset() {
        var tick = 0L
        val d = EnhancedPerformanceDiagnostics({ EnhancedProcessSample(2048, 4096, tick, 6) }, { tick++ })
        d.sample()
        val empty = EnhancedRadioPage<String>(emptyList(), status = "Error or no response")
        val timing = EnhancedPairTiming(empty, empty, null, null, 0, 0, 0, 0)
        val missing = EnhancedShufflePlanner.analyzePair(emptyList<String>(), emptyList(), emptySet()) { it }
        repeat(12) { d.pair("Seed-$it:", "B", missing, timing) }
        val selected = EnhancedShufflePlanner.analyzePair(listOf("C"), listOf("C"), emptySet()) { it }
        d.pair("A", "B", selected, timing)
        d.committed(1)
        d.selection(12); d.selection(8); d.diagnostic(3); d.statsWrite(); d.sessionWrite(10, 2); d.queueSave()
        d.sample(true)
        val report = d.report()
        assertTrue("Pairs decided: 13; selected: 1; missing: 12; recommendations committed: 1" in report)
        assertTrue("Radio B failed or no response: 12" in report)
        assertTrue("Seed-0:" !in report && "Seed-2:" in report)
        assertTrue("10 / 12 ms" in report && "Process PSS at report: 6 KiB" in report)
        d.reset()
        assertTrue("Pairs decided: 0" in d.report() && "Seed-2:" !in d.report())
    }
}
