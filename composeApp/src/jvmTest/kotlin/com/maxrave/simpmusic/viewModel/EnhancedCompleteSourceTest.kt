package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EnhancedCompleteSourceTest {
    @Test
    fun loadsAll322SongsAndKeepsOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val result = completeShuffleSource((1..100).toList(), "p2", { it.toString() }) { token ->
            calls.add(token)
            when (token) {
                "p2" -> (101..200).toList() to "p3"
                "p3" -> (201..300).toList() to "p4"
                "p4" -> (301..322).toList() to null
                else -> error("Unexpected token")
            }
        }
        assertEquals((1..322).toList(), result)
        assertEquals(listOf("p2", "p3", "p4"), calls)
    }

    @Test
    fun noContinuationMakesNoRequestAndDeduplicates() = runBlocking {
        assertEquals(listOf(1, 2), completeShuffleSource(listOf(1, 2, 1), null, { it.toString() }) { error("No request") })
    }

    @Test
    fun failedAndLoopingPagesDoNotReturnTruncatedSource() = runBlocking {
        assertNull(completeShuffleSource(listOf(1), "x", { it.toString() }) { null })
        assertNull(completeShuffleSource(listOf(1), "x", { it.toString() }) { emptyList<Int>() to null })
        assertNull(completeShuffleSource(listOf(1), "x", { it.toString() }) { listOf(2) to "x" })
    }

    @Test
    fun duplicateBoundaryTracksAndConcurrentQueueGrowth() = runBlocking {
        assertEquals(listOf(1, 2, 3), completeShuffleSource(listOf(1, 2), "x", { it.toString() }) { listOf(2, 3) to null })
        assertTrue(shuffleSourceStillMatches(listOf(1, 2), listOf(1, 2, 3)) { it.toString() })
        assertFalse(shuffleSourceStillMatches(listOf(1, 2), listOf(2, 1, 3)) { it.toString() })
        assertFalse(shuffleSourceStillMatches(listOf(1, 2), listOf(1)) { it.toString() })
    }
}
