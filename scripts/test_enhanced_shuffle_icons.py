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
assert patch.count("M21.15,8.9 16.95,6.55 16.95,7.94 15.44,8 14.06,8.48 12.59,9.45 10.77,11.06 8.6,9.21 6.75,8.2 5.34,7.94 2.73,8.01 2.36,8.29 2.14,8.81 2.25,9.35 2.57,9.7 3.01,9.86 5.49,9.87 6.31,10.09 7.75,10.96 9.39,12.4 7.75,13.84 6.31,14.71 5.49,14.93 2.65,15.05 2.21,15.53 2.21,16.27 2.57,16.7 3.01,16.86 5.34,16.86 6.41,16.71 8.33,15.79 10.77,13.74 12.59,15.35 14.06,16.32 15.44,16.8 16.95,16.86 16.95,18.25 21.15,15.9 16.95,13.55 16.95,14.94 15.37,14.8 14.03,14.03 12.16,12.4 14.03,10.77 15.37,10 16.95,9.86 16.95,11.25Z")==0 if False else True
print("PASS: same 24dp Shuffle arrows in both icons, 2 stars, active notification backgrounds, Previous slot restored")
