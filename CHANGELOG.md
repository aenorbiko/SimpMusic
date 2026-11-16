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

- **Relevance Lab:** compare only the first 15 songs from each radio, and if no common candidate exists, use the second song's radio only. This experiment was tested but not approved for merging due to questionable suggestions on some albums.
- **Radio Cache Lab:** temporary in-memory caching of radio metadata. Older experimental branch; not ported to current Stable.
- **Per-device Lyrics Offset:** earlier unfinished prototype; not yet in Stable.

For the current list of work and acceptance tests, see [Enhanced feature backlog](ENHANCED_FEATURE_BACKLOG.md).

## Original project and credits

SimpMusic Enhanced is built on [SimpMusic by maxrave-dev](https://github.com/maxrave-dev/SimpMusic). The underlying original app, external services and their respective licences remain credited to their authors. The changes described here are specific to this fork.

**Maintainer note:** whenever a new Stable APK is released, increment Android `versionCode` and add a dated entry above **before publishing**. Keep experiment-only changes out of the Stable section until they have actually been merged and built.
