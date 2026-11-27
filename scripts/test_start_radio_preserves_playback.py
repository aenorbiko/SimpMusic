#!/usr/bin/env python3
"""Guard Start Radio against resetting playback or applying stale results."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HANDLERS = (
    ROOT / "core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt",
    ROOT / "core/data/src/jvmMain/kotlin/com/maxrave/data/mediaservice/JvmMediaPlayerHandlerImpl.kt",
)

for path in HANDLERS:
    source = path.read_text()
    start = source.index("override fun toggleRadio()")
    end = source.index("override fun toggleLike()", start)
    body = source[start:end]
    for required in (
        "val requestedMediaItem = player.currentMediaItem ?: return@launch",
        "val requestedMediaId = requestedMediaItem.mediaId",
        "val requestedPositionMs = player.currentPosition",
        "val requestedPlayWhenReady = player.playWhenReady",
        "player.currentMediaItem?.mediaId != requestedMediaId",
        "player.setMediaItem(requestedMediaItem)",
        "player.seekTo(requestedPositionMs)",
        "load(downloaded = 0, index = 0)",
        "player.playWhenReady = requestedPlayWhenReady",
    ):
        assert required in body, f"{path}: missing {required!r}"
    assert "removeMediaItems(" not in body, f"{path}: uses potentially refused ranged removal"
    assert "addMediaItem(currentSong.toGenericMediaItem()" not in body, f"{path}: recreates current song at zero"
    assert "playWhenReady = true" not in body, f"{path}: forces playback on"

print("Start Radio preserves the current item, position, and play/pause state, and rejects stale responses.")
