#!/usr/bin/env python3
"""Ensure development assembly produces precisely one ARM64-only APK."""
from pathlib import Path
import sys
import zipfile

apk = Path(sys.argv[1])
outputs = sorted(apk.parent.glob("*.apk"))
assert outputs == [apk], f"Expected only {apk.name}; got {[p.name for p in outputs]}"
with zipfile.ZipFile(apk) as archive:
    abis = {name.split("/")[1] for name in archive.namelist()
            if name.startswith("lib/") and name.endswith(".so")}
assert abis == {"arm64-v8a"}, f"Unexpected APK native ABIs: {abis}"
print(f"Verified single ARM64 APK: {apk.stat().st_size / 1024**2:.1f} MiB")
