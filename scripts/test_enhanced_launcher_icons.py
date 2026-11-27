#!/usr/bin/env python3
"""Check adaptive launcher resources and the compiled manifest's actual icon IDs."""
import argparse
import re
import subprocess
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path("androidApp/src/main/res")
ns = "{http://schemas.android.com/apk/res/android}"
gradle = Path("androidApp/build.gradle.kts").read_text()
manifest = Path("androidApp/src/main/AndroidManifest.xml").read_text()
assert 'isSharedLab -> "enhanced_launcher_lab"' in gradle
assert 'isEnhancedStable -> "enhanced_launcher_stable"' in gradle
assert 'else -> "ic_launcher"' in gradle
assert 'android:icon="${enhancedLauncherIcon}"' in manifest
assert 'android:roundIcon="${enhancedLauncherRoundIcon}"' in manifest
for kind in ("stable", "lab"):
    for suffix in ("", "_round"):
        adaptive = ET.parse(root / "mipmap-anydpi-v26" / f"enhanced_launcher_{kind}{suffix}.xml").getroot()
        assert adaptive.tag == "adaptive-icon"
        assert adaptive.find("foreground").get(ns + "drawable") == f"@drawable/enhanced_{kind}{suffix}_foreground"
        assert adaptive.find("monochrome").get(ns + "drawable") == f"@drawable/enhanced_{kind}_mono"
        layers = ET.parse(root / "drawable" / f"enhanced_{kind}{suffix}_foreground.xml").getroot()
        assert [x.get(ns + "drawable") for x in layers] == [
            f"@mipmap/ic_launcher{suffix}_foreground", f"@drawable/enhanced_{kind}_overlay"
        ]
    overlay = ET.parse(root / "drawable" / f"enhanced_{kind}_overlay.xml").getroot()
    stars = [p for p in overlay.findall("path") if p.get(ns + "fillColor") == "#FFF1FCFF"]
    assert len(stars) == 2
    assert (overlay.find("group") is not None) == (kind == "lab")
    assert ET.parse(root / "drawable" / f"enhanced_{kind}_mono.xml").getroot().tag == "layer-list"

parser = argparse.ArgumentParser()
parser.add_argument("--apk")
parser.add_argument("--aapt")
parser.add_argument("--channel", default="")
parser.add_argument("--lab-property", default="")
args = parser.parse_args()
if args.apk:
    chosen = ("enhanced_launcher_lab" if args.lab_property == "simpmusicSharedLabExperimental"
              else "enhanced_launcher_stable" if args.channel == "Enhanced Stable" else "ic_launcher")
    resources = subprocess.check_output([args.aapt, "dump", "resources", args.apk], text=True)
    tree = subprocess.check_output([args.aapt, "dump", "xmltree", args.apk, "AndroidManifest.xml"], text=True)
    for attribute, suffix in (("icon", ""), ("roundIcon", "_round")):
        resource = re.search(r"resource\s+(0x[0-9a-fA-F]+)\s+\S+:mipmap/" + chosen + suffix + r"\b", resources)
        assert resource, f"Missing {chosen}{suffix} in packaged resources"
        value = re.search(r"A: android:" + attribute + r"\([^)]*\)=@(0x[0-9a-fA-F]+)", tree)
        assert value and value.group(1).lower() == resource.group(1).lower(), f"Wrong manifest {attribute}"
    print(f"PASS: compiled APK selects {chosen} and its round variant.")
else:
    print("PASS: adaptive icons and build-channel selection.")
