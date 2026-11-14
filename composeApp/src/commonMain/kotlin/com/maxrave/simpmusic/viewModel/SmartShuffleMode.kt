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

/** Identify which original source queue is already playing before reshuffling. */
internal fun smartShuffleMatchesOriginalQueue(sourceId: String, queuePlaylistId: String?): Boolean {
    if (queuePlaylistId.isNullOrBlank()) return false
    return when {
        sourceId == "LM" -> queuePlaylistId == "LM"
        sourceId.startsWith("playlist:") -> queuePlaylistId == sourceId.removePrefix("playlist:")
        sourceId.startsWith("album:") -> queuePlaylistId == sourceId.removePrefix("album:")
        sourceId.startsWith("local:") -> queuePlaylistId == sourceId.removePrefix("local:")
        else -> false
    }
}
