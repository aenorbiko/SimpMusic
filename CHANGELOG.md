# SimpMusic Enhanced — What's New

SimpMusic Enhanced is a community fork of [the original SimpMusic](https://github.com/maxrave-dev/SimpMusic). It keeps the original music player and adds an optional **Enhanced 2+1** listening mode, improvements to the player and notifications, and other usability changes.

**This is the changelog for the Enhanced fork**, not the upstream SimpMusic release history. Changes below are listed by *our Android build number and build label*, not only by upstream's `2.5.0` version name. Each new stable build should get a new entry here.

**Latest stable APK:** [Download SimpMusic Enhanced](https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-latest/SimpMusic-Enhanced-arm64.apk)  
**Source:** [aenorbiko/SimpMusic](https://github.com/aenorbiko/SimpMusic)  
**Experimental builds:** [GitHub Releases](https://github.com/aenorbiko/SimpMusic/releases) (not all experiments are included in Stable)

## How is Enhanced different from the original SimpMusic?

- **Enhanced 2+1 shuffle:** play two songs from your own selected collection, followed by one YouTube Music radio recommendation. It uses radio suggestions for both source songs when deciding what to play.
- **Works with collections:** Liked Music, YouTube playlists, albums, local playlists and supported library lists.
- **One three-state shuffle control:** Off → Shuffle → Enhanced. The main player, playlist screen and Android notification stay in sync.
- **Recommendations are identifiable:** a small star next to recommended songs in the queue and notification.
- **One Like button:** the player and notification use the YouTube Music like state rather than a separate local favourite toggle.
- **Queue navigation improvements:** normal scrolling does not accidentally swipe songs away; the current track and playback position are kept when changing shuffle modes within the same collection.
- **Finite, replenishing 2+1 queue:** more pairs are added as you approach the end of a loaded block, without repeating previously used recommendations. The source playlist does not silently become an endless unrelated radio.
- **Clear build identity:** app name `SimpMusic Enhanced`, plus channel and short Git commit ID in queue screenshots.

The recommendation engine is a practical approximation based on YouTube Music's per-song radio results, **not** an official YouTube Music API that accepts two songs together. It requires connectivity and available radio results; occasionally a pair may have no usable recommendation.

## Stable releases

### Android build 98 — Batched statistics (2026-11-28)

**What's new**

- Save Enhanced statistics less often.
- Keep missing recommendation counts across restarts.

**Technical details**

- Load the persistent aggregate once, flush after eight accepted events, on the next event after five seconds, at block completion/cancellation or when opening statistics. No periodic timer or extra requests.
- Keep pending aggregates on storage failure; retain existing history, reset and clock handling. A hard process stop can lose unsaved events.
- Signed Lab r46115748331 (bccd3652) passed CI and phone tests: 27 requests / 6 saves and 25 requests / 5 saves, zero pending or failed events at report. Compared with the earlier 28-save block, 79–82% fewer logical statistics saves. Force-stop/reopen preserved 25 requests, 7 pairs and one missing reason.
- Queue selection and session format unchanged.

### Android build 97 — Performance diagnostics (2026-11-28)

**What's new**

- Show Enhanced performance and missing recommendation reasons.
- Show build numbers in updates and diagnostic reports.

**Technical details**

- Event-driven, process-local counters for selection and diagnostic wall time, managed heap samples, report-time process PSS, whole-process CPU time, logical statistics/session writes and cache residency. No extra network requests, periodic timers or diagnostic disk writes.
- Bounded local history of the last ten missing pairs. Selection, scheduling and cache policy unchanged.
- Lab build 96 r46112717816 (2ed58513) passed CI and phone tests: 14 pairs, 13 recommendations committed, one missing due to radio B failure. Baseline reports and limitations recorded in backlog. Battery consumption and exact cache-only heap not measured; new diagnostic counters reset on process death.
- Add installed/offered build identity to updater and build identity to copied diagnostics; update dialog will be verified on the next real update.
- User approved promotion and prioritized write/memory optimizations before external-link work. No new playback features in this release.

### Android build 96 — Radio cache and updates (2026-11-28)

**What's new**

- Faster Enhanced recommendations with a one-hour radio cache.
- SimpMusic Lab can now update from within the app.
- Shorter update notes.

**Technical details**

- Keep up to 64 anonymous per-song radio snapshots in process memory for a fixed 60 minutes. Store all returned first-page metadata immediately and cache the optional matching continuation on success. No audio is stored; invalid deferred continuations trigger bounded refresh without mixing snapshots.
- Report cache hits, deferred continuation outcomes, recovery requests and evictions separately from network request statistics. Preserve selection, exclusions and progressive delivery.
- Phone acceptance on Lab 7bb3468f confirmed first-page and continuation reuse, two successful deferred continuation requests, six avoided requests in the targeted test, and a 57 ms first cached pair decision. The one-hour configuration passed signed Lab CI run 46099381052; extended one-hour phone testing was not required by the user.
- Independent Lab feed and compact update dialog: phone update preserved authorization, settings, queue, current track and pause state. Stable keeps its existing package, signing and release channel.
- Existing playback failures for a private/unavailable YouTube recording were reproduced in Stable 95 without Enhanced and are tracked separately; no extractor changes in this release.
- Bundle approved PRs #27, #28 and #29 in one Stable build; publication and Stable phone acceptance are separate checkpoints.

### Android build 95 — Distinct Stable and Lab icons (2026-11-28)

**What's new**

- New Enhanced icon with blue stars.
- Purple LAB badge on the test app icon.

**Technical details**

- Reuse the original launcher artwork with adaptive vector overlays, including round and monochrome variants. Select icons by the existing build channel and shared Lab property.
- The revised Launcher Icons Lab passed automated checks and was visually accepted on the phone; Stable appearance will be verified after installation.
- Refresh the feature backlog, including the verified Repeat One behavior, without a separate documentation-only APK build.

### Android build 94 — Radio button on the player (2026-11-28)

**What's new**

- Start radio directly from the Classic player using the new Radio button beside the playlist and queue controls.
- Song comments are available in the three-dot menu, leaving the main controls less cluttered.
- Future development builds update the same SimpMusic Lab app, keeping its settings and account separate from Stable.

**Technical details**

- The Radio button calls the existing protected current-song radio handler from build 93. Other player styles and recommendation algorithms are unchanged.
- Reuse the installed Start Radio Lab package and permanent signing key for future feature Labs; retain branch-specific build labels and APK release destinations.
- The final signed Player Radio Lab passed automated tests and all six phone checks, including updating the existing Lab without losing its data.

### Android build 93 — Start Radio without interrupting playback (2026-11-27)

**What's new**

- Start Radio for the currently playing song keeps its playback position and replaces only the upcoming songs.
- If the song is paused, starting its radio keeps it paused.
- If you switch songs before radio finishes loading, the outdated response will not interrupt your new song.
- Starting radio for another song still plays that song. Switching from Enhanced to ordinary radio ends the Enhanced session.

**Technical details**

- Route the current-song menu action through the shared in-place queue mutation used by Enhanced; retain the active track and history.
- Guard radio requests with shared playback and request revisions, including the final check after metadata loading for a selected song. Android and JVM handlers use the same queue planning and request validity helpers.
- Regression tests and signed Start Radio Lab build passed. All nine phone checks passed across the two Lab revisions, including the stale-response check on the final revision.

### Android build 92 — Clearer updates and shared build infrastructure (2026-11-27)

**What's new**

- Update messages now explain what has changed in plain language.
- The full version history includes easy-to-read summaries of earlier updates.
- Internal build improvements make future updates easier to prepare and verify. Music playback and Enhanced recommendations are unchanged.

**Technical details**

- Generate Stable release notes from the matching changelog entry, retaining the Android build and source commit metadata required by the updater and version gate.
- Share signed ARM64 assembly, caching, package verification and regression tests between Stable and new Labs; keep release destinations isolated. The signed validation run for PR #23 succeeded without publishing an APK.
- No changes to Android playback or recommendation logic. Start Radio remains a separate planned fix.

### Android build 91 — Shared queue management (2026-11-27)

**What's new**

- Updated the way the app manages upcoming songs, laying the groundwork for future playback improvements.
- Enhanced, Shuffle and playback controls continue to work as before.

**Technical details**

- Extract the existing in-place Shuffle/Enhanced queue-tail mutation into a common MediaPlayerHandler extension. Reject mismatched active-track or queue snapshots before removal; preserve played history, current playback, recommendation badges and the existing append sequence.
- Add JVM behavior tests for ordered removal, queue metadata and continuation, recommendation badge ordering, stale snapshots and empty tails. Signed ARM64 Queue Management Lab 90 (`b2ad6ae6`) passed the existing regression suite and new tests.
- Phone testing confirmed uninterrupted playback, pause and position preservation, notification Enhanced activation, queue replenishment and restoration with further replenishment after Force stop. One missing recommendation was observed; unchanged code can omit a recommendation when no eligible candidate is found, but the specific cause was not established.
- No changes to Start Radio, recommendation selection, statistics or the existing asynchronous player operation semantics. Atomic queue replacement and cross-feature cancellation are not part of this refactor.

### Android build 90 — Complete playlist sources and statistics clock fix (2026-11-27)

**What's new**

- Shuffle and Enhanced now use the complete playlist, including songs beyond the first 100, when started from the player or notification.
- Fixed a problem that could reset Enhanced radio statistics when requests finished out of order.

**Technical details**

- Load remaining remote playlist pages before enabling shuffle/Enhanced from the player or notification, preserving the current track and rejecting incomplete or stale source loads. Phone tests in signed Lab 89 exceeded the previous 100 first-page request limit (182 from player, 168 from notification).
- Distinguish delayed asynchronous radio completion writes from actual wall-clock rollback. Keep observation start and aggregate counters, retain recent history on late writes, and report the start timestamp in UTC. A genuine clock rollback clears only the repeat history and adjusts elapsed observation by the observed clock change. Late event timestamps are clamped to the history watermark; repeat windows remain estimates. Old lost history cannot be reconstructed.
- No change to recommendation selection, Full Track, radio concurrency or cache. Stable publication is gated by the existing JVM regressions plus complete-source and clock regression tests. Ordinary listening can verify the new statistics.

### Android build 87 — Full Track recommendations (2026-11-26)

**What's new**

- Enhanced can look for a longer version when it recommends a song shorter than 90 seconds.
- If no suitable longer version is found, the original recommendation is kept.

**Technical details**

- Only selected Enhanced recommendations shorter than 90 seconds receive an additional Songs search. A different eligible audio result must have the same normalized title and complete artist set and be at least 25% longer; otherwise the original is retained. Search timeout is 10 seconds including slot wait.
- Local diagnostics show result and search duration; aggregate Full Track counters persist with radio statistics. Normal playback and anonymous paired radios are unchanged. No radio cache.
- Signed Lab 87 (`d7bca8dc`) passed automated tests. Two phone lookups correctly preserved short complete tracks (~2.5 seconds each); a real successful replacement has not yet been observed. Approved for merge by the user. The existing incomplete playlist source bug and clock reset issue remain separate fixes.

### Android build 85 — Enhanced duplicate protection and reliable queue refill (2026-11-26)

**What's new**

- Reduced repeated recommendations, including different uploads of the same song.
- Fixed overlapping queue refills when skipping through songs quickly.
- Added a reset option for Enhanced radio statistics in Settings.

**Technical details**

- Exclude recommendations with the same video ID or strict normalized title and complete artist set as source tracks or earlier recommendations, including earlier batches and restored sessions. Distinct remixes and versions remain eligible; incomplete or differing metadata can prevent matching.
- Serialize Enhanced batch generation through queue append and session checkpoint so rapid near-end track selection cannot generate overlapping batches. Retain progressive 2+1 ordering and existing four-pair/eight-request radio concurrency.
- Reset local Enhanced radio statistics in Settings → About us → Enhanced statistics, with confirmation.
- Development APKs now build only ARM64; standard Gradle dependency and task-output caching enabled, with a cache-free manual verification option. Existing permanent signing, APK ABI validation and regression tests retained. No radio request cache added.
- Based on successful signed Lab build 84 (`bcfdcc2f`), tested on the phone: repeated near-end taps added one seven-recommendation block at a time; stars survived restart. Three logged blocks selected 21 distinct titles with no request failures. Strict alternate-ID metadata exclusion is covered by automated tests, not independently established by the phone log.

### Android build 78 — Local Enhanced radio request statistics (2026-11-25)

**What's new**

- Added Enhanced radio statistics in Settings > About us, including request counts, errors and repeated requests.
- Statistics are stored locally and survive app restarts.

**Technical details**

- Settings → About us → Enhanced statistics → Copy statistics: report completed first-page radio requests, continuation requests, errors/empty results and cumulative repeats within 20 minutes, 1 hour and 24 hours.
- Local bounded history of recent successful seed IDs and aggregate counters; no titles, IDs, account data or continuation tokens in copied reports. No telemetry or radio cache. Repeat counts estimate potential savings, not actual cache hits.
- Statistics survive process restart and Force stop, verified on the phone with Radio Statistics Lab build 77. Existing anonymous four-tier selection, queue ordering, playback and signing are unchanged. Abrupt process termination can lose outstanding asynchronous writes.

### Android build 76 — Anonymous radio and four-tier Enhanced recommendations (2026-11-25)

**What's new**

- Enhanced now requests radio suggestions anonymously without changing your signed-in account or likes.
- Improved recommendations by comparing song titles and artists as well as exact matches.
- Recommendation stars are restored when playback resumes via Bluetooth.

**Technical details**

- Anonymous YouTube Music radio requests for the two Enhanced seeds, with signed-in account operations unchanged.
- Four-tier selection: exact videoId, normalized title and artist, shared artist with a different song, then the first eligible track from radio B. Random shuffle and 2+1 ordering are preserved.
- Fetch a second page only when no eligible exact overlap exists on page one; compare up to 100 returned tracks per radio. Four nearest pairs and eight concurrent radio requests, with ordered progressive delivery.
- Restore recommendation stars before Bluetooth Media3 recovery. Keep session-only blue-star Copy/Copy all diagnostics for field testing. No radio cache or persistent repeat-request statistics yet.
- Based on phone-tested Anonymous Radio Lab build 75. Shared artist credits may sometimes include soundtrack/project names.

### Android build 68 — Restore Enhanced after process death and Bluetooth resume (2026-11-21)

**What's new**

- Enhanced remembers its queue and recommendation stars after the app closes or the phone restarts.
- Music can resume from a Bluetooth play command without opening the app.

**Technical details**

- **Enhanced 2+1 resumes after Android process eviction or device reboot**, including the original source queue, recommendation markers and later queue prefetch.
- **Bluetooth media resumption can start playback without opening SimpMusic**, as verified by phone testing. The resumed song may restart from the beginning.
- Preserves original shuffle/Enhanced behavior, playback controls, account, playlists and update channel; recommendation relevance is unchanged and remains a separate experiment.
- Supersedes the accidental same-version Stable build 67 publication after PR #8. Build 68 allows the existing in-app updater to detect the new APK.

### Android build 67 — Clear Enhanced and original SimpMusic credits (2026-11-17)

**What's new**

- The About page now identifies the Enhanced version and its build number, with links to its history and the original developer.

**Technical details**

- **About/Credits now clearly identifies SimpMusic Enhanced** with its own Android build number, build channel and short Git revision, separately from the original SimpMusic v2.5.0.
- **Original developer: maxrave-dev** remains credited with a working link; original project description, links, licence and legal disclaimer are preserved under **About the original SimpMusic**.
- Added links from About to our [Enhanced changelog](https://github.com/aenorbiko/SimpMusic/blob/main/CHANGELOG.md) and [fork source code](https://github.com/aenorbiko/SimpMusic).
- Settings → About us → Version displays `SimpMusic Enhanced · build 67 · Enhanced Stable`; upstream/unlabelled builds keep their original About identity. An Update Lab-only test button is hidden from unrelated Labs.
- No changes to playback, Enhanced recommendation selection or queue loading.
- About Lab `ec2afcd1` was reviewed from user-provided Android screenshots; the corrected About Lab `c467c2c0` passed CI. The user explicitly approved this layout. Stable build 67 also enables testing the **66 → 67** update prompt from our GitHub release channel.


### Android build 66 — Enhanced-only update checks (2026-11-17)

**What's new**

- Stable now checks for updates to SimpMusic Enhanced itself rather than the original app.
- Experimental versions remain separate and do not offer Stable updates.

**Technical details**

- **Fork-aware updates:** Stable SimpMusic Enhanced checks only signed APK releases in [our own GitHub repository](https://github.com/aenorbiko/SimpMusic/releases), never the original SimpMusic GitHub/F-Droid update feeds.
- **Correct version comparison:** compare Android build numbers (66, 67, ...) instead of treating the original project's version `2.5.0` as a newer or older Enhanced release.
- **Safer download prompts:** only accept an existing, expected ARM64 APK from our `enhanced-latest` release; the update button opens that APK, and Android verifies its package ID and signing certificate during installation. Nothing is installed silently.
- **Separate Labs:** experimental applications have different Android package IDs and do not offer the stable Enhanced APK as an update.
- **Settings:** the update source is fixed to SimpMusic Enhanced GitHub Releases while automatic update checks can still be turned off. The original SimpMusic project and other developer builds retain their original update settings.
- Tested in a separate Update Lab (`3d55c513`) on 17 November: it correctly reported Stable build 65 without offering an incompatible APK. The in-place Stable 65 → 66 upgrade and future 66 → 67 prompt still require observation on an actual phone.

### Android build 65 — Changelog and public release information (2026-11-16)

**What's new**

- Added a What's New link in Settings > About us so you can follow the development of Enhanced.

**Technical details**

- **What's New** link added under **Settings → About us**, opening this maintained changelog.
- GitHub stable-release notes link directly here so friends who receive the APK can see how the fork differs from original SimpMusic.
- No change to the Enhanced recommendation algorithm in this release.

### Android build 64 — Faster Enhanced queue (2026-11-16)

**What's new**

- Enhanced starts adding recommendations sooner, without waiting for the whole first group of songs.
- More recommendations appear automatically in the correct order.

**Technical details**

- **Progressive 2+1 loading:** the first pair and its recommendation appear without waiting for the entire first batch of 14 source songs.
- Additional groups append in correct order as each pair's radio results become available.
- Preserves the current song without restarting playback, keeps the four-request concurrency limit, retains the previous recommendation choices, and cancels old work when switching modes or collections.
- **Phone testing:** on a Liked playlist with 300+ songs, the first group appeared in ~3 seconds instead of ~10 seconds previously. Rapid Next, switching off Enhanced, and near-end queue refill were checked successfully.
- First published Stable build identity: `Enhanced Stable · 87ff4de8`.

### Android build 63 — Stable Enhanced name and screenshot identity (2026-11-16)

**What's new**

- The app is now called SimpMusic Enhanced.
- The queue screen shows which build you are using.

**Technical details**

- Renamed the stable Android app label from **SimpMusic Dev** to **SimpMusic Enhanced**, without changing its Android app ID or signing key; existing installs can be upgraded.
- Added a visible **Enhanced Stable · <commit>** build stamp in the queue so screenshots identify exactly which build is being tested.
- Standardised the permanent signed ARM64 APK release.

### Earlier Enhanced features — Through 2026-11-15

- Added Enhanced 2+1 radio recommendations and consistent Off/Shuffle/Enhanced toggling.
- Extended Enhanced mode from Liked Music to playlists, albums and other supported collections.
- Redesigned the Enhanced shuffle icon using the original-size shuffle arrows with two small stars.
- Unified the YouTube Music Like control between the main player and Android notification; removed the duplicate local-favourite control.
- Marked recommendations with stars in the queue and notification.
- Fixed the notification controls' synchronization and kept Previous instead of replacing it with Repeat.
- Prevented accidental song dismissal when scrolling the queue.
- Kept current playback when changing modes, stopped automatically at the source collection's end and added queue refill near the end of a block.

## Experimental features — Not in the current Stable build

These builds are deliberately separate and **must not be described as shipped Stable features**:

- **Relevance Lab:** the original signed-in, top-15 experiment was superseded by the anonymous four-tier engine in build 76; that older experiment is not the Stable algorithm.
- **Radio Cache Lab:** temporary in-memory caching of radio metadata. Older experimental branch; not ported to current Stable.
- **Per-device Lyrics Offset:** earlier unfinished prototype; not yet in Stable.

For the current list of work and acceptance tests, see [Enhanced feature backlog](ENHANCED_FEATURE_BACKLOG.md).

## Original project and credits

SimpMusic Enhanced is built on [SimpMusic by maxrave-dev](https://github.com/maxrave-dev/SimpMusic). The underlying original app, external services and their respective licences remain credited to their authors. The changes described here are specific to this fork.

**Maintainer note:** whenever a new Stable APK is released, increment Android `versionCode` and add a dated entry above **before publishing**. Keep experiment-only changes out of the Stable section until they have actually been merged and built.
