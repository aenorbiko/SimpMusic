package com.maxrave.simpmusic.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnhancedAboutDetailsTest {
    @Test
    fun stableAboutShowsEnhancedBuildAndCreditsOriginalAuthor() {
        val about = assertNotNull(
            enhancedAboutDetails(
                buildChannel = "Enhanced Stable",
                buildCode = 66,
                baseVersion = "2.5.0",
                commit = "518934a58e71a960a945a5a995deeef0e4fe88b4",
            )
        )
        assertEquals("SimpMusic Enhanced", about.title)
        assertEquals("Build 66 · Enhanced Stable", about.buildLabel)
        assertEquals("Based on SimpMusic v2.5.0", about.baseVersionLabel)
        assertEquals("Original developer: maxrave-dev", about.originalDeveloperLabel)
        assertEquals("Enhanced Stable · 518934a5", about.revisionLabel)
    }

    @Test
    fun labIdentifiesItsOwnChannelWithoutClaimingItIsStable() {
        val about = assertNotNull(enhancedAboutDetails("About Lab", 66, "v2.5.0", "abcdef123"))
        assertEquals("Build 66 · About Lab", about.buildLabel)
        assertEquals("Based on SimpMusic v2.5.0", about.baseVersionLabel)
        assertTrue(about.revisionLabel.contains("About Lab"))
    }

    @Test
    fun upstreamBuildKeepsItsOriginalAboutIdentity() {
        assertNull(enhancedAboutDetails("", 66, "2.5.0", "abcdef12"))
        assertNull(enhancedAboutDetails("  ", 66, "2.5.0", "abcdef12"))
    }
}
