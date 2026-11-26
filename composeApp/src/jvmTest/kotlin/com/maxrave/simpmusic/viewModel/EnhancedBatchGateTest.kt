package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EnhancedBatchGateTest {
    @Test
    fun duplicateCursorCannotGenerateOrCommitTwice() = runBlocking {
        val gate = EnhancedBatchGate()
        var cursor = 14
        var generated = 0
        var committed = 0
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.runIfCurrent({ cursor == 14 }) {
                generated++
                entered.complete(Unit)
                finish.await()
                committed++
                cursor += 14
            }
        }
        entered.await()
        val duplicate = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.runIfCurrent({ cursor == 14 }) {
                generated++
                committed++
                cursor += 14
            }
        }
        assertEquals(1, generated)
        assertFalse(duplicate.isCompleted)
        finish.complete(Unit)
        first.join()
        duplicate.join()
        assertEquals(1, generated)
        assertEquals(1, committed)
        assertEquals(28, cursor)
        gate.runIfCurrent({ cursor == 28 }) { generated++ }
        assertEquals(2, generated)
    }

    @Test
    fun cancellationReleasesGateAndStaleSessionDoesNotRun() = runBlocking {
        val gate = EnhancedBatchGate()
        var session = 1
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.runIfCurrent({ session == 1 }) {
                entered.complete(Unit)
                finish.await()
            }
        }
        entered.await()
        var staleRan = false
        val stale = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.runIfCurrent({ session == 1 }) { staleRan = true }
        }
        session = 2
        old.cancelAndJoin()
        stale.join()
        assertFalse(staleRan)
        var currentRuns = 0
        gate.runIfCurrent({ session == 2 }) { currentRuns++ }
        assertEquals(1, currentRuns)
    }
}
