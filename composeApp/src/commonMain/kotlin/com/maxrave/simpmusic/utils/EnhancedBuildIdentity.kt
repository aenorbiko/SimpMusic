package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.BuildKonfig

/**
 * Identifies OUR built APK in screenshots independently of the upstream SimpMusic version.
 * Build channel is set by each signed build workflow; revision is the GitHub commit SHA.
 * Upstream/unlabelled builds keep the original Now Playing heading and footer.
 */
object EnhancedBuildIdentity {
    val reportCaption: String
        get() = formatEnhancedReportIdentity(BuildKonfig.enhancedBuildChannel, BuildKonfig.versionCode, BuildKonfig.enhancedLabRevision, BuildKonfig.enhancedBuildCommit)

    val screenshotCaption: String
        get() = formatEnhancedBuildIdentity(
            BuildKonfig.enhancedBuildChannel,
            BuildKonfig.enhancedBuildCommit,
        )
}

internal fun formatEnhancedBuildIdentity(channel: String, revision: String): String {
    val name = channel.trim()
    if (name.isEmpty()) return ""
    val sha = revision.trim().take(8)
    return if (sha.isEmpty()) name else "$name · $sha"
}

internal fun formatEnhancedReportIdentity(channel: String, build: Int, labRevision: String, commit: String): String = buildString {
    append(channel.ifBlank { "SimpMusic" })
    append(" · build $build")
    labRevision.toLongOrNull()?.takeIf { it > 0 }?.let { append(" · r$it") }
    commit.trim().take(8).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
}
