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

### Android build 78 — Local Enhanced radio request statistics (2026-11-25)

- Settings → About us → Enhanced statistics → Copy statistics: report completed first-page radio requests, continuation requests, errors/empty results and cumulative repeats within 20 minutes, 1 hour and 24 hours.
- Local bounded history of recent successful seed IDs and aggregate counters; no titles, IDs, account data or continuation tokens in copied reports. No telemetry or radio cache. Repeat counts estimate potential savings, not actual cache hits.
- Statistics survive process restart and Force stop, verified on the phone with Radio Statistics Lab build 77. Existing anonymous four-tier selection, queue ordering, playback and signing are unchanged. Abrupt process termination can lose outstanding asynchronous writes.

### Android build 76 — Anonymous radio and four-tier Enhanced recommendations (2026-11-25)

- Anonymous YouTube Music radio requests for the two Enhanced seeds, with signed-in account operations unchanged.
- Four-tier selection: exact videoId, normalized title and artist, shared artist with a different song, then the first eligible track from radio B. Random shuffle and 2+1 ordering are preserved.
- Fetch a second page only when no eligible exact overlap exists on page one; compare up to 100 returned tracks per radio. Four nearest pairs and eight concurrent radio requests, with ordered progressive delivery.
- Restore recommendation stars before Bluetooth Media3 recovery. Keep session-only blue-star Copy/Copy all diagnostics for field testing. No radio cache or persistent repeat-request statistics yet.
- Based on phone-tested Anonymous Radio Lab build 75. Shared artist credits may sometimes include soundtrack/project names.

### Android build 68 — Restore Enhanced after process death and Bluetooth resume (2026-11-21)

- **Enhanced 2+1 resumes after Android process eviction or device reboot**, including the original source queue, recommendation markers and later queue prefetch.
- **Bluetooth media resumption can start playback without opening SimpMusic**, as verified by phone testing. The resumed song may restart from the beginning.
- Preserves original shuffle/Enhanced behavior, playback controls, account, playlists and update channel; recommendation relevance is unchanged and remains a separate experiment.
- Supersedes the accidental same-version Stable build 67 publication after PR #8. Build 68 allows the existing in-app updater to detect the new APK.

### Android build 67 — Clear Enhanced and original SimpMusic credits (2026-11-17)

- **About/Credits now clearly identifies SimpMusic Enhanced** with its own Android build number, build channel and short Git revision, separately from the original SimpMusic v2.5.0.
- **Original developer: maxrave-dev** remains credited with a working link; original project description, links, licence and legal disclaimer are preserved under **About the original SimpMusic**.
- Added links from About to our [Enhanced changelog](https://github.com/aenorbiko/SimpMusic/blob/main/CHANGELOG.md) and [fork source code](https://github.com/aenorbiko/SimpMusic).
- Settings → About us → Version displays `SimpMusic Enhanced · build 67 · Enhanced Stable`; upstream/unlabelled builds keep their original About identity. An Update Lab-only test button is hidden from unrelated Labs.
- No changes to playback, Enhanced recommendation selection or queue loading.
- About Lab `ec2afcd1` was reviewed from user-provided Android screenshots; the corrected About Lab `c467c2c0` passed CI. The user explicitly approved this layout. Stable build 67 also enables testing the **66 → 67** update prompt from our GitHub release channel.


### Android build 66 — Enhanced-only update checks (2026-11-17)

- **Fork-aware updates:** Stable SimpMusic Enhanced checks only signed APK releases in [our own GitHub repository](https://github.com/aenorbiko/SimpMusic/releases), never the original SimpMusic GitHub/F-Droid update feeds.
- **Correct version comparison:** compare Android build numbers (66, 67, ...) instead of treating the original project's version `2.5.0` as a newer or older Enhanced release.
- **Safer download prompts:** only accept an existing, expected ARM64 APK from our `enhanced-latest` release; the update button opens that APK, and Android verifies its package ID and signing certificate during installation. Nothing is installed silently.
- **Separate Labs:** experimental applications have different Android package IDs and do not offer the stable Enhanced APK as an update.
- **Settings:** the update source is fixed to SimpMusic Enhanced GitHub Releases while automatic update checks can still be turned off. The original SimpMusic project and other developer builds retain their original update settings.
- Tested in a separate Update Lab (`3d55c513`) on 17 November: it correctly reported Stable build 65 without offering an incompatible APK. The in-place Stable 65 → 66 upgrade and future 66 → 67 prompt still require observation on an actual phone.

### Android build 65 — Changelog and public release information (2026-11-16)

- **What's New** link added under **Settings → About us**, opening this maintained changelog.
- GitHub stable-release notes link directly here so friends who receive the APK can see how the fork differs from original SimpMusic.
- No change to the Enhanced recommendation algorithm in this release.

### Android build 64 — Faster Enhanced queue (2026-11-16)

- **Progressive 2+1 loading:** the first pair and its recommendation appear without waiting for the entire first batch of 14 source songs.
- Additional groups append in correct order as each pair's radio results become available.
- Preserves the current song without restarting playback, keeps the four-request concurrency limit, retains the previous recommendation choices, and cancels old work when switching modes or collections.
- **Phone testing:** on a Liked playlist with 300+ songs, the first group appeared in ~3 seconds instead of ~10 seconds previously. Rapid Next, switching off Enhanced, and near-end queue refill were checked successfully.
- First published Stable build identity: `Enhanced Stable · 87ff4de8`.

### Android build 63 — Stable Enhanced name and screenshot identity (2026-11-16)

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
