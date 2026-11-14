#!/usr/bin/env python3
"""Detect incomplete UI coverage and accidentally shared APK release destinations."""
from pathlib import Path

root = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic")
screens = {
    "YouTube playlist": ("ui/screen/other/PlaylistScreen.kt", 2),
    "Album": ("ui/screen/other/AlbumScreen.kt", 2),
    "Local playlist": ("ui/screen/library/LocalPlaylistScreen.kt", 2),
    "Dynamic song lists": ("ui/screen/library/LibraryDynamicPlaylistScreen.kt", 1),
}
for title, (path, expected) in screens.items():
    data = (root / path).read_text()
    assert data.count("sharedViewModel.cycleSourceShuffle(") >= expected, (
        f"{title}: missing Enhanced entry points"
    )
    assert "SmartShuffleMode.ENHANCED" in data, f"{title}: Enhanced state icon missing"

vm = (root / "viewModel/SharedViewModel.kt").read_text()
assert "forceNewQueue = switchingCollection" in vm, "new collection must start its own queue"
assert "PlaylistType.ALBUM" in vm and "PlaylistType.PLAYLIST" in vm
assert "cycleLikedShuffle(" in vm, "Liked Songs compatibility lost"
workflow = Path(".github/workflows/enhanced-playlists-debug.yml").read_text()
assert "enhanced-playlists-latest" in workflow
assert "enhanced-latest" not in workflow, "must not replace stable APK"
assert "enhanced-lab-latest" not in workflow, "must not replace radio-cache Lab APK"
print("PASS: all playlist and album screens wired; stable releases isolated")
