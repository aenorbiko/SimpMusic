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
assert "moveTo(12f, 0.9f)" in e[1] and "moveTo(12f, 18.75f)" in e[2]
assert "lineTo(15.25f, 4f)" in e[1], "Top star must be large"
assert "lineTo(13.35f, 20.55f)" in e[2], "Bottom star must be small"
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
assert patch.count("M20.235,9.39 16.455,7.369 16.455,8.5")==3, "All Android states must use identical arrow geometry"
assert patch.count("M12,0.9 L13.15,2.8 L15.25,4 L13.15,5.2 L12,7.1 L10.85,5.2 L8.75,4 L10.85,2.8Z")==1
assert patch.count("M12,18.75 L12.48,20.12 L13.35,20.55 L12.48,20.98 L12,22.35 L11.52,20.98 L10.65,20.55 L11.52,20.12Z")==1
print("PASS: same 24dp Shuffle arrows in both icons, 2 stars, active notification backgrounds, Previous slot restored")
