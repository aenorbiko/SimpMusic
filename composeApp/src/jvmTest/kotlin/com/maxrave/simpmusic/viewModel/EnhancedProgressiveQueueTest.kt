package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnhancedProgressiveQueueTest {
    @Test
    fun firstPairAppearsBeforeLastRadioCompletes() = runBlocking {
        val laterRadioGate = CompletableDeferred<Unit>()
        val firstEmitted = CompletableDeferred<Unit>()
        val emitted = mutableListOf<List<String>>()
        val worker = async {
            emitEnhancedPairsProgressively(
                seeds = listOf("A", "B", "C", "D"),
                excludedIds = setOf("A", "B", "C", "D"),
                id = { it },
                fetchRadio = { seed ->
                    when (seed) {
                        "A", "B" -> listOf("R_AB")
                        else -> {
                            laterRadioGate.await()
                            listOf("R_CD")
                        }
                    }
                },
            ) { pair, recommendation ->
                emitted.add(pair + listOfNotNull(recommendation))
                if (emitted.size == 1) firstEmitted.complete(Unit)
            }
        }
        withTimeout(2_000) { firstEmitted.await() }
        assertEquals(listOf("A", "B", "R_AB"), emitted.first())
        assertFalse(worker.isCompleted, "Later radio should still be fetching")

        laterRadioGate.complete(Unit)
        withTimeout(2_000) { worker.await() }
        assertEquals(
            listOf(listOf("A", "B", "R_AB"), listOf("C", "D", "R_CD")),
            emitted,
        )
    }

    @Test
    fun recommendationsUseOnlyTheirOwnPairAndNeverDuplicate() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        emitEnhancedPairsProgressively(
            seeds = listOf("A", "B", "C", "D", "E", "F"),
            excludedIds = setOf("A", "B", "C", "D", "E", "F"),
            id = { it },
            fetchRadio = { seed ->
                when (seed) {
                    "A", "B" -> emptyList()
                    "C", "D" -> listOf("R1", "R2", "R3")
                    else -> listOf("R3")
                }
            },
        ) { pair, recommendation ->
            emitted += pair + listOfNotNull(recommendation)
        }
        // An unrelated pair may have plenty of candidates, but it must NEVER
        // provide a recommendation for A/B (both its own radios were empty).
        assertEquals(
            listOf(listOf("A", "B"), listOf("C", "D", "R1"), listOf("E", "F", "R3")),
            emitted,
        )
        assertEquals(listOf(null, "R1", "R3"), emitted.map { it.getOrNull(2) })
    }

    @Test
    fun fullReturnedListsAndSecondRadioFallbackApplyToProgressiveStartup() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        val first = (1..30).map { "A$it" } + "Beyond30"
        val second = (1..30).map { "B$it" } + "Beyond30"
        emitEnhancedPairsProgressively(
            seeds = listOf("A", "B", "C", "D"),
            excludedIds = setOf("A", "B", "C", "D"),
            id = { it },
            fetchRadio = { seed ->
                when (seed) {
                    "A" -> first
                    "B" -> second
                    "C" -> listOf("OnlyFromC")
                    else -> listOf("FreshFromD")
                }
            },
        ) { pair, recommendation ->
            emitted += pair + listOfNotNull(recommendation)
        }
        // Beyond30 is shared and now eligible; prefer it to B1.
        // Second pair has no common radio candidate, so retain D's radio fallback.
        assertEquals(
            listOf(listOf("A", "B", "Beyond30"), listOf("C", "D", "FreshFromD")),
            emitted,
        )
    }

    @Test
    fun commonTrackAtThirtiethCandidateWorksDuringProgressiveStartup() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        emitEnhancedPairsProgressively(
            seeds = listOf("A", "B"),
            excludedIds = setOf("A", "B"),
            id = { it },
            fetchRadio = { seed ->
                if (seed == "A") (1..29).map { "A$it" } + "SharedAt30"
                else listOf("B1") + (1..28).map { "B$it" } + "SharedAt30"
            },
        ) { pair, recommendation ->
            emitted += pair + listOfNotNull(recommendation)
        }
        assertEquals(listOf(listOf("A", "B", "SharedAt30")), emitted)
    }

    @Test
    fun emptySecondRadioEmitsNextPairWithoutWaitingForOtherRadios() = runBlocking {
        val laterRadioGate = CompletableDeferred<Unit>()
        val firstEmitted = CompletableDeferred<Unit>()
        val emitted = mutableListOf<List<String>>()
        val worker = async {
            emitEnhancedPairsProgressively(
                seeds = listOf("A", "B", "C", "D"),
                excludedIds = setOf("A", "B", "C", "D"),
                id = { it },
                fetchRadio = { seed ->
                    when (seed) {
                        "A" -> listOf("OnlyFromA")
                        "B" -> emptyList()
                        else -> {
                            laterRadioGate.await()
                            listOf("LaterCandidate")
                        }
                    }
                },
            ) { pair, recommendation ->
                emitted += pair + listOfNotNull(recommendation)
                if (emitted.size == 1) firstEmitted.complete(Unit)
            }
        }
        withTimeout(2_000) { firstEmitted.await() }
        assertEquals(listOf("A", "B"), emitted.first())
        assertFalse(worker.isCompleted)
        laterRadioGate.complete(Unit)
        withTimeout(2_000) { worker.await() }
        assertEquals(
            listOf(listOf("A", "B"), listOf("C", "D", "LaterCandidate")),
            emitted,
        )
    }

    @Test
    fun cancellingOldSessionPreventsAnyLateQueueAppend() = runBlocking {
        val firstEmitted = CompletableDeferred<Unit>()
        val laterRadioGate = CompletableDeferred<Unit>()
        val emitted = mutableListOf<List<String>>()
        val worker = async {
            emitEnhancedPairsProgressively(
                seeds = listOf("A", "B", "C", "D"),
                excludedIds = setOf("A", "B", "C", "D"),
                id = { it },
                fetchRadio = { seed ->
                    if (seed in listOf("A", "B")) listOf("R_AB")
                    else {
                        laterRadioGate.await()
                        listOf("R_CD")
                    }
                },
            ) { pair, recommendation ->
                emitted.add(pair + listOfNotNull(recommendation))
                if (emitted.size == 1) firstEmitted.complete(Unit)
            }
        }
        withTimeout(2_000) { firstEmitted.await() }
        worker.cancelAndJoin()
        laterRadioGate.complete(Unit)
        assertEquals(listOf(listOf("A", "B", "R_AB")), emitted)
        assertTrue(worker.isCancelled)
    }

    @Test
    fun oddFinalSourceTrackIsStillAppendedWithoutInventingRecommendation() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        emitEnhancedPairsProgressively(
            seeds = listOf("A", "B", "C"),
            excludedIds = setOf("A", "B", "C"),
            id = { it },
            fetchRadio = { listOf("R1") },
        ) { pair, recommendation ->
            emitted.add(pair + listOfNotNull(recommendation))
        }
        assertEquals(listOf(listOf("A", "B", "R1"), listOf("C")), emitted)
    }
    @Test
    fun extraPagesOnlyWhenNoFirstPageMatchAndRemainAnonymousAtCaller() = runBlocking {
        val calls = mutableListOf<Pair<String, String?>>()
        val emitted = mutableListOf<List<String>>()
        val reports = mutableListOf<EnhancedPairTiming<String>>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B", "C", "D"),
            excludedIds = setOf("A", "B", "C", "D"),
            id = { it },
            fetchPage = { seed, token ->
                calls.add(seed to token)
                when {
                    seed == "A" && token == null -> EnhancedRadioPage(listOf("OnlyA"), "A2")
                    seed == "B" && token == null -> EnhancedRadioPage(listOf("OnlyB"), "B2")
                    seed == "A" -> EnhancedRadioPage(listOf("Shared"))
                    seed == "B" -> EnhancedRadioPage(listOf("Shared"))
                    else -> EnhancedRadioPage(listOf("AlreadyShared"), "unused")
                }
            },
            onDecision = { _, _, timing -> reports.add(timing) },
        ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        assertEquals(listOf(listOf("A", "B", "Shared"), listOf("C", "D", "AlreadyShared")), emitted)
        assertEquals(setOf("A" to "A2", "B" to "B2"), calls.filter { it.second != null }.toSet())
        assertEquals(0, reports[0].firstMatches)
        assertEquals(1, reports[1].firstMatches)
        assertEquals(null, reports[1].secondA)
    }

    @Test
    fun missingOrFailedContinuationKeepsOriginalBFallback() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B"), excludedIds = emptySet(), id = { it },
            fetchPage = { seed, token ->
                if (seed == "A") EnhancedRadioPage(listOf("OnlyA"))
                else if (token == null) EnhancedRadioPage(listOf("OnlyB"), "B2")
                else EnhancedRadioPage(emptyList(), status = "Error")
            },
        ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        assertEquals(listOf(listOf("A", "B", "OnlyB")), emitted)
    }

    @Test
    fun extraPageDoesNotCompareBeyondOneHundredReturnedTracks() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B"), excludedIds = emptySet(), id = { it },
            fetchPage = { seed, token ->
                if (token == null) EnhancedRadioPage((1..50).map { "$seed-$it" }, "next")
                else EnhancedRadioPage((51..100).map { "$seed-$it" } + "TooFar")
            },
        ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        assertEquals(listOf(listOf("A", "B", "B-1")), emitted)
    }

    @Test
    fun firstFourPairsExtendConcurrentlyBeforeLaterPairsCanTakeSlots() = runBlocking {
        val firstExtraStarted = CompletableDeferred<Unit>()
        val releaseExtras = CompletableDeferred<Unit>()
        val initialSeeds = mutableSetOf<String>()
        val extraSeeds = mutableSetOf<String>()
        val emitted = mutableListOf<List<String>>()
        var active = 0
        var maxActive = 0
        val seeds = (1..10).map { "S$it" }
        val worker = async {
            emitPagedEnhancedPairs(
                seeds = seeds, excludedIds = seeds.toSet(), id = { it },
                fetchPage = { seed, token ->
                    active++
                    maxActive = maxOf(maxActive, active)
                    try {
                        if (token == null) {
                            initialSeeds.add(seed)
                            EnhancedRadioPage(listOf("Only-$seed"), "next")
                        } else {
                            extraSeeds.add(seed)
                            if (extraSeeds.size == 8) firstExtraStarted.complete(Unit)
                            releaseExtras.await()
                            EnhancedRadioPage(listOf("Extra-$seed"))
                        }
                    } finally { active-- }
                },
            ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        }
        withTimeout(2_000) { firstExtraStarted.await() }
        assertEquals(seeds.take(8).toSet(), initialSeeds)
        assertEquals(seeds.take(8).toSet(), extraSeeds)
        assertEquals(8, maxActive)
        assertTrue(emitted.isEmpty())
        releaseExtras.complete(Unit)
        withTimeout(2_000) { worker.await() }
        assertEquals(seeds.chunked(2).map { it + "Only-${it[1]}" }, emitted)
        assertTrue(maxActive <= 8)
    }

    @Test
    fun laterPairFetchesExtraIfEarlierPairConsumesItsOnlyInitialMatch() = runBlocking {
        val extraCalls = mutableListOf<String>()
        val emitted = mutableListOf<List<String>>()
        val reports = mutableListOf<EnhancedPairTiming<String>>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B", "C", "D"), excludedIds = setOf("A", "B", "C", "D"), id = { it },
            fetchPage = { seed, token ->
                if (token == null) EnhancedRadioPage(listOf("Shared", "Only-$seed"), "next")
                else {
                    extraCalls.add(seed)
                    EnhancedRadioPage(listOf("NextShared"))
                }
            },
            onDecision = { _, _, timing -> reports.add(timing) },
        ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        assertEquals(listOf(listOf("A", "B", "Shared"), listOf("C", "D", "NextShared")), emitted)
        assertEquals(setOf("C", "D"), extraCalls.toSet())
        assertEquals(listOf(1, 0), reports.map { it.firstMatches })
    }

    @Test
    fun laterPairCanFinishExtraPagesButCannotEmitBeforeEarlierPair() = runBlocking {
        val earlyGate = CompletableDeferred<Unit>()
        val laterReady = CompletableDeferred<Unit>()
        val emitted = mutableListOf<List<String>>()
        val worker = async {
            emitPagedEnhancedPairs(
                seeds = listOf("A", "B", "C", "D"), excludedIds = emptySet(), id = { it },
                fetchPage = { seed, token ->
                    if (token == null) EnhancedRadioPage(listOf("Only-$seed"), "next")
                    else if (seed in listOf("A", "B")) {
                        earlyGate.await()
                        EnhancedRadioPage(listOf("FirstShared"))
                    } else {
                        if (seed == "D") laterReady.complete(Unit)
                        EnhancedRadioPage(listOf("LaterShared"))
                    }
                },
            ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        }
        withTimeout(2_000) { laterReady.await() }
        assertTrue(emitted.isEmpty())
        earlyGate.complete(Unit)
        withTimeout(2_000) { worker.await() }
        assertEquals(listOf(listOf("A", "B", "FirstShared"), listOf("C", "D", "LaterShared")), emitted)
    }

    @Test
    fun finalMetadataSelectorKeepsContinuationPolicyAndDeduplicationAcrossPairs() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        val calls = mutableListOf<Pair<String, String?>>()
        val seeds = listOf("A", "B", "C", "D")
        emitPagedEnhancedPairs(
            seeds = seeds,
            excludedIds = seeds.toSet(),
            id = { it },
            fetchPage = { seed, token ->
                calls.add(seed to token)
                if (token == null) EnhancedRadioPage(
                    if (seed in listOf("A", "C")) listOf("Evidence-$seed") else listOf("Fallback-$seed", "Candidate"),
                    "next",
                ) else EnhancedRadioPage(emptyList())
            },
            chooseFinal = { a, b, used, id ->
                EnhancedShufflePlanner.analyzePairWithMetadata(a, b, used, id) { item, rank ->
                    RadioMetadataCandidate(item, item, listOf(if (item.startsWith("Evidence") || item == "Candidate") "Shared" else "Other"), null, rank)
                }
            },
        ) { pair, recommendation -> emitted.add(pair + listOfNotNull(recommendation)) }
        assertEquals(listOf(listOf("A", "B", "Candidate"), listOf("C", "D", "Fallback-D")), emitted)
        assertEquals(4, calls.count { it.second != null })
    }
    @Test
    fun failedDeferredTokenRefreshesFirstAndSkipsUnneededNewContinuation() = runBlocking {
        val calls = mutableListOf<Pair<String, String?>>()
        val emitted = mutableListOf<String?>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B"), excludedIds = emptySet(), id = { it },
            fetchPage = { seed, token ->
                calls.add(seed to token)
                when {
                    seed == "A" && token == null -> EnhancedRadioPage(listOf("Old"), "old", fromCache = true)
                    seed == "B" && token == null -> EnhancedRadioPage(listOf("Shared"))
                    token == "old" -> EnhancedRadioPage(emptyList(), status = "Error", retryFirst = true)
                    else -> error("Unexpected token")
                }
            },
            refreshFirst = { EnhancedRadioPage(listOf("Shared"), "new") },
        ) { _, recommendation -> emitted.add(recommendation) }
        assertEquals(listOf<String?>("Shared"), emitted)
        assertFalse(calls.any { it.second == "new" })
    }

    @Test
    fun failedDeferredTokenUsesOnlyRefreshedPagesWhenMoreCandidatesNeeded() = runBlocking {
        val calls = mutableListOf<String?>()
        val emitted = mutableListOf<String?>()
        emitPagedEnhancedPairs(
            seeds = listOf("A", "B"), excludedIds = emptySet(), id = { it },
            fetchPage = { seed, token ->
                if (seed == "B") EnhancedRadioPage(listOf("Shared"))
                else {
                    calls.add(token)
                    when (token) {
                        null -> EnhancedRadioPage(listOf("Old"), "old", fromCache = true)
                        "old" -> EnhancedRadioPage(emptyList(), status = "Error", retryFirst = true)
                        "new" -> EnhancedRadioPage(listOf("Shared"))
                        else -> error("Unexpected token")
                    }
                }
            },
            refreshFirst = { EnhancedRadioPage(listOf("Fresh"), "new") },
            onDecision = { _, _, timing -> assertEquals(listOf("Fresh"), timing.firstA.tracks) },
        ) { _, recommendation -> emitted.add(recommendation) }
        assertEquals(listOf<String?>("Shared"), emitted)
        assertEquals(listOf(null, "old", "new"), calls)
    }

}
