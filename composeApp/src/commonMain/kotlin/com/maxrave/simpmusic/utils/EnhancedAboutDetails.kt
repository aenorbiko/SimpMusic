package com.maxrave.simpmusic.utils

/** Public identity of our Enhanced fork, distinct from the upstream app version. */
internal data class EnhancedAboutDetails(
    val title: String,
    val buildLabel: String,
    val baseVersionLabel: String,
    val originalDeveloperLabel: String,
    val revisionLabel: String,
)

/**
 * For upstream/unlabelled builds, keep the original About screen unchanged.
 * Lab packages share the Enhanced base but must identify their own channel.
 */
internal fun enhancedAboutDetails(
    buildChannel: String,
    buildCode: Int,
    baseVersion: String,
    commit: String,
): EnhancedAboutDetails? {
    if (buildChannel.isBlank()) return null
    val version = baseVersion.trim().removePrefix("v")
    return EnhancedAboutDetails(
        title = "SimpMusic Enhanced",
        buildLabel = "Build $buildCode · ${buildChannel.trim()}",
        baseVersionLabel = "Based on SimpMusic v$version",
        originalDeveloperLabel = "Original developer: maxrave-dev",
        revisionLabel = formatEnhancedBuildIdentity(buildChannel, commit),
    )
}
