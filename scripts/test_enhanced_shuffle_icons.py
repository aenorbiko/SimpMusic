#!/usr/bin/env python3
"""Regression checks for identical 24dp Shuffle arrows and notification slots."""
from pathlib import Path
import re
base = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/icon")
shuffle = (base / "Shuffle.kt").read_text()
enhanced = (base / "EnhancedShuffle.kt").read_text()
def paths(s):
    return re.findall(r'path\(fill = SolidColor\(Color.Black\)\) \{(.*?)\n          \}',s,re.S)
s,e=paths(shuffle),paths(enhanced)
assert len(s)==1 and len(e)==3,(len(s),len(e))
assert s[0]==e[0],"Shuffle arrow geometry must not change between modes"
assert "moveTo(12f, 1.57f)" in e[1] and "moveTo(12f, 17.67f)" in e[2]
for name,text in [("Shuffle",shuffle),("Enhanced",enhanced)]:
    for p in ("defaultWidth = 24.dp","defaultHeight = 24.dp","viewportWidth = 24f","viewportHeight = 24f"):
        assert p in text,(name,p)
    assert "group(" not in text
patch = Path("scripts/apply_enhanced_core_patch.py").read_text()
assert "GenericCommandButton.Repeat(repeatState = control.repeatState)" not in patch
assert ".setSlots(CommandButton.SLOT_BACK)" not in patch
assert "GenericCommandButton.Shuffle(" in patch and "GenericCommandButton.Like(ytLiked == true)" in patch
for drawable in ("baseline_simp_shuffle_off_24","baseline_simp_shuffle_on_24","baseline_simp_shuffle_enhanced_24"):
    assert drawable in patch,drawable
assert patch.count('android:width="24dp"')==3
assert patch.count('android:fillType="evenOdd"')==2
assert patch.count("M21.15,8.9 16.95,6.55")==3, "All three Android states must use identical arrow geometry"
print("PASS: same 24dp Shuffle arrows in both icons, 2 stars, active notification backgrounds, Previous slot restored")
