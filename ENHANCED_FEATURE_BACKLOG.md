# SimpMusic Enhanced — current feature backlog

Last reviewed: 2026-11-28 (Australia/Brisbane).

**Release checkpoint: Android build 101 (analysis reuse) — user approved promotion; publication and Stable phone acceptance pending.** Stable 100 signed publication verified (CI 46134249478, commit b36ed458).

- [Download Stable APK](https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-latest/SimpMusic-Enhanced-arm64.apk)
- [Release history and technical notes](CHANGELOG.md)
- [Build and signing rules](docs/ENHANCED_BUILDS.md)
- [Archived original detailed backlog through Stable 90](https://github.com/aenorbiko/SimpMusic/blob/1065b274a93a57aa86dc1b24ab8fe4e9d8684971/ENHANCED_FEATURE_BACKLOG.md) — historical experiments and original acceptance notes, not current status.

## Active phase — stabilization and optimization (user-approved 2026-11-28)

Pause new features until this phase is completed and reviewed. On 2026-11-28 the user prioritized statistics writes, session storage, cache memory and analysis ahead of external links and playback error fixes. Session storage must precede progressive refill. Use one independent branch per behavior change, short Lab acceptance, then explicit Stable promotion. Collect comparable baseline and follow-up measurements; avoid speculative tuning and unnecessary APK builds.

### 1. Baseline performance (Stable 96 / Diagnostics Lab — initial samples complete)
- [ ] Verify installed Stable 96 update, retained queue, pause, settings and authorization; use Stable for ordinary listening. Confirm independent Lab channel on next feature build.
- [ ] Measure RAM (cold/warm cache, entries and tracks), CPU / metadata analysis, time to first recommendation, network counts and failures, storage writes, playback errors under parallel requests, and battery under comparable conditions. Distinguish measured data from estimates; use Android profiling when a phone connection is available. No background wakeups for instrumentation.
- [x] Collect initial phone baseline using existing diagnostics and instrumented Lab; preserve reports and conditions below. Battery and controlled CPU/memory comparisons remain for follow-up.

**Baseline sample 1 — 2026-11-28 05:23 UTC (user phone, reported Stable 96):** Liked Music, fresh statistics reset (cache not cleared), all 14 seeds were cache misses, two expired lookups, 14 successful non-empty first pages, 14 continuations, 28 completed requests, no first-page failures or unrecorded events. Seven reported recommendations, all B-only rank 1, no eligible or raw A+B overlap/title+artist/shared artist; no Full Track attempts. Decision timestamps from batch start: 7.289, 9.000, 10.687, 10.825, 18.002, 19.699, 20.679 s. Mean first-page request 4559.5 ms, second-page 4030.0 ms; request range 1956–5875 ms. Zero request semaphore wait; ordered-decision wait 1155 ms for pair 4 only. Unattributed decision wall time after subtracting reported admission/initial/extension/ordered waits: 162, 123, 153, 162, 165, 156, 149 ms (not CPU or full diagnostics time; diagnostics created after decision timestamp). Previous cumulative report: 56 first requests (55 nonempty), 55 continuations, 111 total, 5 repeats within 24h, none within 1h; process cache counters 0/14 hits. Baseline conditions/device version not independently confirmed. No direct memory, CPU, battery, UI insertion or disk-write measurements yet. Use equivalent workload and cold/warm cache distinction for follow-up; random seeds and network variability limit comparisons.

**Instrumented baseline sample 2 — 2026-11-28 05:50 UTC, Performance Diagnostics Lab build 96, revision 46112717816, commit 2ed58513.** Signed CI passed; phone report confirms build identity and diagnostic UI, not all updater/missing-pair scenarios. Before reset: resident cache 0, managed heap 49,682 KiB, process PSS 320,651 KiB; restored session write 8,308 UTF-16 code units. Historic Lab radio stats (not this experiment): 149 first-page requests, 129 nonempty, 20 failed/empty, 119 continuation, 268 total; 2 short-track lookups, neither replaced.
- After reset: 14 successful first pages + 14 continuation requests, 28 network requests, zero cache hits/errors/unrecorded events, 14 live resident seeds, 1,361 stored track records; seven pairs decided/selected/committed, no missing pairs. Selection sources: five B-only, one title+artist, one shared artist, zero exact overlaps.
- Decision times: 8.074, 9.918, 11.318, 13.053, 18.064, 19.672, 21.030 s. Final selection wall time avg/max 47/60 ms (7); detailed diagnostics 168/224 ms (7). No request semaphore wait. Different random seeds/network conditions from Stable sample 1; not a controlled overhead comparison.
- 28 successful statistics writes, 11 successful Enhanced session writes, 2,507,803 UTF-16 code units total, 286 ms encoding, seven queue-persistence calls. These count logical writes/calls, not physical disk writes. Session write reasons and size distribution not captured; investigate before changing.
- Managed heap current/sample peak/limit 62,382/62,382/262,144 KiB (5 samples); PSS 447,753 KiB. Before/after differences +12,700 KiB heap and +127,102 KiB PSS; entire process, not cache-only attribution. PSS only at report; sampled peak not actual peak. CPU 139,835 ms over 81,536 ms elapsed (~1.715 core equivalents), all process activities, not Enhanced-only. No battery or controlled idle-playback comparison yet.
- Next: repeat in same Lab process to observe cache hits/residency; collect missing-pair counters in ordinary listening, do not deliberately induce failure. Need comparable playback-only baseline if evaluating CPU/battery. Do not introduce new APK solely for additional measurements unless necessary.

**Instrumented baseline sample 3 — 2026-11-28 05:55 UTC, same Lab 96 r46112717816, cumulative since 05:50 reset.** 14 pairs decided, 13 selected and committed, 1 missing. Phone screenshot confirms missing third recommendation for Chopin Fantaisie-Impromptu + The Gate (Cell), followed by next source pair. Radio A pages 49/48 OK, 67 eligible; Radio B first page 0 Error or no response, no continuation, 0 eligible. Confirms bounded missing-pair report and safe continuation; The Gate is also the known playback/metadata failure, but exact underlying cause remains unconfirmed. No change to recommendation policy.
- 26 first-page network requests (25 successful, 1 failed/empty), 25 continuation, 51 total; 2 first-page hits of 28, 2 continuation hits, 4 gross avoided requests, no evictions/expiry/deferred failures. Second block increment: 23 network requests, 4 cache-served pages, 11 new live seeds, 1,071 more cached track records; 25 resident/live seeds, 2,432 track records.
- Cumulative selection wall avg/max 42/60 ms (14); diagnostic wall avg/max 146/224 ms (14). 51 statistics writes, 21 session writes, 5,015,098 UTF-16 code units, 452 ms session encoding, 14 completed queue persistence calls. Second block increment: 23 stats writes, 10 session writes, 2,507,295 code units, 166 ms encoding, 7 queue persistence calls.
- Managed heap current/sample peak/limit 77,440/77,440/262,144 KiB (8 samples); PSS 433,231 KiB. Compared with first block: heap +15,058 KiB, PSS -14,522 KiB despite larger cache; cannot isolate cache memory. CPU 347,731 ms over 287,822 ms cumulative; incremental 207,896 ms CPU / 206,286 ms elapsed (~1.01 core equivalents), mixed playback/queue generation/UI. Need matched playback-only comparison, no battery attribution yet.
- Missing-pair phone acceptance now demonstrated. Performance diagnostic counters remain process-local; persistent counters deferred to grouped-statistics work; no background wakeups. Still need verify update dialog on next real update; Stable promotion subsequently approved.

**Instrumented baseline sample 4 — 2026-11-28 06:08 UTC, same Lab 96 r46112717816, normal Shuffle with Enhanced disabled, no reset.** Relative to sample 3: elapsed interval 810,693 ms (13m 30.7s), process CPU increment 697,568 ms (~0.8605 core equivalents). Interval includes switching mode, UI/report and playback; exact screen-off duration and track transitions not measured. No further Enhanced radio requests, cache accesses, decisions, statistics writes or queue-persistence calls. Cache remains 25 live entries / 2,432 track records. Managed heap 57,551 KiB (~56.2 MiB), sampled peak 77,440 KiB, limit 262,144 KiB; PSS 431,687 KiB (~421.6 MiB). Heap fell 19,889 KiB despite unchanged cache; PSS fell 1,544 KiB. Two additional successful session writes (23 cumulative), no increase in JSON payload or encoding milliseconds, consistent with empty-session clearing on mode change; cause not instrumented directly. CPU is whole-process wall interval, not comparable controlled workload or evidence of energy use. No direct battery measurements. Initial phone baseline and missing-pair reproduction complete; diagnostic Lab promotion subsequently approved by user. Retain build identity and use comparable workloads for subsequent optimization measurements.

### 2. Missing recommendation diagnostics — Lab verified; Stable promotion approved
- [x] Count processed pairs, added/missing recommendations and evidence-based failure reasons; keep a bounded local history of failed pairs with request outcomes and exclusions. Ranking unchanged; no extra requests. Phone verified a missing pair due to radio B failure. Persistent diagnostic counters remain for grouped-statistics work.

### 3. Statistics writes — Lab accepted; Stable 98 promotion approved
- [x] Accumulate counters in memory; flush after 8 accepted events (radio, Full Track or pair decision), or on the next result when more than 5 seconds have elapsed since the last save, and after normal completion/cancellation of a block or opening/copying statistics. No independent timer, wakeup or extra network request.
- [x] Persist new aggregate missing-recommendation counters without per-event writes; recent title history may remain bounded and process-local. Keep performance samples labeled by process.
- [x] Preserve reset ordering, out-of-order event handling, rollback protection and existing persisted history; serialize flushes, retain pending data on write failures. Target at most 7 unsaved completed events between successful count-triggered writes in normal operation; hard process death or I/O failure may lose more, and no guarantee of final lifecycle callbacks. Never affect queue, playlists or settings.

**Phone acceptance — 2026-11-28, Batched Statistics Lab build 97 r46115748331, bccd3652.** Two reset-separated blocks: 27 network requests, 6 successful stats writes, 7 pairs / 6 selected / 1 missing; then 25 requests, 5 writes, 7 / 6 / 1. Both reports had zero pending events, failed flushes and unrecorded events. Baseline 28 writes: 79–82% fewer logical saves per block, not a controlled CPU/energy comparison. Force-stop and reopen retained 25 requests, 7 pairs, 6 selected and one Radio B failure; process cache and counters reset correctly. Reopened session still wrote 358,502 UTF-16 code units in 275 ms. User approved Stable 98.

### 4. Enhanced session storage — Lab accepted; Stable 99 promotion approved
- [x] Serialize source metadata once and store shuffled order by video ID, retaining compatibility with existing sessions and recovery behavior. Measure payload and write overhead; do not assume equivalent heap savings.

**Phone acceptance — 2026-11-28, Compact Session Lab build 98 r46119527495 (fe43c374).** Upgrade restored visible queue, Enhanced and recommendation stars; preserved 25 requests and 7 pair counts. Restored session payload 183,651 versus previous 358,502 UTF-16 code units (48.8% smaller); encoding 53 versus 275 ms. Fresh block: 27 requests, 6 stats writes, 7 pairs / 6 selected / 1 missing; 10 session writes, 1,283,406 units total, 160 ms encoding. Force-stop/reopen restored visible remaining order and stars, retained 27 requests and pair counts; one compact session save 183,679 units / 82 ms, no radio requests. No phone Bluetooth-specific cold-start or controlled RAM/battery measurement; core bootstrap regression passed CI. No change to save frequency. User approved promotion.

### 5. Cache memory — Lab accepted; Stable 100 promotion approved
- [x] Keep 64 seeds and fixed 60-minute TTL. Prune expired entries during existing cache/report operations; preserve pinned in-flight snapshots and continuation matching. No periodic wakeups, extra network requests or Android memory-pressure listener.
- [x] Automated expiry and snapshot regression tests passed. Earlier Lab verified first/second-page reuse and deferred continuations. No further cache optimization or cache-size instrumentation requested.

**Phone acceptance — 2026-11-28, temporary Cache Memory 10min Lab build 99 r46130066201 (8e81acb6).** Two expiry intervals: 13 entries / 1,267 records, then 14 entries / 1,287 records, all removed; total 27 / 2,554. Process counters preserved, network totals unchanged during expiry intervals (27 and 53). Second interval PSS 416,255 → 378,663 KiB while cache still full, then 372,841 and 358,454 KiB after prune; managed heap 62,314 → 66,780 → 65,824 → 51,930 KiB. These are whole-process snapshots, not measured cache size; no direct cache-only memory attribution. Earlier hour-long test reset process counters and lost notification, so did not prove pruning. User requested leaving cache unchanged; temporary 10-minute branch must not merge. Stable TTL remains one hour.

### 6. Enhanced analysis — Lab accepted; Stable 101 promotion approved
- [x] Reuse normalized metadata and analysis results across recommendation selection and diagnostics; preserve ranking, exclusions and diagnostic output. Automated regressions passed.

**Phone acceptance — 2026-11-28, Analysis Reuse Lab build 100 r46134282761 (0083e8cd).** Two blocks, 14/14 selected and committed, no missing pairs or unrecorded events, 44 network requests and six gross avoided requests. Average/max selection wall time 15/28 ms; detailed diagnostics 25/45 ms (previous 43/58 and 153/192 ms). Detailed reports: four exact A+B, one shared artist, nine B-only; exclusions, first/second-page overlaps, cached pages and diagnostic ranks intact. Title+artist matching covered by automated tests but not observed in this phone sample. Different inputs and cache/page counts limit comparison; no isolated CPU, battery or network latency claims.

### 7. External song links
- [ ] Treat repeated identical links as separate events and consume intents correctly.
- [ ] Reuse Start Radio's stale-response protection: newer playback actions must win over earlier requests.
- [ ] Investigate existing metadata methods as fallback for missing videoDetails; verify exact video ID, never substitute a different recording. Preserve working simpmusic.org/app/watch links and Android app separation. Test same-link retries, cold/warm starts, rapid A/B links, switching playlists and unavailable IDs.

### 8. Playback errors
- [ ] Distinguish connectivity, actual timeout, sign-in required, private/unavailable recording, metadata failure and unknown playback errors; do not invent causes. Keep technical details in App Log.
- [ ] Investigate unexpected loss of playback notification and process restart observed during 60-minute Cache Memory phone test. User thinks music had been playing; no crash/OS termination evidence yet.
- [ ] Investigate legitimate support for authenticated personal uploads (not yet confirmed). Stable 95 without Enhanced failed on a recording that official YouTube Music played while anonymous YouTube reported private; App Log showed LOGIN_REQUIRED and No URL found. Do not change extraction or authorization until the supported mechanism is established.

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
| 97 | Event-driven performance and missing-recommendation diagnostics, build identity in updates and reports; Lab accepted; Stable publication verified |
| 98 | Batched statistics and persistent pair counts; Lab phone acceptance; Stable publication verified |
| 99 | Compact session storage; legacy migration and compact reopen accepted on phone; Stable publication verified |
| 100 | Expired cache cleanup; two phone expiry tests accepted with temporary 10-minute TTL; Stable 60-minute TTL; publication verified |
| 101 | Faster recommendation analysis and diagnostics; 14 phone recommendations and detailed reports accepted; publication pending |

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

Publish and verify Stable 101. Next independent Lab: repeated external song links and stale-response protection, then separate metadata fallback if needed, playback errors, progressive refill and final measurements. Leave cache unchanged unless a demonstrated regression requires it.
