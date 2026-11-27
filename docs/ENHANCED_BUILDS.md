# Enhanced Android builds

Stable uses `.github/workflows/enhanced-shuffle-debug.yml`. New Labs use `.github/workflows/enhanced-lab-build.yml` through a small caller, like `enhanced-queue-management-debug.yml`. Both use the same composite action for cache, core patches, signing, ARM64 verification, package ID verification and regression tests. The publication script uses only the verified APK path.

## One persistent development app

For every **new** Lab, copy `enhanced-player-radio-debug.yml`. Change its trigger, expected branch, build channel, unique non-Stable release tag and APK filename, but keep **`lab_property: simpmusicSharedLabExperimental` and `expected_package: com.maxrave.simpmusic.dev.startradio`**. The latter deliberately reuses the previously installed Start Radio Lab package. The shared permanent signing key stays unchanged. The launcher name is SimpMusic Lab; the branch-specific build channel and commit remain visible in the app. Stable keeps its own package, `com.maxrave.simpmusic.dev`.

All future feature Labs install over that same development app, rather than creating a third app. Use the latest stable baseline and never lower its Android versionCode relative to the installed Lab; a same-versionCode signed APK can be installed manually as an update. Keep the existing historical Lab workflows and package IDs unchanged. Do not publish a historical branch as the next development APK; install only the intended current Lab. A Lab may contain experimental database changes, so back up before testing migrations. Stable's data remains separate.

Optional extra regression tests may be a `scripts/test_*.sh` file passed via `extra_tests`. Do not copy long historical workflows. Keep one feature per branch; the common Android identity does not merge branches.

The workflow validation caller runs one signed build and the full test suite without publishing an APK. Stable publication is allowed only on main; Lab publication rejects main and the Stable release destination. Existing historical workflows remain unchanged to preserve prior experiments and their source guards. Stable release notes generation remains in the Stable caller, so the readable release-notes PR can be merged separately.

A Stable release still requires a new versionCode, matching changelog entry, and explicit approval after any user-visible changes have been tested. Never use a manual Stable dispatch from a Lab branch.
