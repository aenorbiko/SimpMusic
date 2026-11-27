# Enhanced Android builds

Stable uses `.github/workflows/enhanced-shuffle-debug.yml`. New Labs use `.github/workflows/enhanced-lab-build.yml` through a small caller, like `enhanced-queue-management-debug.yml`. Both use the same composite action for cache, core patches, signing, ARM64 verification, package ID verification and regression tests. The publication script uses only the verified APK path.

For a new Lab, copy the small queue management caller, change its trigger and expected branch, channel, existing Gradle experiment property (add an isolated property/package in androidApp/build.gradle.kts if needed), expected application ID, release tag and APK name. Use a unique non-Stable tag and application ID; verify no existing unrelated Lab uses them. Optional extra regression tests may be a `scripts/test_*.sh` file passed via `extra_tests`. Do not copy the long historical workflows. Keep one feature per branch.

The workflow validation caller runs one signed build and the full test suite without publishing an APK. Stable publication is allowed only on main; Lab publication rejects main and the Stable release destination. Existing historical workflows remain unchanged to preserve prior experiments and their source guards. Stable release notes generation remains in the Stable caller, so the readable release-notes PR can be merged separately.

A Stable release still requires a new versionCode, matching changelog entry, and explicit approval after any user-visible changes have been tested. Never use a manual Stable dispatch from a Lab branch.
