#!/usr/bin/env bash
# Publish one revision-specific APK on its branch release, then a metadata-only channel pointer.
set -euo pipefail
: "${SIMP_VERIFIED_APK:?verified APK required}"
test -s "$SIMP_VERIFIED_APK"
test "$LAB_PROPERTY" = simpmusicSharedLabExperimental
test "$EXPECTED_PACKAGE" = com.maxrave.simpmusic.dev.startradio
test "$GITHUB_REF" = "refs/heads/$EXPECTED_BRANCH"
test "$EXPECTED_BRANCH" != main
test "$TAG" != enhanced-lab-latest
[[ "$TAG" =~ ^enhanced-[a-z0-9-]+-latest$ ]]
[[ "$ASSET" =~ ^SimpMusic-[A-Za-z0-9-]+-arm64\.apk$ ]]
[[ "$GITHUB_RUN_ID" =~ ^[0-9]+$ ]]
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]]
revision="$GITHUB_RUN_ID"
code="$(sed -n 's/^version-code = "\([0-9]*\)"$/\1/p' gradle/libs.versions.toml)"
[[ "$code" =~ ^[0-9]+$ ]]
name="${ASSET%-arm64.apk}-r${revision}-arm64.apk"
check_head() {
  test "$(gh api "repos/$GITHUB_REPOSITORY/branches/$EXPECTED_BRANCH" --jq .commit.sha)" = "$GITHUB_SHA"
  test "$(gh api "repos/$GITHUB_REPOSITORY/compare/main...$GITHUB_SHA" --jq .status)" = ahead
}
check_head
# A completed newer workflow must never be replaced by an old rerun.
if gh release view enhanced-lab-latest --json body > "$RUNNER_TEMP/lab-pointer.json" 2>/dev/null; then
  old="$(python3 -c 'import json,re,sys; b=json.load(open(sys.argv[1]))["body"]; m=re.findall(r"^Lab revision: ([0-9]+)$", b, re.M); assert len(m)==1; print(m[0])' "$RUNNER_TEMP/lab-pointer.json")"
  if [ "$old" -ge "$revision" ]; then
    echo "Shared Lab already points to revision $old; not republishing $revision."
    exit 0
  fi
fi
test -n "$WHATS_NEW"
while IFS= read -r line; do
  [ -z "$line" ] && continue
  [[ "$line" == "- "* ]]
  [ "${#line}" -le 100 ]
done <<< "$WHATS_NEW"
notes="$(printf "What's new\n\n%s\n\nPackage: %s\nAndroid build: %s\nLab revision: %s\nSource commit: %s\nLab release: %s\nLab asset: %s\n" "$WHATS_NEW" "$EXPECTED_PACKAGE" "$code" "$revision" "$GITHUB_SHA" "$TAG" "$name")"
# A revision-specific filename cannot silently download a newer/different build.
bash scripts/publish_enhanced_apk.sh "$TAG" "$name" "SimpMusic $CHANNEL" "$notes"
check_head
if gh release view enhanced-lab-latest >/dev/null 2>&1; then
  gh release edit enhanced-lab-latest --notes "$notes"
else
  gh release create enhanced-lab-latest --target "$GITHUB_SHA" --title "SimpMusic Lab (latest update)" --notes "$notes" --prerelease
fi
# Verify pointer publication before removing older APKs for this branch only.
test "$(gh release view enhanced-lab-latest --json body --jq .body)" = "$notes"
gh release view "$TAG" --json assets --jq '.assets[].name' | while IFS= read -r asset; do
  if [[ "$asset" == "${ASSET%-arm64.apk}-r"*-arm64.apk && "$asset" != "$name" ]]; then
    gh release delete-asset "$TAG" "$asset" --yes
  fi
done
echo "Shared Lab APK: https://github.com/$GITHUB_REPOSITORY/releases/download/$TAG/$name"
