#!/usr/bin/env python3
"""Verify that every Enhanced Stable APK exposes an accurate public changelog."""
from pathlib import Path
import re
from generate_stable_release_notes import extract_user_notes, release_notes

root = Path(".")
changelog = (root / "CHANGELOG.md").read_text()
gradle = (root / "gradle/libs.versions.toml").read_text()
settings = (root / "composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/SettingScreen.kt").read_text()
release = (root / ".github/workflows/enhanced-shuffle-debug.yml").read_text()
cleanup = (root / ".github/workflows/cleanup-merged-branches.yml").read_text()

match = re.search(r'^version-code = "(\d+)"$', gradle, re.MULTILINE)
assert match, "Missing Android versionCode"
version = int(match.group(1))
assert f"### Android build {version} " in changelog, "Every stable Android build needs a changelog entry"

# Require a plain-language entry for the current release and keep all prior summaries readable.
for build in re.findall(r"^### Android build (\d+) ", changelog, re.MULTILINE):
    extract_user_notes(changelog, build)
summary = extract_user_notes(changelog, version)
notes = release_notes(changelog, version, "test-sha", "https://example.com/history")
assert notes.startswith("What's new\n\n" + summary)
assert f"Android build: {version}\n" in notes
assert "Source commit: test-sha\n" in notes
assert "**Technical details**" not in notes
assert "https://example.com/history" in notes
try:
    extract_user_notes("### Android build 999 — Test\n\n- Technical only\n", 999)
except ValueError:
    pass
else:
    raise AssertionError("A release without readable notes must fail")
assert "generate_stable_release_notes.py CHANGELOG.md" in release


assert "How is Enhanced different from the original SimpMusic?" in changelog
assert "Experimental features — Not in the current Stable build" in changelog
assert "Relevance Lab" in changelog and "Radio Cache Lab" in changelog
assert "https://github.com/maxrave-dev/SimpMusic" in changelog
assert "What's new in SimpMusic Enhanced" in settings, "What's New must be visible in About settings"
assert 'uriHandler.openUri("https://github.com/aenorbiko/SimpMusic/blob/main/CHANGELOG.md")' in settings
assert 'CHANGELOG_URL="https://github.com/' in release
assert 'RELEASE_NOTES=' in release
assert "pull_request_target:" in cleanup
assert "github.event.pull_request.merged == true" in cleanup
assert "github.event.pull_request.head.repo.full_name == github.repository" in cleanup
assert "compare/main..." in cleanup, "Never delete branches without checking for unique commits"
print(f"PASS: build {version} has public changelog, Settings link, Release notes and safe branch cleanup")
