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
    fun topFifteenAndSecondRadioFallbackAlsoApplyToProgressiveStartup() = runBlocking {
        val emitted = mutableListOf<List<String>>()
        val first = (1..15).map { "A$it" } + "Beyond15"
        val second = (1..15).map { "B$it" } + "Beyond15"
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
        // Beyond15 is shared but not eligible; pick only B1 from B's top 15.
        // Second pair has no common radio candidate, so use D's radio.
        assertEquals(
            listOf(listOf("A", "B", "B1"), listOf("C", "D", "FreshFromD")),
            emitted,
        )
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
}
