#!/usr/bin/env bash
# Publish exactly the signed APK verified by the shared build action.
set -euo pipefail
TAG="$1"
ASSET="$2"
TITLE="$3"
NOTES="$4"
: "${SIMP_VERIFIED_APK:?Run the shared build and APK verification first}"
test -s "$SIMP_VERIFIED_APK"
if [ "$TAG" = enhanced-latest ]; then
  test "$GITHUB_REF" = refs/heads/main
  test "$ASSET" = SimpMusic-Enhanced-arm64.apk
else
  [[ "$TAG" =~ ^enhanced-[a-z0-9-]+-latest$ ]]
  test "$GITHUB_REF" != refs/heads/main
  [[ "$ASSET" =~ ^SimpMusic-[A-Za-z0-9-]+-arm64\.apk$ ]]
  test "$ASSET" != SimpMusic-Enhanced-arm64.apk
fi
cp "$SIMP_VERIFIED_APK" "$RUNNER_TEMP/$ASSET"
if gh release view "$TAG" >/dev/null 2>&1; then
  gh release upload "$TAG" "$RUNNER_TEMP/$ASSET" --clobber
  gh release edit "$TAG" --notes "$NOTES"
else
  gh release create "$TAG" "$RUNNER_TEMP/$ASSET" --target "$GITHUB_SHA" --title "$TITLE" --notes "$NOTES" --prerelease
fi
gh release view "$TAG" --json assets --jq '.assets[].name' | grep -Fx "$ASSET"
echo "APK: https://github.com/$GITHUB_REPOSITORY/releases/download/$TAG/$ASSET"
