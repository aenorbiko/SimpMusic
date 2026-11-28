# SimpMusic Enhanced — current feature backlog

Last reviewed: 2026-11-28 (Australia/Brisbane).

**Release checkpoint: Android build 96 (radio cache and updates).** User approved Stable promotion on 2026-11-28 after Lab phone tests. The signed one-hour Lab passed [CI run 46099381052](https://github.com/aenorbiko/SimpMusic/actions/runs/46099381052). Stable 96 publication and phone acceptance remain to be verified; check the [current signed Stable release](https://github.com/aenorbiko/SimpMusic/releases/tag/enhanced-latest).

- [Download Stable APK](https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-latest/SimpMusic-Enhanced-arm64.apk)
- [Release history and technical notes](CHANGELOG.md)
- [Build and signing rules](docs/ENHANCED_BUILDS.md)
- [Archived original detailed backlog through Stable 90](https://github.com/aenorbiko/SimpMusic/blob/1065b274a93a57aa86dc1b24ab8fe4e9d8684971/ENHANCED_FEATURE_BACKLOG.md) — historical experiments and original acceptance notes, not current status.

## Active tasks, in proposed order

### 1. Enhanced radio metadata cache — implemented; Stable verification pending

- [x] Implement process-local anonymous radio metadata cache: 64 seeds, fixed 60-minute TTL, full returned pages, optional continuation, bounded recovery and cache statistics.
- [x] Lab phone verified first and second page reuse, deferred token success (2/2), six avoided requests; one-hour default and expiry tested by signed Lab CI.
- [ ] Verify Stable 96 installation and ordinary listening; monitor hits, capacity evictions and expiry. Actual heap use not measured. Recovery on rejected deferred token has automated coverage but has not occurred on the phone.
- [ ] Tune capacity or TTL only if normal-use evidence warrants it; no speculative extra builds.

### 2. Bluetooth device-specific Lyrics Offset

- [ ] Provide a default offset plus individual values for Bluetooth headphones, car and speaker; select the active device's value automatically and restore the default when disconnected.
- [ ] Inspect and reuse only suitable code from `feature/enhanced-optimisation`, rather than merging the entire old multi-feature prototype.
- [ ] Verify Android permissions, device identification, switching/disconnect, manual offset, backup compatibility and persistence. Phone-test with at least two devices.

### 3. Diagnose missing third recommendations — lower priority

- [ ] Provide a diagnostic for pairs that yield no eligible recommendation (including failed/empty radio, exclusion and exhausted candidates), without altering the selected tracks or adding network requests.
- [ ] Keep it separate from ranking or scheduling changes. Existing blue-star diagnostics describe chosen recommendations; the missing recommendation observed in Queue Management Lab was not conclusively diagnosed.
- [ ] Continue to observe Full Track real-world replacements: build 87 is shipped and automated coverage passed, but a successful longer replacement has not yet been observed on the phone. Do not reopen the implementation without evidence of a defect.

### 4. Backup / Restore safety and portability

- [ ] Review current backup contents, restoration behavior and protection of account credentials. Existing YouTube Music authorization restoration works according to phone testing; do not rebuild it unnecessarily.
- [ ] Add compatibility coverage for future Bluetooth offset profiles and relevant settings; test across Lab upgrades. Review old prototype separately from other features.

### 5. Prepare for wider distribution — later

- [ ] Ko-fi/donation link and public-facing description/credits; confirm scope before implementation. Stable and Lab launcher icon design is approved.
- [ ] Review release packaging for other CPU architectures and public distribution; keep ARM64-only development builds for now.
- [ ] Consider immutable versioned release tags and a verified update manifest if useful. Current stable-only updater works.

### 6. Upstream integration — later, independent

- [ ] Compare new official SimpMusic releases and pinned `core` submodule against our latest Stable. Adopt needed fixes selectively; do not overwrite Enhanced or automatically merge upstream.
- [ ] Use a dedicated integration branch and Lab, regression tests and phone acceptance, then explicit approval. Preserve account, playlists, queue, lyrics, Radio, notifications and Enhanced behavior.
- [ ] Continue incremental refactoring only when a feature or demonstrated problem justifies it; avoid broad rewrites. Queue management and shared build workflows were already refactored in builds 91–92.

### 7. Lab update channel and concise update dialogs — implemented; Stable verification pending

- [x] Permanent independent Lab update feed, increasing revision, validated branch APK and concise What's new with clickable history.
- [x] Lab phone accepted same-version check and in-app update into the cache Lab; account, settings, queue, current track and pause preserved.
- [ ] Verify Stable 96 update and channel isolation after merge; verify next Lab branch update as part of normal feature development, not a dedicated extra build.

### 8. Playback diagnostics for private / unavailable recordings — separate issue

- [ ] Investigate authenticated personal uploads (not yet confirmed) and private recording playback using the existing extractor and official YouTube Music comparison. Reproduced in Stable 95 without Enhanced; official YouTube Music plays the record, anonymous YouTube says private, and app log reports LOGIN_REQUIRED and No URL found.
- [ ] Distinguish access/availability failures from actual network timeouts in user-facing messages. Do not change extraction or authorization until the supported mechanism is established. Keep separate from the radio-cache release.

## Completed / shipped

See [CHANGELOG.md](CHANGELOG.md) for precise build history and evidence.

| Build | Completed work |
| --- | --- |
| Earlier–64 | Enhanced 2+1 for Liked Music and supported playlists/albums; synchronized Off → Shuffle → Enhanced, recommendation stars, one YouTube Music Like (player and notification), accidental queue swipe fix, progressive queue loading and refill, build identity |
| 65–67 | Public changelog, fork-aware update checks, About/credits and version identification |
| 68 | Enhanced session persistence, process/reboot restoration, Bluetooth media resumption |
| 76 | Anonymous A/B radio, conditional second pages, four-tier selection, progressive scheduling and Bluetooth notification-star recovery |
| 78 | Persistent local radio statistics and repeat windows |
| 85 | Strict duplicate protection, serialized batch refill, statistics reset, ARM64 builds and Gradle caching |
| 87 | Longer-version lookup for selected recommendations shorter than 90 seconds; keep original on no match/failure |
| 90 | Full source playlist pagination, statistics clock handling |
| 91 | Shared upcoming-queue mutation preserving current playback |
| 92 | Readable What's New and common build/test/publish infrastructure |
| 93 | Start Radio preserves current track, position and pause; prevents late radio replies overriding new playback; exits Enhanced correctly |
| 94 | Classic player Radio shortcut beside Playlist/Queue, comments in three-dot menu, persistent Lab package; phone-tested in final Lab |
| 95 | Distinct Stable and Lab launcher icons, two blue stars on white, purple LAB corner for development builds; final Lab phone-approved, Stable published; Stable appearance to verify |
| 96 | Radio metadata cache and independent Lab updates; Lab accepted, Stable promotion approved; publication and phone acceptance pending |

Repeat One / manual Next — phone-verified 2026-11-28: the current song repeats normally; manually pressing Next advances to the next track and disables Repeat One. No fix required.

Do not reopen confirmed fixes absent a regression. The previously discussed Like/swipe idea is not an active task: the existing one YouTube Music Like in the player and notification is sufficient; a separate gesture has not been confirmed.

## Old experiments and open pull requests

Historical experiments: do not merge old experimental branches wholesale.

| PR / branch | Current disposition |
| --- | --- |
| [#12](https://github.com/aenorbiko/SimpMusic/pull/12) anonymous radio | Superseded by the shipped build-76 implementation; closed |
| [#11](https://github.com/aenorbiko/SimpMusic/pull/11) restored notification star | Shipped through build 76; closed |
| [#9](https://github.com/aenorbiko/SimpMusic/pull/9) ephemeral playback session | Superseded by the accepted persistent recovery design; closed, do not merge |
| `feature/enhanced-radio-relevance` | Old top-15 algorithm superseded by the four-tier engine |
| `feature/enhanced-radio-cache` | Old prototype; reference for task 1 only |
| `feature/enhanced-optimisation` | Old multi-feature prototype; extract individual changes only if needed |

Branch cleanup is a separate maintenance task; check for unique commits and preserve historical work before deleting anything. The repository default branch is now `main`; `dev` is an upstream snapshot, not an integration target.

## Development and release rules

- One behavior change per independent feature branch, based on latest Stable `main`. Test automated regressions, then signed APK on the phone. Merge only after explicit user approval.
- Two Android apps: Stable **SimpMusic Enhanced** (`com.maxrave.simpmusic.dev`) and persistent **SimpMusic Lab** (`com.maxrave.simpmusic.dev.startradio`, deliberately reusing the installed Start Radio Lab identity). All new feature Labs use `simpmusicSharedLabExperimental` and the same permanent signing certificate. Historical experiment packages are unchanged.
- Preserve per-branch release tags, APK names and visible commit/build labels. Use the shared workflow template and keep Stable publication isolated from Labs. See [build rules](docs/ENHANCED_BUILDS.md).
- At every build launch provide the direct APK URL and GitHub Actions status URL; explain whether the APK is still the previous version or not yet available.
- Avoid redundant builds; verify changes and configuration first. If two consecutive attempts at the same issue fail, stop and document the tested hypotheses, results and remaining problem before proposing another approach.
- Every change merged/pushed to `main` must increase Android versionCode beyond published Stable and add a matching readable changelog entry. Bundle documentation-only changes into the next approved Stable release, rather than releasing a new APK solely for backlog edits.
- Verify published release, version, package and successful CI separately from code merge and phone acceptance.

## Next checkpoint

Stable 96 promotion approved; verify signed CI and release, then update Stable on the phone and verify retained data and cache statistics. Continue the separate private-recording diagnosis without mixing it into build 96. Device-specific Lyrics Offset remains the next planned independent feature.
