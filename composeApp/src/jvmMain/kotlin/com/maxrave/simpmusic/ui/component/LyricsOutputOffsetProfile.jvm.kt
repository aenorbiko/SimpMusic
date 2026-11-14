package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable

// Desktop has no Android audio route. Keep the normal global Lyrics Offset.
@Composable
actual fun rememberLyricsOutputOffsetProfile(
    globalOffsetMs: Int,
    updateGlobalOffset: (Int) -> Unit,
): LyricsOutputOffsetProfile = LyricsOutputOffsetProfile(
    offsetMs = globalOffsetMs,
    deviceName = null,
    updateOffset = updateGlobalOffset,
)
