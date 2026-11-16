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
    fun recommendationsUseExistingNearestRadioFallbackAndNeverDuplicate() = runBlocking {
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
        val initial = EnhancedShufflePlanner.selectForAllPairs(
            radioPairs = listOf(
                emptyList<String>() to emptyList(),
                listOf("R1", "R2", "R3") to listOf("R1", "R2", "R3"),
                listOf("R3") to listOf("R3"),
            ),
            excludedIds = setOf("A", "B", "C", "D", "E", "F"),
            id = { it },
        )
        assertEquals(initial, emitted.map { it.getOrNull(2) })
        assertEquals(3, emitted.size)
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
