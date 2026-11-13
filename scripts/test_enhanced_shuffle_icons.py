#!/usr/bin/env python3
"""Static regression checks for the approved 24dp Option B icon."""
from pathlib import Path
import re
app = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/icon")
regular = (app / "Shuffle.kt").read_text()
enhanced = (app / "EnhancedShuffle.kt").read_text()
assert "defaultWidth = 24.dp" in enhanced
assert "viewportWidth = 24f" in enhanced
assert "defaultHeight = 24.dp" in enhanced
assert "viewportHeight = 24f" in enhanced
assert "group(" not in enhanced, "Shuffle arrows must not be scaled"
# Exact original shuffle path commands are kept byte-for-byte.
start = regular.index("            moveTo(15f, 20f)")
end = regular.index("\n          }",start)
assert regular[start:end] in enhanced, "Shuffle arrow path changed!"
assert "moveTo(4.5f, 8.5f)" in enhanced, "Option B star missing"
patch = Path("scripts/apply_enhanced_core_patch.py").read_text()
assert 'android:width="24dp"' in patch
assert 'android:fillType="evenOdd"' in patch
assert 'M21,1H3c-1.1,0' in patch, "Active background missing"
assert 'M4.5,8.5 L5.25,10.55' in patch, "Star cutout missing"
print("PASS: shuffle arrows unchanged, 24dp canvas, Option B star, enhanced active background")
