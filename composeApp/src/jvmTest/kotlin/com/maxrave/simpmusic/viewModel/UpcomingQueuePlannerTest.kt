package com.maxrave.simpmusic.viewModel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpcomingQueuePlannerTest {
    @Test
    fun preservesPlayingTrackAndPlayedHistoryOnShuffle() {
        val old = listOf("played", "current", "old-next", "old-later")
        val plan = UpcomingQueuePlanner.preserveCurrent(
            existingQueue = old,
            currentIndex = 1,
            proposedUpcoming = listOf("new-2", "current", "played", "new-1"),
            id = { it },
        )
        assertEquals(listOf("played", "current"), plan?.preserved)
        assertEquals(listOf("new-2", "new-1"), plan?.upcoming)
    }

    @Test
    fun enhancedTailRetainsTwoPlusOneSequence() {
        val plan = UpcomingQueuePlanner.preserveCurrent(
            existingQueue = listOf("old-liked", "currently-playing", "old-next"),
            currentIndex = 1,
            proposedUpcoming = listOf("L1", "L2", "R1", "L3", "L4", "R2"),
            id = { it },
        )
        assertEquals(
            listOf("old-liked", "currently-playing", "L1", "L2", "R1", "L3", "L4", "R2"),
            plan!!.preserved + plan.upcoming,
        )
    }

    @Test
    fun originalOrderContinuesAfterCurrentAndSkipsHistory() {
        assertEquals(
            listOf("D", "E"),
            UpcomingQueuePlanner.originalOrderAfterCurrent(
                liked = listOf("A", "B", "C", "D", "E"),
                currentId = "C",
                playedIds = setOf("B"),
                id = { it },
            ),
        )
    }

    @Test
    fun invalidCurrentNeverGeneratesUnsafeReplacement() {
        assertNull(UpcomingQueuePlanner.preserveCurrent(listOf("one"), 10, listOf("two")) { it })
    }
}
