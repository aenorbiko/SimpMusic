# SimpMusic Enhanced — current feature backlog

Last reviewed: 2026-11-28 (Australia/Brisbane).

**Release checkpoint: Android build 96 (radio cache and updates).** User approved Stable promotion on 2026-11-28 after Lab phone tests. The signed one-hour Lab passed [CI run 46099381052](https://github.com/aenorbiko/SimpMusic/actions/runs/46099381052). Stable 96 publication verified (CI run 46100640729, commit 9676edb3); phone acceptance remains to be verified; check the [current signed Stable release](https://github.com/aenorbiko/SimpMusic/releases/tag/enhanced-latest).

- [Download Stable APK](https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-latest/SimpMusic-Enhanced-arm64.apk)
- [Release history and technical notes](CHANGELOG.md)
- [Build and signing rules](docs/ENHANCED_BUILDS.md)
- [Archived original detailed backlog through Stable 90](https://github.com/aenorbiko/SimpMusic/blob/1065b274a93a57aa86dc1b24ab8fe4e9d8684971/ENHANCED_FEATURE_BACKLOG.md) — historical experiments and original acceptance notes, not current status.

## Active phase — stabilization and optimization (user-approved 2026-11-28)

Pause new features until this phase is completed and reviewed. Use one independent branch per behavior change, short Lab acceptance, then explicit Stable promotion. Collect comparable baseline and follow-up measurements; avoid speculative tuning and unnecessary APK builds.

### 1. Baseline performance (Stable 96)
- [ ] Verify installed Stable 96 update, retained queue, pause, settings and authorization; use Stable for ordinary listening. Confirm independent Lab channel on next feature build.
- [ ] Measure RAM (cold/warm cache, entries and tracks), CPU / metadata analysis, time to first recommendation, network counts and failures, storage writes, playback errors under parallel requests, and battery under comparable conditions. Distinguish measured data from estimates; use Android profiling when a phone connection is available. No background wakeups for instrumentation.
- [ ] Use existing diagnostics first; add only missing measurement points. Keep test workload, build and conditions documented.

**Baseline sample 1 — 2026-11-28 05:23 UTC (user phone, reported Stable 96):** Liked Music, fresh statistics reset (cache not cleared), all 14 seeds were cache misses, two expired lookups, 14 successful non-empty first pages, 14 continuations, 28 completed requests, no first-page failures or unrecorded events. Seven reported recommendations, all B-only rank 1, no eligible or raw A+B overlap/title+artist/shared artist; no Full Track attempts. Decision timestamps from batch start: 7.289, 9.000, 10.687, 10.825, 18.002, 19.699, 20.679 s. Mean first-page request 4559.5 ms, second-page 4030.0 ms; request range 1956–5875 ms. Zero request semaphore wait; ordered-decision wait 1155 ms for pair 4 only. Unattributed decision wall time after subtracting reported admission/initial/extension/ordered waits: 162, 123, 153, 162, 165, 156, 149 ms (not CPU or full diagnostics time; diagnostics created after decision timestamp). Previous cumulative report: 56 first requests (55 nonempty), 55 continuations, 111 total, 5 repeats within 24h, none within 1h; process cache counters 0/14 hits. Baseline conditions/device version not independently confirmed. No direct memory, CPU, battery, UI insertion or disk-write measurements yet. Use equivalent workload and cold/warm cache distinction for follow-up; random seeds and network variability limit comparisons.

### 2. Missing recommendation diagnostics
- [ ] Count processed pairs, added/missing recommendations and evidence-based failure reasons; keep a bounded local history of failed pairs with request outcomes and exclusions. Do not change ranking or introduce extra requests.

### 3. External song links
- [ ] Treat repeated identical links as separate events and consume intents correctly.
- [ ] Reuse Start Radio's stale-response protection: newer playback actions must win over earlier requests.
- [ ] Investigate existing metadata methods as fallback for missing videoDetails; verify exact video ID, never substitute a different recording. Preserve working simpmusic.org/app/watch links and Android app separation. Test same-link retries, cold/warm starts, rapid A/B links, switching playlists and unavailable IDs.

### 4. Playback errors
- [ ] Distinguish connectivity, actual timeout, sign-in required, private/unavailable recording, metadata failure and unknown playback errors; do not invent causes. Keep technical details in App Log.
- [ ] Investigate legitimate support for authenticated personal uploads (not yet confirmed). Stable 95 without Enhanced failed on a recording that official YouTube Music played while anonymous YouTube reported private; App Log showed LOGIN_REQUIRED and No URL found. Do not change extraction or authorization until the supported mechanism is established.

### 5. Enhanced analysis
- [ ] Reuse normalized metadata and analysis results across recommendation selection and diagnostics. Preserve exact output, ranking, exclusions and diagnostics; regression-test and measure CPU/time.

### 6. Cache memory
- [ ] Keep 64 seeds and fixed 60-minute TTL unless measured evidence supports tuning. Prune expired entries at existing block/cache operations, no periodic wakeups; evaluate safe Android memory-pressure handling. Preserve in-flight snapshots and continuation matching.
- [ ] Lab already verified first/second-page reuse, two successful deferred continuations, and six avoided requests. One-hour TTL and recovery tested automatically; rejected deferred tokens not yet observed on phone. Actual heap use not measured.

### 7. Statistics writes
- [ ] Accumulate counters in memory; flush after 8 completed network requests, or on the next result when more than 5 seconds have elapsed since the last save, and after normal completion/cancellation of a block or opening/copying statistics. No independent timer, wakeup or extra network request.
- [ ] Preserve reset ordering, out-of-order event handling, rollback protection and existing persisted history; serialize flushes, retain pending data on write failures. Target at most 7 unsaved completed events between successful count-triggered writes in normal operation; hard process death or I/O failure may lose more, and no guarantee of final lifecycle callbacks. Never affect queue, playlists or settings.

### 8. Enhanced session storage
- [ ] Serialize source metadata once and store shuffled order by video ID, retaining compatibility with existing sessions and recovery behavior. Measure payload and write overhead; do not assume equivalent heap savings.

### 9. Progressive refill
- [ ] Reuse startup progressive builder for subsequent queue refill; append each ready two-source group and optional recommendation in order without waiting for all seven pairs. Preserve current track, pause and position; advance cursor, membership, exclusions and recovery checkpoints only for committed groups. Test cancellation, stale sessions, process death mid-block and duplicate prevention.

### 10. Final measurements
- [ ] Compare against Stable 96 baseline under equivalent workloads, report actual effects and outstanding limitations. Review before resuming new features.

## Deferred features (retain existing scope)

### Bluetooth device-specific Lyrics Offset

- [ ] Provide a default offset plus individual values for Bluetooth headphones, car and speaker; select the active device's value automatically and restore the default when disconnected.
- [ ] Inspect and reuse only suitable code from `feature/enhanced-optimisation`, rather than merging the entire old multi-feature prototype.
- [ ] Verify Android permissions, device identification, switching/disconnect, manual offset, backup compatibility and persistence. Phone-test with at least two devices.

### Existing Full Track observation

- [ ] Continue to observe Full Track real-world replacements: build 87 is shipped and automated coverage passed, but a successful longer replacement has not yet been observed on the phone. Do not reopen the implementation without evidence of a defect.

### Backup / Restore safety and portability

- [ ] Review current backup contents, restoration behavior and protection of account credentials. Existing YouTube Music authorization restoration works according to phone testing; do not rebuild it unnecessarily.
- [ ] Add compatibility coverage for future Bluetooth offset profiles and relevant settings; test across Lab upgrades. Review old prototype separately from other features.

### Prepare for wider distribution — later

- [ ] Ko-fi/donation link and public-facing description/credits; confirm scope before implementation. Stable and Lab launcher icon design is approved.
- [ ] Review release packaging for other CPU architectures and public distribution; keep ARM64-only development builds for now.
- [ ] Consider immutable versioned release tags and a verified update manifest if useful. Current stable-only updater works.

### Upstream integration — later, independent

- [ ] Compare new official SimpMusic releases and pinned `core` submodule against our latest Stable. Adopt needed fixes selectively; do not overwrite Enhanced or automatically merge upstream.
- [ ] Use a dedicated integration branch and Lab, regression tests and phone acceptance, then explicit approval. Preserve account, playlists, queue, lyrics, Radio, notifications and Enhanced behavior.
- [ ] Continue incremental refactoring only when a feature or demonstrated problem justifies it; avoid broad rewrites. Queue management and shared build workflows were already refactored in builds 91–92.

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
| 96 | Radio metadata cache and independent Lab updates; Lab accepted, Stable published; phone acceptance pending |

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

Stable 96 is published. Begin baseline measurements and stabilization in the agreed order. New features including device-specific Lyrics Offset are deferred. This documentation-only change must be bundled into the next approved Stable release, not trigger an APK on its own.
