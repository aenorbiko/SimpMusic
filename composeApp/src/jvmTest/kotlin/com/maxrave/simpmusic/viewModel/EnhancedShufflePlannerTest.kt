package com.maxrave.simpmusic.viewModel

import kotlin.test.Test
import kotlin.test.assertEquals

class EnhancedShufflePlannerTest {
    @Test
    fun enablingEnhancedDuringAStartsWithBThenRecommendationOfAAndB() {
        val upcoming = ('B'..'Z').map(Char::toString)
        val first = EnhancedShufflePlanner.firstBlock(
            currentLiked = "A",
            shuffledUpcoming = upcoming,
            batchSize = 14,
            id = { it },
        )
        assertEquals(listOf("A") + upcoming.take(13), first.radioSeeds)
        assertEquals(13, first.upcomingLikedCount)
        assertEquals(true, first.hasCurrentSeed)
        // The recommended song for pair(A, B) must follow B, not C/D.
        val fullBlock = EnhancedShufflePlanner.mix(first.radioSeeds, listOf("R_AB"), id = { it })
        val queuedAfterCurrent = fullBlock.drop(1)
        assertEquals(listOf("B", "R_AB", "C"), queuedAfterCurrent.take(3))
        assertEquals("O", upcoming[first.upcomingLikedCount])
    }

    @Test
    fun enablingEnhancedWithoutCurrentSongUsesFourteenUpcomingLikedSongs() {
        val upcoming = (1..20).map { "L$it" }
        val first = EnhancedShufflePlanner.firstBlock(
            currentLiked = null,
            shuffledUpcoming = upcoming,
            batchSize = 14,
            id = { it },
        )
        assertEquals(upcoming.take(14), first.radioSeeds)
        assertEquals(14, first.upcomingLikedCount)
        assertEquals(false, first.hasCurrentSeed)
    }

    @Test
    fun repeatedCurrentInUpcomingIsNotQueuedTwice() {
        val first = EnhancedShufflePlanner.firstBlock(
            currentLiked = "A",
            shuffledUpcoming = listOf("A", "B", "C"),
            batchSize = 4,
            id = { it },
        )
        assertEquals(listOf("A", "B", "C"), first.radioSeeds)
        assertEquals(2, first.upcomingLikedCount)
    }

    @Test
    fun firstPairWithEmptyRadioStillGetsThirdTrackFromNearbyRadio() {
        val selected = EnhancedShufflePlanner.selectForAllPairs(
            radioPairs = listOf(
                emptyList<String>() to emptyList(),
                listOf("R1", "R2") to emptyList(),
                listOf("R3") to emptyList(),
            ),
            excludedIds = setOf("A", "B", "C", "D", "E", "F"),
            id = { it },
        )
        assertEquals(listOf("R1", "R2", "R3"), selected)
        val mixed = listOf("A", "B", selected[0], "C", "D", selected[1], "E", "F", selected[2])
        assertEquals(listOf("A", "B", "R1", "C", "D", "R2", "E", "F", "R3"), mixed)
    }

    @Test
    fun recommendationPriorityIsAlwaysThePairsOwnRadioWhenPresent() {
        val selected = EnhancedShufflePlanner.selectForAllPairs(
            radioPairs = listOf(
                listOf("OwnAB") to listOf("OwnAB", "OtherAB"),
                listOf("OwnCD") to listOf("OwnCD"),
            ),
            excludedIds = emptySet(),
            id = { it },
        )
        assertEquals(listOf("OwnAB", "OwnCD"), selected)
    }

    @Test
    fun fallbackDoesNotRepeatLikedOrPreviouslyRecommendedTracks() {
        val selected = EnhancedShufflePlanner.selectForAllPairs(
            radioPairs = listOf(
                emptyList<String>() to emptyList(),
                listOf("Liked", "Candidate") to listOf("Candidate", "SecondCandidate"),
                emptyList<String>() to emptyList(),
            ),
            excludedIds = setOf("Liked"),
            id = { it },
        )
        assertEquals(listOf("Candidate", "SecondCandidate", null), selected)
    }

    @Test
    fun emptyRadiosDoNotInventRecommendations() {
        val selected = EnhancedShufflePlanner.selectForAllPairs(
            radioPairs = listOf(
                emptyList<String>() to emptyList(),
                emptyList<String>() to emptyList(),
            ),
            excludedIds = emptySet(),
            id = { it },
        )
        assertEquals(listOf(null, null), selected)
    }

    @Test
    fun prefetchStartsBeforeTheEndOfTheFirstBlock() {
        assertEquals(false, shouldPrefetchEnhanced(15))
        assertEquals(true, shouldPrefetchEnhanced(14))
        assertEquals(true, shouldPrefetchEnhanced(8))
        assertEquals(true, shouldPrefetchEnhanced(0))
    }

    @Test
    fun roundRobinDoesNotClusterOneRadio() {
        val result = EnhancedShufflePlanner.roundRobinRecommendations(
            pools = listOf(listOf("A1", "A2", "A3"), listOf("B1", "B2", "B3")),
            excludedIds = emptySet(),
            limit = 6,
            id = { it },
        )
        assertEquals(listOf("A1", "B1", "A2", "B2", "A3", "B3"), result)
    }

    @Test
    fun skipsOwnedAndDuplicateRadioSongs() {
        val result = EnhancedShufflePlanner.roundRobinRecommendations(
            pools = listOf(listOf("Liked", "R1", "R2"), listOf("R1", "R3")),
            excludedIds = setOf("Liked"),
            limit = 4,
            id = { it },
        )
        assertEquals(listOf("R1", "R3", "R2"), result)
    }

    @Test
    fun twoLikedOneSuggestionRemainsInOrder() {
        val result = EnhancedShufflePlanner.mix(
            liked = listOf("L1", "L2", "L3", "L4", "L5"),
            recommended = listOf("R1", "R2"),
            id = { it },
        )
        assertEquals(listOf("L1", "L2", "R1", "L3", "L4", "R2", "L5"), result)
    }

    @Test
    fun choosesCommonRecommendationWhenBothRadiosAgree() {
        val pick = EnhancedShufflePlanner.selectForPair(
            firstRadio = listOf("SeedA", "OnlyA", "Common"),
            secondRadio = listOf("SeedB", "Common", "OnlyB"),
            usedIds = setOf("SeedA", "SeedB"),
            id = { it },
        )
        assertEquals("Common", pick)
    }

    @Test
    fun fallbackCanPreferSecondRadio() {
        val pick = EnhancedShufflePlanner.selectForPair(
            firstRadio = listOf("OnlyA"),
            secondRadio = listOf("OnlyB"),
            usedIds = emptySet(),
            id = { it },
            preferSecondOnFallback = true,
        )
        assertEquals("OnlyB", pick)
    }

    @Test
    fun excludesPreviouslyRecommendedAndAllLiked() {
        val pick = EnhancedShufflePlanner.selectForPair(
            firstRadio = listOf("Liked1", "Used", "Fresh"),
            secondRadio = listOf("Liked2", "Fresh"),
            usedIds = setOf("Liked1", "Liked2", "Used"),
            id = { it },
        )
        assertEquals("Fresh", pick)
    }

    @Test
    fun noRadioReturnsAllLikedTracks() {
        val result = EnhancedShufflePlanner.mix(listOf("L1", "L2", "L3"), emptyList()) { it }
        assertEquals(listOf("L1", "L2", "L3"), result)
    }
}
