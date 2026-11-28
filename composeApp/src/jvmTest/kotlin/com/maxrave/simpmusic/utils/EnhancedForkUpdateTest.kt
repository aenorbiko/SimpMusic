package com.maxrave.simpmusic.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class EnhancedForkUpdateTest {
    private fun githubRelease(
        code: Int = 66,
        tag: String = "enhanced-latest",
        assetName: String = "SimpMusic-Enhanced-arm64.apk",
        assetUrl: String = ENHANCED_STABLE_APK_URL,
        assetSize: Long = 72_000_000,
        draft: Boolean = false,
        body: String = "SimpMusic Enhanced Stable (ARM64)\nAndroid build: $code\nSource commit: abcdef",
    ): String = buildJsonObject {
        put("tag_name", tag)
        put("draft", draft)
        put("body", body)
        put("published_at", "2026-11-16T09:40:00Z")
        put("assets", JsonArray(listOf(buildJsonObject {
            put("name", assetName)
            put("browser_download_url", assetUrl)
            put("size", assetSize)
            put("updated_at", "2026-11-16T09:53:05Z")
        })))
    }.toString()

    @Test
    fun acceptsOurStableReleaseWithAndroidVersionAndExpectedApk() {
        val release = assertNotNull(parseEnhancedStableRelease(githubRelease()))
        assertEquals(66, release.buildCode)
        assertEquals("2026-11-16T09:53:05Z", release.releaseTime)
        assertTrue(release.notes.contains("Android build: 66"))
    }

    @Test
    fun rejectsUpstreamLabOrUntrustedDownloads() {
        assertNull(parseEnhancedStableRelease(githubRelease(tag = "v2.5.0")))
        assertNull(parseEnhancedStableRelease(githubRelease(assetUrl = "https://evil.invalid/app.apk")))
        assertNull(parseEnhancedStableRelease(githubRelease(assetName = "SimpMusic-Enhanced-Relevance-arm64.apk")))
        assertNull(parseEnhancedStableRelease(githubRelease(assetSize = 0)))
        assertNull(parseEnhancedStableRelease(githubRelease(draft = true)))
        assertNull(parseEnhancedStableRelease(githubRelease(body = "Original SimpMusic v2.5.0")))
        assertNull(parseEnhancedStableRelease("invalid json"))
    }

    @Test
    fun offersOnlyHigherAndroidVersionCode() {
        assertTrue(isNewerEnhancedBuild(65, "Enhanced build 66"))
        assertFalse(isNewerEnhancedBuild(66, "Enhanced build 66"))
        assertFalse(isNewerEnhancedBuild(67, "Enhanced build 66"))
        assertFalse(isNewerEnhancedBuild(65, "v2.6.0"))
        assertFalse(isNewerEnhancedBuild(65, "Enhanced build nonexistent"))
    }

    private fun labPointer(revision: Long = 100, code: Int = 95): LabReleasePointer =
        LabReleasePointer(revision, code, "enhanced-test-latest", "SimpMusic-Test-r$revision-arm64.apk", "a".repeat(40))

    private fun labBody(p: LabReleasePointer) =
        "What's new\n\n- New Lab updater.\n\nPackage: $ENHANCED_LAB_PACKAGE\nAndroid build: ${p.buildCode}\nLab revision: ${p.revision}\nSource commit: ${p.commit}\nLab release: ${p.tag}\nLab asset: ${p.asset}"

    @Test
    fun sharedLabUsesRevisionAndRejectsDowngradesAndOtherPackages() {
        val p = labPointer()
        assertTrue(isSharedEnhancedLab("Any Feature Lab", ENHANCED_LAB_PACKAGE, 99))
        assertFalse(isSharedEnhancedLab("Enhanced Stable", ENHANCED_LAB_PACKAGE, 99))
        assertFalse(isSharedEnhancedLab("Any Feature Lab", ENHANCED_STABLE_PACKAGE, 99))
        assertFalse(isSharedEnhancedLab("Any Feature Lab", ENHANCED_LAB_PACKAGE, 0))
        assertTrue(isNewerLabBuild(95, 99, p))
        assertFalse(isNewerLabBuild(95, 100, p))
        assertFalse(isNewerLabBuild(96, 99, p))
    }

    @Test
    fun labPointerMustMatchVerifiedBranchAssetAndNotes() {
        val p = labPointer()
        val body = labBody(p)
        val pointer = githubRelease(tag = "enhanced-shared-lab-feed-latest", body = body)
        assertEquals(p, parseLabPointer(pointer))
        assertNull(parseLabPointer(githubRelease(tag = "enhanced-latest", body = body)))
        assertNull(parseLabPointer(githubRelease(tag = "enhanced-shared-lab-feed-latest", body = body.replace(ENHANCED_LAB_PACKAGE, ENHANCED_STABLE_PACKAGE))))
        val url = "https://github.com/aenorbiko/SimpMusic/releases/download/${p.tag}/${p.asset}"
        val release = githubRelease(tag = p.tag, assetName = p.asset, assetUrl = url, body = body)
        assertEquals(url, assertNotNull(parseLabAsset(release, p)).downloadUrl)
        assertNull(parseLabAsset(release, labPointer(101)))
        assertNull(parseLabAsset(githubRelease(tag = p.tag, assetName = p.asset, assetUrl = "https://evil.invalid/app.apk", body = body), p))
        assertEquals("- New Lab updater.", enhancedUserNotes(body))
        assertEquals("See full version history.", enhancedUserNotes("Android build: 95"))
    }

    @Test
    fun neverSuggestsStableApkToLabOrUpstreamPackage() {
        assertTrue(isEnhancedStablePackage("Enhanced Stable", ENHANCED_STABLE_PACKAGE))
        assertFalse(isEnhancedStablePackage("Update Lab", ENHANCED_STABLE_PACKAGE))
        assertFalse(isEnhancedStablePackage("Enhanced Stable", "com.maxrave.simpmusic.dev.updates"))
        assertFalse(isEnhancedStablePackage("Enhanced Stable", "com.maxrave.simpmusic"))
    }
}
