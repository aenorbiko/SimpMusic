package com.maxrave.simpmusic.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class EnhancedBuildIdentityTest {
    @Test
    fun labelShowsTheExactChannelAndFirstEightCommitCharacters() {
        assertEquals(
            "Relevance Lab · 4d077753",
            formatEnhancedBuildIdentity("Relevance Lab", "4d0777536fdc463ccff4f8b819fbf1137bc1b9f1"),
        )
    }

    @Test
    fun stableBuildHasItsOwnClearlyNamedIdentity() {
        assertEquals(
            "Enhanced Stable · 239d8e78",
            formatEnhancedBuildIdentity("Enhanced Stable", "239d8e78d13d5f96a48b81c1a103feec74ff360a"),
        )
    }

    @Test
    fun originalBuildKeepsTheUnmodifiedHeading() {
        assertEquals("", formatEnhancedBuildIdentity("", "abc123ef"))
    }

    @Test
    fun localBuildStillShowsItsChannel() {
        assertEquals("Identity Lab · local", formatEnhancedBuildIdentity("Identity Lab", "local"))
    }
}
