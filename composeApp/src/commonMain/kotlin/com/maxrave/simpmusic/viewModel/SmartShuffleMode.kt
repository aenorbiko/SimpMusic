package com.maxrave.simpmusic.viewModel

/** One-button shuffle cycle shared by the playlist and Now Playing screens. */
enum class SmartShuffleMode {
    OFF,
    SHUFFLE,
    ENHANCED;

    fun next(): SmartShuffleMode =
        when (this) {
            OFF -> SHUFFLE
            SHUFFLE -> ENHANCED
            ENHANCED -> OFF
        }
}
