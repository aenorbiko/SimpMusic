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
assert patch.count("M15,20 q-0.42,0,-0.71,-0.29") == 3, "Android modes must use original arrows"
assert patch.count("M9.5,0.45 L10.32,2.25") == 1, "One sparkle in Enhanced"
assert patch.count("M9.45,21.05 Q10.25,21.05") == 1, "One dot in Enhanced"
assert patch.count('android:width="24dp"') == 3
assert patch.count('android:fillType="evenOdd"') == 2
assert "GenericCommandButton.Repeat(repeatState = control.repeatState)" not in patch, "Preserve Previous in notification"
print("PASS: original 24dp Shuffle arrows unchanged; Enhanced star and dot; native icons match")
