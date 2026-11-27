#!/usr/bin/env python3
"""Integration guard; JVM tests exercise the shared planner and queue mutation."""
from pathlib import Path

root = Path("core")
for path in (
    "data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt",
    "data/src/jvmMain/kotlin/com/maxrave/data/mediaservice/JvmMediaPlayerHandlerImpl.kt",
):
    text = (root / path).read_text()
    radio = text.split("    override fun toggleRadio() {", 1)[1].split("    override fun toggleLike()", 1)[0]
    for required in ("startRadioRequestIsCurrent(", "planStartRadioQueue(", "replaceUpcomingKeepingCurrent(",
                     "removeUpcomingItem = ::removeUpcomingRadioItemOnOwner", "startRadioCommitRevision.incrementAndGet()",
                     "if (!stillCurrent()) return@collectLatest"):
        assert required in radio, (path, required)
    for forbidden in ("clearMediaItems(", "seekTo(", "player.prepare(", "playWhenReady =", "loadPlaylistOrAlbum(", "removeMediaItems("):
        assert forbidden not in radio, (path, forbidden)
    assert "player.removeMediaItemOnOwner(index)" in text
    assert "if (radioRevision != startRadioCommitRevision.get()) return@collect" in text
print("PASS: both ordinary radio handlers use the shared in-place mutation and reject stale requests.")
