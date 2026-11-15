# SimpMusic Enhanced — feature backlog

Last reviewed: 2026-11-15 (Australia/Brisbane).
This is the durable feature and bug tracker for our SimpMusic fork, not an indication that all listed features are implemented.

## Development and release rules

- Stable Enhanced integration branch: `feature/enhanced-shuffle`. Upstream/default `dev` is not the target for experimental features.
- **One behavior change per feature branch**, with its own Android Lab build when needed.
- Run automated tests, then **test the APK on the user's phone**; CI success alone is not acceptance.
- **Merge only after the user's explicit approval**, preserving old experimental branches for later work.
- Lab APKs must use a distinct Android application ID, label and GitHub Release. Stable debug APK keeps `com.maxrave.simpmusic.dev` and the existing permanent signing certificate; experimental Playlist Lab uses `com.maxrave.simpmusic.dev.playlists` only when `-PsimpmusicPlaylistsExperimental=true` is supplied.
- Do not overwrite stable APK with an experimental Lab, or silently update upstream/default `dev`.
- When merging, check the stable signing/app ID, successful CI and published stable APK.

## Completed and tested

### Enhanced Shuffle 2+1: Liked and all playlist types

- **Core 2+1 behavior:** two source songs, then up to one YouTube Music radio recommendation; star icons on generated rows; approved compact two-star Shuffle/Enhanced icon; synchronized main player and Android notification controls.
- **Liked Music support** already established on baseline `feature/enhanced-shuffle`.
- **All collections:** YouTube Music playlists, albums, SimpMusic local playlists and library song collections; Off → Shuffle → Enhanced on the corresponding sources.
- Recommendations seed from the actual selected collection, not unrelated Liked Songs.
- Switching modes in the same collection preserves current playback position and pause state. Opening a different collection starts that source; recommendations are distinct within a queue.
- Batch logic: up to 14 source tracks and 7 recommendations; prefetches remaining source songs when appropriate; never continues with Enhanced recommendations after exhausting the original source collection.
- Phone-tested November 14–15, 2026: Shpongle album, Russian album `Весёлая жизнь`, a regular playlist, locally created `Test` playlist, switching/re-enabling Enhanced, no unwanted duplicate rows, short albums and a 20-track album, finite queue and dynamic prefetch. **User explicitly approved merging `feature/enhanced-all-playlists` into `feature/enhanced-shuffle` on November 15, 2026. GitHub PR #1 was merged successfully at commit `42b19994ae1d847435b1a4e09204c2f8ac19094c`.**
- Previous fixes confirmed by user: accidental song swipe while scrolling queue; Like/Unlike actions via notification and main player; notification heart's transient flicker (user reports fixed, November 15, 2026).
- Integration into stable Enhanced confirmed via GitHub PR #1. The separate `feature/enhanced-all-playlists` branch remains available for reference; the new stable signed APK must still complete CI before it can be called published.

## Next: independent feature branches, in intended priority order

### 1. Reduce ~10-second Enhanced initial startup delay — NOT IMPLEMENTED

- On a 300+ Liked playlist the initial 14-source-song batch makes up to 14 radio metadata requests, capped at 4 concurrently. The whole batch appears after about 10 seconds, rather than appearing progressively.
- Proposed: prioritize the first song pair, add its recommendation promptly, and generate later 2+1 segments progressively in the background.
- Ensure no restart of the current song, no gaps or duplicates, rapid cancellation of old-source work, no stale results after switching; retain prefetch near end.
- Test phone behavior with large Liked and short albums, navigation and notification toggles, pause/Next, empty/offline radio.

### 2. Improve recommendation relevance — IMPLEMENTED ON A SEPARATE LAB BRANCH, PHONE TEST PENDING

- Branch: `feature/enhanced-radio-relevance`; **do not merge until the user tests and approves it**.
- User observed unexpected recommendations from the `Весёлая жизнь` album.
- Exact requested rule for each consecutive source pair A, B:
  1. Ask YouTube Music for separate radio lists for A and B.
  2. Compare only the **first 15 returned songs from each radio**; choose an unused song appearing in both, ranked by the combined positions, resolving ties in favour of B (the second, most recently played source song).
  3. If no common unused song exists, take the **first unused suggestion from B's own radio**, again within its first 15 songs. **No top-5 rule.**
  4. Never fall back to A's radio or borrow suggestions from other pairs; if B has no unused candidate, omit this pair's recommendation.
- A and B are source tracks from the chosen album or playlist; “second playlist” here means the *radio recommendation list for B*, not another user-created playlist.
- Exclude original collection songs and already selected recommendations by video ID. Do not change Enhanced's 14-track batch size, current-song preservation or finite queue behavior.
- Status: code and automated regression tests committed on `feature/enhanced-radio-relevance`; Android Lab APK/build and phone acceptance must be verified separately.
- Compare recommendation quality on albums from one artist and mixed-genre playlists; this is a selection heuristic, not an audio-similarity measurement. Limiting local candidates may not reduce network requests.
- **Phone observations, November 16, 2026 (user-supplied screenshots):** two independently generated Enhanced queues for album `Весёлая жизнь` preserve the visible 2-source + 1-starred-recommendation rhythm, with no obvious in-queue duplicates; both queues contain conspicuously unrelated-looking music, including `EE DOWNLOAD IN DESCRIPTION`, `CLUB - ROI INSTRUMENTAL`, `Charlie Puth - Attention` remixes and `Stay with me remix`. User satisfaction with relevance has **not** been established; do not merge solely on those screenshots.
- **Diagnosis required before more heuristics:** screenshots alone cannot verify whether a selected track was present in both top-15 lists or chosen as fallback from B's radio. Add optional non-sensitive logging or a debug report per pair: original A/B `videoId` and titles, returned list sizes, first 15 candidate IDs/titles/positions, chosen candidate ID, common-vs-B-fallback decision, and whether a candidate was excluded. Keep diagnostics opt-in and avoid logging tokens/account credentials. Check whether the YouTube Music radio endpoint itself offers off-topic recommendations; if so, a first-15 restriction or B fallback cannot guarantee genre similarity. Re-test on the album and evaluate improvement before user approves merge.
- **Build identity caveat:** queue screenshots showing footer `SimpMusic v2.5.0` do not distinguish Lab from stable: the fork still uses upstream version metadata. Verify the installed Android app's `SimpMusic Lab (Relevance)` identity when attributing this test to the new algorithm.


### 3. Fix Start Radio restarting the currently playing song — NOT IMPLEMENTED

- User confirmed this also occurs in original SimpMusic. In the pinned core Android handler, `toggleRadio()` does `clearMediaItems()`, re-adds current song, and calls `loadPlaylistOrAlbum(0)`.
- Replace only upcoming queue entries with radio suggestions, preserving current song, position and pause state. Test starting radio from a selected different song separately.
- This is a **different** feature from Enhanced, and may require patching the pinned `core` submodule or the build-time patch script.

### 4. Radio response cache — EXPERIMENTAL, NOT MERGED

- Branch `feature/enhanced-radio-cache`: in-memory cache for up to 64 **distinct radio response lists** (one per source song) for 20 minutes, *metadata only*, NOT MP3/audio files.
- When the same seed is requested again within the TTL, re-use response; after expiry it is re-fetched; least-useful/old entries are evicted when full. Clears on app process termination.
- Tests exist on that branch, but the user has not accepted phone testing. Cannot promise faster first run with an empty cache.
- Integrate only after independent testing and approval.

### 5. Device-specific Bluetooth Lyrics Offset — PROTOTYPE ONLY

- Different lyrics timing compensation for different Bluetooth devices (headphones, car, speaker) plus a global/default offset; handle disconnect and device switches.
- Prototype exists on `feature/enhanced-optimisation`, not merged. Validate Android permissions/device identity and backup portability before adopting.
- Note Metrolist 14.0.0 has a **global** lyrics offset, which is not the same as per-device profiles.

### 6. Backup/restore safety and settings portability — PROTOTYPE ONLY

- Restore sign-ins and preferences reliably, avoid credential leaks, preserve future per-device timing profiles appropriately, and test backup compatibility.
- User reported YouTube Music authorization survives current backup/restore. The prototype on `feature/enhanced-optimisation` needs separate review/test.

### 7. Clearly identify our build for sharing — PARTIAL

- Display custom build name/version, fork/commit identifier, link to our releases and upstream licenses/attribution.
- Keep stable, experimental Lab, and upstream SimpMusic clearly distinguishable without changing the installed app identity inadvertently.

### 8. Fork-aware in-app update checks — NOT IMPLEMENTED

**Goal: update only our signed SimpMusic Enhanced app on the phone, never replace it with upstream SimpMusic.**

- Current code still checks upstream SimpMusic GitHub/F-Droid via `UpdateRepository`. In `App.kt`, the update dialog opens `https://simpmusic.org/download`. This is wrong for our fork.
- Proposed: stable `SimpMusic Dev` (`com.maxrave.simpmusic.dev`) checks **only releases from `aenorbiko/SimpMusic` for the stable Enhanced channel**. Experimental Lab packages must have their own matching release channel or no update checks.
- Publish **immutable versioned release tags** and an explicit latest-stable pointer/manifest. Current moving `enhanced-latest` tag alone is not a proper comparable version number.
- Show two version identities: original SimpMusic base version plus our Enhanced revision/build (for example `2.6.0-enhanced.3`). The Android `versionCode` must increase for each in-place upgrade. Keep application ID and permanent signing certificate unchanged.
- Offer an APK only if its channel, Android package and expected signing identity match the installed app. Use standard Android installation with user confirmation; do not silently install updates.
- Show our release notes and links, allow opt-out, work offline without breaking playback, and handle missing/stale/prerelease metadata.
- Test updating the stable app **over the installed stable app** and preserving login, settings, playback and backup/restore. Test Lab packages separately.
- Develop in a separate branch with an isolated Lab APK. Merge only after phone testing and explicit approval.
- This is distinct from pulling **source code updates** from the upstream project, tracked below.

### 9. Maintainable code and repeatable tests — PLANNED

- Extract independent components for shuffle mode, radio recommendation selection, queue generation/prefetch, player-state handling, settings and cache.
- Refactor incrementally with unit and Android integration tests. Goal: **make future features easier and safer to add**, without changing behavior in a single massive rewrite.
- Trim unnecessary debug ABIs to reduce build time and artifact size if doing so does not break user devices.

### 10. Repeat One / manual Next behavior — VERIFY, NOT AN ACTIVE FIX YET

- Verify that tapping Next while Repeat One is enabled advances to the next track while natural end-of-track repeats the current one; check previous and repeat-mode changes.
- Original SimpMusic v2.5.0 lists improvements here; determine whether any issue remains in our fork before implementing code changes.

### 11. Incorporate new official SimpMusic releases without losing Enhanced — PROCESS PLANNED

**Goal: adopt selected official bug fixes and features, but keep our own tested Enhanced behavior intact.**

- Official upstream: `maxrave-dev/SimpMusic` (default `dev`, versioned releases). Our repository's own `dev` is a **snapshot**, not an automatically synchronized upstream mirror. Stable **our app** code lives in `feature/enhanced-shuffle`.
- The project pins a second upstream repo via Git submodule `core` (`maxrave-dev/core`) and modifies its behavior with `scripts/apply_enhanced_core_patch.py`. Every upstream sync must check **both** the main source and the pinned `core` revision.
- For each official release:
  1. Examine the upstream release notes and actual changed code (including `core`); note security and breaking changes.
  2. Branch from the latest tested stable Enhanced into `integration/upstream-vX.Y.Z`. Bring in upstream **release-tag source** and resolve conflicts deliberately, especially shuffle, queue, likes, notification, player, data schema and build files. Preserve our code and tests.
  3. Build and run regressions; create a **separate signed integration Lab** with a different app ID.
  4. Phone-test login, playlists (Liked/YouTube/local/albums), 2+1, queue prefetch, icon and notification, backups, lyrics, Radio and playback. Do not automatically merge upstream releases.
  5. After explicit user approval, merge the integration changes into the stable Enhanced branch, increment Android `versionCode`, keep its existing package/signing certificate and publish a new immutable **our** release. Update the latest-stable pointer for our in-app updater.
- Automating **upstream-release monitoring/diff/checklist creation** is welcome; automatically replacing working Enhanced or merging untested upstream code is not.
- Official release `v2.6.0` was published 2026-11-14; our copied base has much of that implementation already, but still has `version-name = 2.5.0` / `version-code = 62`. Compare actual source + `core` SHA before synchronizing version metadata. A newer upstream version does **not** mean a newer signed Enhanced build is available.
- Keep this separate from one-feature-per-branch development and fork-aware **in-app update checks** (item 8).

## Verified resolved / not to re-open without a regression

- Notification Like-heart flicker: user says it is already fixed (2026-11-15).
- Main-player/notification like action and scroll-induced accidental swipe: previously tested.
- Avoid generating duplicate recommendations *within one queue*. The same recommendation may legitimately appear when Enhanced is regenerated in a different queue.

## Branch map and merge status (2026-11-15)

- `dev`: **GitHub default branch**, original/upstream SimpMusic source. **No Enhanced feature merges here.**
- `feature/enhanced-shuffle`: **our stable Enhanced integration branch** (Liked 2+1, icons, main-player/notification sync, Like fixes, all playlists and albums); user-tested; stable Android APK published via `enhanced-latest`.
- `feature/enhanced-all-playlists`: branched from older Enhanced baseline; user-tested on phone, **merged by PR #1 into `feature/enhanced-shuffle`**. Branch retained for history.
- `feature/enhanced-radio-cache`: branched from older Enhanced baseline; 64 radio response lists, TTL 20 min; automatic tests only, no approved phone test; **unmerged**.
- `feature/enhanced-optimisation`: branched from older Enhanced baseline; multi-feature prototypes (including per-device Bluetooth lyrics offset and backup work); **unmerged, not fully phone-tested**. We must extract each feature into its own clean branch rather than merge everything.
- `feature/enhanced-radio-relevance`: branched from the current stable Enhanced baseline; top-15 match or second-song-radio fallback; **code and tests in progress / not user-accepted; unmerged**.
- Development workflow: copy/branch from stable → implement one feature → build a separately installed Lab APK → phone test → explicit approval → merge into stable. Leave `dev` alone until an explicit future decision to reorganize branches.

## Current delivery checkpoint

- **MERGED:** PR #1 merged into `feature/enhanced-shuffle` on 2026-11-15 (merge commit `42b19994`). Stable app label remains `SimpMusic Dev`; the Lab-only name is set in the Lab workflow. Stable workflow runs all-playlist source and JVM regression checks. **CI/release status must be verified separately; do not claim the newly merged APK has been published before the stable workflow succeeds.**
- Keep future improvements isolated in their own branches. Do not combine startup-speed, relevance, radio-preserve, cache and lyrics-profile work in the merge.
