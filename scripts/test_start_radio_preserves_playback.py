#!/usr/bin/env python3
"""Guard the patched Start Radio path against restarting/replacing the active item."""
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
    assert "player.currentMediaItem?.mediaId" in body, f"{path}: missing stale-request guard"
    assert "player.removeMediaItems(activeIndex + 1, itemCount) {}" in body, f"{path}: does not remove old upcoming items"
    assert "player.removeMediaItems(0, activeIndex) {}" in body, f"{path}: does not remove old history"
    assert "clearMediaItems()" not in body, f"{path}: clears the active item"
    assert "addMediaItem(currentSong.toGenericMediaItem()" not in body, f"{path}: re-adds and restarts the current song"
    assert "playWhenReady = true" not in body, f"{path}: forces playback on while replacing the queue"

print("Start Radio keeps the active item, playback position, and pause state.")
