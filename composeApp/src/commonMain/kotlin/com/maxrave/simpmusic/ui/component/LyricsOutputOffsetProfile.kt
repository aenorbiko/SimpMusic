package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable

/**
 * Effective lyric timing for the current output route.
 * The user's existing global offset remains the fallback for speakers/wired
 * playback and for Bluetooth outputs without a saved override.
 */
data class LyricsOutputOffsetProfile(
    val offsetMs: Int,
    val deviceName: String?,
    val updateOffset: (Int) -> Unit,
)

@Composable
expect fun rememberLyricsOutputOffsetProfile(
    globalOffsetMs: Int,
    updateGlobalOffset: (Int) -> Unit,
): LyricsOutputOffsetProfile
