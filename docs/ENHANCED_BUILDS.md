# Enhanced Android builds

Stable uses `.github/workflows/enhanced-shuffle-debug.yml`. New Labs use `.github/workflows/enhanced-lab-build.yml` through a small caller, like `enhanced-queue-management-debug.yml`. Both use the same composite action for cache, core patches, signing, ARM64 verification, package ID verification and regression tests. The publication script uses only the verified APK path.

## Launcher icons

Enhanced Stable uses the original glass SimpMusic logo with two solid blue, white-outlined sparkles in the upper-left white area and a subtle blue lower-right wave. Shared Labs use the same original logo and two sparkles, with a purple diagonal corner labelled LAB. Both standard and round adaptive icons have a themed monochrome variant (the Lab variant uses monochrome lettering rather than a colored corner). Gradle chooses the icons by build channel/shared Lab property; existing package IDs, permanent signing, historical Lab artwork and unlabelled upstream builds are unchanged. The common workflow checks both manifest icon resource IDs in the compiled APK.

## Shared Lab update feed

New shared Lab callers should set `shared_update: true` and provide short `whats_new` bullets. Copy the shared Lab updater workflow as the template. The reusable workflow serializes shared publications, verifies the current branch head and that it is ahead of main, and publishes one revision-named APK in its existing branch-specific release. A metadata-only `enhanced-lab-latest` release points to that APK. The app validates both releases, package, source, revision, asset name/URL and size; Android enforces signing and package on manual install. Older runs cannot replace newer revisions. GitHub workflow run ID is the Lab revision, independent of the Android versionCode; rebuilds of the same completed run do not supersede it. Historical branch APKs remain separate, and obsolete assets on the current branch are removed only after the pointer is updated.

Stable continues to check only `enhanced-latest`. A Lab without the new updater must install the first updater APK manually. No second copy of the APK is uploaded for the pointer.

## One persistent development app

For every **new** Lab, copy `enhanced-shared-lab-updater-debug.yml`. Change its trigger, expected branch, build channel, unique non-Stable release tag and APK filename, but keep **`lab_property: simpmusicSharedLabExperimental` and `expected_package: com.maxrave.simpmusic.dev.startradio`**. The latter deliberately reuses the previously installed Start Radio Lab package. The shared permanent signing key stays unchanged. The launcher name is SimpMusic Lab; the branch-specific build channel and commit remain visible in the app. Stable keeps its own package, `com.maxrave.simpmusic.dev`.

All future feature Labs install over that same development app, rather than creating a third app. Use the latest stable baseline and never lower its Android versionCode relative to the installed Lab; a same-versionCode signed APK can be installed manually as an update. Keep the existing historical Lab workflows and package IDs unchanged. Do not publish a historical branch as the next development APK; install only the intended current Lab. A Lab may contain experimental database changes, so back up before testing migrations. Stable's data remains separate.

Optional extra regression tests may be a `scripts/test_*.sh` file passed via `extra_tests`. Do not copy long historical workflows. Keep one feature per branch; the common Android identity does not merge branches.

The workflow validation caller runs one signed build and the full test suite without publishing an APK. Stable publication is allowed only on main; Lab publication rejects main and the Stable release destination. Existing historical workflows remain unchanged to preserve prior experiments and their source guards. Stable release notes generation remains in the Stable caller, so the readable release-notes PR can be merged separately.

A Stable release still requires a new versionCode, matching changelog entry, and explicit approval after any user-visible changes have been tested. Never use a manual Stable dispatch from a Lab branch.
