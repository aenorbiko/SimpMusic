#!/usr/bin/env python3
"""Generate human-readable Stable release notes from CHANGELOG.md."""
import re
import sys
from pathlib import Path


def extract_user_notes(changelog, build):
    heading = rf"^### Android build {int(build)} [^\n]+$"
    matches = list(re.finditer(heading, changelog, re.MULTILINE))
    if len(matches) != 1:
        raise ValueError(f"Expected one changelog entry for build {build}")
    remainder = changelog[matches[0].end():]
    section = re.split(r"^#{1,3} ", remainder, maxsplit=1, flags=re.MULTILINE)[0]
    match = re.search(r"^\*\*What's new\*\*\s*\n(.*?)^\*\*Technical details\*\*\s*$",
                      section, re.MULTILINE | re.DOTALL)
    if not match:
        raise ValueError(f"Build {build} needs What's new and Technical details sections")
    bullets = [line.strip() for line in match.group(1).splitlines() if line.strip()]
    if not bullets or any(not line.startswith("- ") or not line[2:].strip() for line in bullets):
        raise ValueError(f"Build {build} needs nonempty plain-language bullet points")
    return "\n".join(bullets)


def release_notes(changelog, build, source, history_url):
    summary = extract_user_notes(changelog, build)
    return (f"What's new\n\n{summary}\n\n"
            f"Full version history: {history_url}\n\n"
            f"SimpMusic Enhanced Stable (ARM64)\n"
            f"Android build: {int(build)}\nSource commit: {source}\n")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        raise SystemExit("Usage: generate_stable_release_notes.py CHANGELOG BUILD SOURCE HISTORY_URL")
    path, build, source, history = sys.argv[1:]
    print(release_notes(Path(path).read_text(encoding="utf-8"), build, source, history), end="")
