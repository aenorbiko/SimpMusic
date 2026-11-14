#!/usr/bin/env python3
"""Regression checks for clean Spotify-style Enhanced icon with original shuffle arrows."""
from pathlib import Path
root = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/icon")
normal = (root / "Shuffle.kt").read_text()
enhanced = (root / "EnhancedShuffle.kt").read_text()
original_arrow = "moveTo(15f, 20f)"
def stock_arrow(s):
    start = s.index(original_arrow)
    end = s.index("\n          }", start)
    return s[start:end]
assert stock_arrow(normal) == stock_arrow(enhanced), "Arrow shapes differ between modes"
assert "moveTo(9.5f, 0.45f)" in enhanced, "Top-left sparkle missing"
assert "moveTo(9.45f, 21.05f)" in enhanced, "Bottom dot missing"
assert "moveTo(9.5f, 0.45f)" not in normal, "Normal shuffle must not have a star"
for icon in (normal, enhanced):
    assert "defaultWidth = 24.dp" in icon
    assert "defaultHeight = 24.dp" in icon
    assert "viewportWidth = 24f" in icon
    assert "viewportHeight = 24f" in icon
    assert "group(scaleX = 0.92f, scaleY = 0.92f, pivotX = 12f, pivotY = 12f)" in icon, "Both icons must scale arrow geometry equally"
patch = Path("scripts/apply_enhanced_core_patch.py").read_text()
assert patch.count("M14.76,19.36") == 3, "All native Shuffle states must use identical 92%-scale arrow paths"
assert patch.count("M14.76,19.36") == 1, "Enhanced notification arrows should be scaled to 92 percent"
assert patch.count("M9.6,0.2 L11.05,2.15") == 1, "One prominent sparkle in native Enhanced"
assert patch.count("M12,20.15 Q12.65,20.15") == 1, "One centered near-crossing dot in native Enhanced"
assert "moveTo(12f, 20.15f)" in enhanced, "One centered near-crossing dot in player Enhanced"
assert patch.count('android:width="24dp"') == 3
assert patch.count('android:fillType="evenOdd"') == 2
assert patch.count(' H15 Q') == 0 and patch.count(' H15 Z') == 0, 'Scaled native icons use explicit line segments rather than the unscaled H geometry'
assert ' L15 Q' not in patch and ' L15 Z' not in patch, 'No malformed single-argument L path commands'
assert "GenericCommandButton.Repeat(repeatState = control.repeatState)" not in patch, "Preserve Previous in notification"
assert 'val recommendedTrackIds = MutableStateFlow<Set<String>>(emptySet())' in patch
assert 'title = "✦ " + metadata.title' in patch
assert 'artist = metadata.artist?.let' in patch, 'Artist must indent below the title after the star'
assert 'artist = meta.artist?.removePrefix' in patch, 'Do not leak notification indent into the main player'
shared = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt").read_text()
assert 'EnhancedMediaBridge.recommendedTrackIds.value = ids' in shared
assert 'setRecommendedTrackIds(_recommendedTrackIds.value + nextBatch.recommendedIds)' in shared
assert "EnhancedShufflePlanner.firstBlock(" in shared, "Current song must seed first Enhanced pair"
assert "firstBatch.tracks.drop(1)" in shared, "Current track must never be played twice"
planner = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/EnhancedShufflePlanner.kt").read_text()
assert "currentLiked: T?" in planner and "upcomingLikedCount" in planner
print("PASS: original 24dp Shuffle arrows unchanged; Enhanced star and dot; native icons match")
