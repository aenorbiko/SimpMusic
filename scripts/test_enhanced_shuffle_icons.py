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
    assert "group(" not in icon and "scaleX" not in icon, "Do not shrink arrows"
patch = Path("scripts/apply_enhanced_core_patch.py").read_text()
assert patch.count("M15,20 q-0.42,0,-0.71,-0.29") == 2, "Regular native Shuffle must keep original arrows"
assert patch.count("M14.76,19.36") == 1, "Enhanced notification arrows should be scaled to 92 percent"
assert patch.count("M9.6,0.2 L11.05,2.15") == 1, "One prominent sparkle in native Enhanced"
assert patch.count("M9.45,21.05 Q10.25,21.05") == 1, "One dot in Enhanced"
assert patch.count('android:width="24dp"') == 3
assert patch.count('android:fillType="evenOdd"') == 2
assert patch.count(' H15 Q') == 2 and patch.count(' H15 Z') == 2, 'Regular native icons must use valid horizontal commands'
assert ' L15 Q' not in patch and ' L15 Z' not in patch, 'No malformed single-argument L path commands'
assert "GenericCommandButton.Repeat(repeatState = control.repeatState)" not in patch, "Preserve Previous in notification"
assert 'val recommendedTrackIds = MutableStateFlow<Set<String>>(emptySet())' in patch
assert 'metadata.copy(title = "✦ " + metadata.title)' in patch
shared = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt").read_text()
assert 'EnhancedMediaBridge.recommendedTrackIds.value = ids' in shared
assert 'setRecommendedTrackIds(_recommendedTrackIds.value + nextBatch.recommendedIds)' in shared
print("PASS: original 24dp Shuffle arrows unchanged; Enhanced star and dot; native icons match")
