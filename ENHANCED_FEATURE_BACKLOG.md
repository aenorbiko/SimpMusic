# SimpMusic Enhanced — feature backlog

Last reviewed: 2026-11-21 (Australia/Brisbane).
This is the durable feature and bug tracker for our SimpMusic fork, not an indication that all listed features are implemented.

## Development and release rules

- Stable Enhanced integration branch: **`main`** (formerly `feature/enhanced-shuffle`). `dev` remains the unchanged original/upstream snapshot, and is currently still the GitHub repository's default branch until switched in GitHub Settings.
- **One behavior change per feature branch**, with its own Android Lab build when needed.
- **Every change merged or pushed to `main` MUST increase `gradle/libs.versions.toml` `version-code`** beyond the last published Stable Android build, even when it is only a maintenance or documentation change. Update `CHANGELOG.md` accordingly and verify the newly published signed APK. Do not reuse a Stable `versionCode` for a different Git commit; the updater compares Android build numbers, not Git SHAs.
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

### 1. Reduce ~10-second Enhanced initial startup delay — COMPLETED, USER-TESTED, MERGED TO `main`

- **Merged PR #3 on 2026-11-16:** `feature/enhanced-progressive-queue` → `main`, merge commit `87ff4de80541df2bd5869f6bb2bb45fee2f595dd`. **User explicitly approved** this merge after tests. GitHub stable CI/release must be verified independently.
- Before: waiting for all radio requests for 14 source tracks delayed the entire initial batch (~10 seconds on a 300+ song Liked playlist).
- After: request radio metadata concurrently (4 at once; timeout unchanged), but append each **ordered 2+1 group** when its pair is ready. The first group was visible on the user's phone after approximately **3 seconds**. Later groups appeared progressively.
- Android phone checks passed on **Speed Lab `479996db`** (2026-11-16): time to first visible group ~3s rather than ~10s; correct 2+1 ordering; rapid Next smooth; disabling Enhanced leaves ordinary shuffled source tracks; new segments automatically append near end. No user report of problems in these checks.
- Existing recommended-song selection algorithm, finite playlist boundaries, unique recommendation exclusion, current playback preservation, session cancellation and Media3 notification badge handling were preserved in code. This does **not** merge the unapproved Radio Relevance proposal; phone testing on short/other playlist types and rapid source switching while radio requests are pending was not separately documented as passed.
- Four additional coroutine regression tests cover first group before later lookups finish, original recommendation fallback and uniqueness, cancelled session not appending late items, and odd final source track. The signed **Speed Lab APK** built and automated tests passed (run `43792531145`).
- Stable `main` Android `versionCode` increased from 63 to **64** for an in-place signed update. APK retains stable package `com.maxrave.simpmusic.dev` and stable label `SimpMusic Enhanced`. Separate `SimpMusic Lab (Speed)` (`.dev.speed`) remains only for history/testing.
- No further speed work is required for this feature unless regressions or additional requirements are reported. Keep radio cache, radio relevance, Lyrics Offset and upstream updates as independent experiments.

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
- **USER PRIORITY UPDATE (2026-11-16): Detailed radio-selection diagnostics are LOW PRIORITY / DEFERRED. Do not implement them now.** The user confirmed screenshots were captured using `SimpMusic Lab (Relevance)`; no further confirmation required.
- **Build identity caveat:** queue screenshots showing footer `SimpMusic v2.5.0` do not distinguish Lab from stable: the fork still uses upstream version metadata. Verify the installed Android app's `SimpMusic Lab (Relevance)` identity when attributing this test to the new algorithm.


### 2A. Radio Relevance V2 + updater dialog cleanup — ISOLATED LAB, NOT MERGED

- **2026-11-25 Bluetooth-star fix added by user request:** port the tested-by-compilation (not yet phone-validated) `feature/enhanced-restored-notification-star` restoration patch into Relevance V2. Before Media3 restores the queued item from disk, recover recommended IDs from validated saved Enhanced snapshot, preserving queue, position, like, and source behavior. Run regression tests; the original Star Restore Lab failed only because pinned `core` has pre-existing, unrelated nullable `SyncSupportTest.kt` compilation errors. CI temporarily excludes this *test source only* to execute focused badge tests; do not modify upstream production code for this workaround. PR #11 remains separate and unmerged.

- **2026-11-17 (rebased 2026-11-21):** user explicitly requested revisiting Radio Relevance and combining the small literal `\\n\\n` formatting fix in the **same feature branch**. New `feature/enhanced-radio-relevance-v2` was originally based on Stable build 67; it is now based on **main / Android build 68** including tested Enhanced headless restoration; old `feature/enhanced-radio-relevance` remains unchanged for reference.
- **2026-11-25 user-requested correction:** For each source-song pair A/B, first exclude tracks already in the original playlist/album, previously used recommendations and duplicate video IDs, **then** take the first 15 remaining candidates from each radio. Previously the top-15 limit was applied *before* exclusions; on single-artist albums this wasted candidate slots. The user wants the existing B-only fallback retained, no artist-based filter, no new video/remix filters, and no deliberate stricter skipping of recommendations. If the radio B returns no usable candidate at all, the existing lack-of-recommendation edge case remains unchanged. The correction may expose more overlap but does not guarantee better relevance; it adds no extra network requests.
- **2026-11-25 user-requested experiment:** Increase the candidate limit from **15 to 30** for each radio, *after* excluding source-list tracks and previous recommendations and deduplicating. No other selection, filtering, radio request, playback, queue or Bluetooth changes. Explicitly test same-artist album «Весёлая жизнь» and compare the recommended tracks with earlier 15-candidate screenshots. The first-30 change can improve overlap but cannot guarantee musical relevance.
- The remaining V2 rule is unchanged: choose a common candidate ranked by summed positions (ties favour B), otherwise pick the first unused candidate from B's filtered top 30. No borrow from A's own radio or any neighboring pair; no change to the progressive/batch queue or Bluetooth restoration.
- **Important:** apply the same selection algorithm both to the **progressive first queue** and to **later near-end prefetch**. Do not discard user-approved progressive loading (~3 seconds to first group on initial 300+ Liked phone test), notification markers, current-song preservation, shuffle state or one-Like behavior. First-30 is a local candidate limit; it does not change radio network fetch size or guarantee better subjective relevance.
- Fix updater popup's literal `\\n\\nWhat's new` text by using the release body directly without appending escaped newlines and duplicate changelog link. It already links to CHANGELOG.md. No changes to update source/URL/package ID/certificate handling.
- Separate permanently signed Android Lab app ID `com.maxrave.simpmusic.dev.relevance2` and label `SimpMusic Lab (Relevance V2)`. Stable `main` build 68 is untouched. Builds are published to tag `enhanced-relevance-v2-latest` once CI passes.
- JVM tests verify top-30 boundary and overlap, rank/tiebreaks, B-only fallback, duplicate exclusion, no nearest-pair borrowing, progressive emission with empty B, cancel/rapid mode changes, and odd final source song. User phone testing and explicit merge approval are still required.
- **Phone acceptance to request:** compare 2+1 on a mixed Liked playlist and on a single-artist album, note the selected recommendations, verify first partial queue and later near-end prefetch, quickly switch Enhanced→Shuffle and back, and inspect the updater dialog in this Lab only if a suitable newer Lab release exists (the separate Lab does not itself offer Stable APK updates). Formatting can be validated by regression/static tests and later Stable update prompt; no synthetic update to be shown.
- Before any future Stable release, bump Android `versionCode` and write a new CHANGELOG.md Stable entry after user approves the actual quality on phone.

### 2B. Enhanced Anonymous Radio Lab — ISOLATED EXPERIMENT

- **2026-11-25 user request:** Create separate `feature/enhanced-anonymous-radio` from Relevance V2 *including the Bluetooth notification-star restoration fix*. Keep the source A/B pair radio requests anonymous, without any settings toggle or fallback to the user's authenticated radio.
- Pass a request-scoped `anonymous = true` only from `fetchEnhancedRadio`; the default for all other calls to `SongRepository.getRadioFromEndpoint` and `YouTube.next` remains signed in. A guest next request must omit the account cookie, SAPISIDHASH, X-Goog-Authuser, X-Goog-Pageid, and saved account visitorData. Preserve guest mode if the automix code recursively follows a playlist pointer.
- No change to Enhanced 2+1 order, top-30 filter-before-limit and radio-B fallback, shuffle state, local player, Bluetooth session recovery, star metadata recovery, Like, liked playlists or account auth.
- Separate app ID `com.maxrave.simpmusic.dev.anonymousradio`, a different signed Lab APK from Relevance V2. Do not modify or merge Stable `main`. Compare both Labs on the same Umka album. Guest requests could return fewer/no songs, or still reflect region/IP/language and YouTube's general recommender.
- Existing upstream pinned `core` nullability fixture error in `SyncSupportTest.kt` is excluded from the temporary CI test checkout, not production code; targeted star tests and normal app JVM tests must still pass.
- PR should target the V2 feature branch and stay draft until explicit phone approval.

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

### 7. Identify custom builds visibly in screenshots — MERGED INTO STABLE; RELEASE VERIFICATION PENDING

- **User-approved and merged 2026-11-16:** PR #2 merged `feature/enhanced-build-identity` into stable `feature/enhanced-shuffle`, merge commit `b3adf5b877486ac50d7bae8a7adb607c28217a60`. This merges **only build identity UI**, **not** radio-relevance recommendation changes.
- User successfully tested the label on Android in `SimpMusic Lab (Relevance)`: header `NOW PLAYING · Relevance Lab · 94bd9afe` and matching footer `Relevance Lab · 94bd9afe` under upstream `v2.5.0`.
- Stable signed workflow is configured to stamp `Enhanced Stable · <Git commit first 8 chars>` in the header and footer. The stable Android application ID and permanent signing key remain unchanged.
- Separate `Identity Lab` CI and on-phone `Relevance Lab` screenshot test passed. Verify the post-merge stable CI run and release before marking the new stable APK published; do not claim another on-phone stable test occurred.
- `feature/enhanced-radio-relevance` remains **unmerged/unapproved** as a recommendation algorithm; its screenshot identity is already present as a separate compatible patch.
- Future build identity improvements: display fuller fork/version/changelog and upstream licence attribution for sharing, tracked separately.
- Upstream SimpMusic version (`v2.5.0`) is distinct from our build label and Git commit identity.

### 8. Fork-aware in-app update checks — MERGED, SIGNED, PUBLISHED IN STABLE BUILD 66

- **Completed 2026-11-17:** user explicitly approved merging after signing and automated verification of Updates Lab and a phone screenshot proving the Lab fetched our Stable Android build 65 correctly without offering incompatible Stable APK.
- Merged [PR #5](https://github.com/aenorbiko/SimpMusic/pull/5) into `main` as commit `518934a58e71a960a945a5a995deeef0e4fe88b4`. Successful Stable [GitHub Actions run 43950350763](https://github.com/aenorbiko/SimpMusic/actions/runs/43950350763) compiled the Android build **66** APK, verified the permanent signing certificate and passed JVM shuffle/update regression tests.
- GitHub Release `enhanced-latest` was verified to publish a new `SimpMusic-Enhanced-arm64.apk` (72,138,403 bytes) with release notes **Android build: 66** and source commit `518934a5`. Download: https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-latest/SimpMusic-Enhanced-arm64.apk
- `com.maxrave.simpmusic.dev` Stable checks only our `aenorbiko/SimpMusic` tagged `enhanced-latest` GitHub API release (never upstream SimpMusic/F-Droid). Requires exact expected tag, released non-draft state, positive `Android build: N`, and expected large ARM64 APK URL. Offers update only if `N > BuildKonfig.versionCode`. The button opens the APK in the browser; Android still enforces package identity and signing certificate during user-approved installation.
- Stable Settings allow toggling auto-check, pin source to our GitHub and keep manual Check for update. Labs show no automatic update offers and remain separate Android packages. Failed offline/malformed requests return no update without affecting music.
- **2026-11-17 screenshot acceptance, Stable installed:** user shared screenshots from installed Stable `Enhanced Stable · 518934a5` confirming build **66** runs and plays music. Settings shows `Check for update — Last checked at 2026-11-17 06:54:36` and no visible offer for another APK. Credits/About still displays original upstream `SimpMusic v2.5.0` and maxrave-dev, not the Enhanced build number; we should expose both identities clearly for users who receive the fork, while preserving original project attribution. Screenshots alone do **not** prove that sign-in, playlist/settings data survived the in-place install, nor does a stored check timestamp prove successful network validation or a future 66→67 prompt.
- **2026-11-17 Android phone confirmation:** user explicitly confirmed that YouTube Music sign-in, playlists and preferences **all survived the in-place 65→66 update**. Stable identity screenshot `Enhanced Stable · 518934a5` shows the app playing and Check for update recorded a check at 06:54:36. **Still to test later:** a future 66→67 prompt, installation and update eligibility end-to-end; a timestamp alone does not prove successful fetch. 
- **Requested next:** improve Credits/About and Settings version presentation so Stable Enhanced build number, upstream base `v2.5.0`, and original author maxrave-dev are clearly distinguishable. Work in a dedicated branch/Lab, preserve original credits and licensing.
- **Later optional improvement:** publish immutable release tags `enhanced-vN` plus an explicit verifiable update manifest. Current `enhanced-latest` stable-release validation is adequate for this stage; metadata validation does not substitute for Android's signature check.

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

### 12. Clear Enhanced version, original SimpMusic and author in About — MERGED AND PUBLISHED, BUILD 67

- **Completed 2026-11-17:** user reviewed Android About Lab `ec2afcd1` screenshots and explicitly approved the layout. The Lab-only issue of showing a nonfunctional Update Lab diagnostic was fixed in `c467c2c0`; corrected About Lab CI run [43959815985](https://github.com/aenorbiko/SimpMusic/actions/runs/43959815985) passed.
- Merged approved [PR #6](https://github.com/aenorbiko/SimpMusic/pull/6) into `main` as commit `34f76e7c542c4dcc4f19be39ac80179df421a878`. Android Stable `versionCode` increased **66 → 67**. Successful stable [Actions run 43964309556](https://github.com/aenorbiko/SimpMusic/actions/runs/43964309556) compiled and signed the APK, verified the permanent signing certificate, and passed Enhanced shuffle, updater and About tests. GitHub `enhanced-latest` release notes confirmed **Android build: 67** with ARM64 APK published 2026-11-16 22:01 UTC.
- Settings → About us → Version now identifies `SimpMusic Enhanced · build 67 · Enhanced Stable`. Tapping Version opens Credits/About showing the Enhanced title, build/channel/short SHA, `Based on SimpMusic v2.5.0`, `Original developer: maxrave-dev` (clickable), plus links to our changelog and fork source. Original project description, website, GitHub, issues, credits and legal disclaimer remain accessible.
- Upstream/unlabelled builds retain original About title. The dedicated Update Lab check button is hidden from other Labs. No recommendation algorithm, playback, queue or auth state changes.
- **Verified earlier by user:** YouTube Music sign-in, preferences and playlists remained intact when updating Stable 65 → 66.
- **2026-11-17 Android phone-tested updater:** user opened Stable **66**, tapped Settings → About us → Check for update, and shared screenshot of a **real update dialog for Enhanced build 67**, with release date, expected commit `34f76e7c542c4dcc4f19be39ac80179df421a878`, release notes and Download button. This **passes automatic newer-version detection and presentation**. Subsequent user screenshots confirm **Stable build 67 installed and running** (`Enhanced Stable · 34f76e7c`). The exact download route is not recorded in the screenshot; **user explicitly confirmed** on 2026-11-17 that YouTube Music sign-in, playlists and preferences **all survived the in-place 66→67 upgrade**. **Minor formatting bug:** dialog literally shows `\\n\\nWhat's new` text appended to release notes instead of a line break; fix in a separate feature branch, preferably remove redundant extra What's new link since release body already has link. Installation of Stable build 67 was subsequently confirmed by screenshot; only the download route and preservation of data remain unverified.

- **2026-11-17 08:30 Brisbane, Stable 67 screenshots:** Settings footer shows `Enhanced Stable · 34f76e7c` with a recent update check at 08:29:39. Credits shows `Build 67 · Enhanced Stable`, `Based on SimpMusic v2.5.0`, `Original developer: maxrave-dev`, and fork changelog/source links. The revised About presentation is user-visible in Stable and the app is running. No update prompt for the current version is expected.

- **2026-11-17 user-confirmed stable upgrade acceptance:** after installing signed Enhanced Stable build 67, user answered **yes** that YouTube Music authorization, playlists and settings were all preserved. This completes on-phone verification of update detection 66→67, successful installation/running of build 67, correct Credits/About identity, and persistence of user data. A minor issue with literal `\\n\\nWhat's new` in the updater dialog remains a cosmetic follow-up; do not mark it fixed yet.

### 13. Restore Enhanced session after Android process restart — SIGNED RESTORE LAB READY, PHONE TEST PENDING

- **2026-11-19, phone screenshot report:** In **Liked Music** with Enhanced 2+1, leave it playing, close the app, then reopen the next day after the app process is recreated. The restored list still displays `Liked Music · Enhanced 2+1`, but the Enhanced/Shuffle state is OFF; recommendation stars are missing; the finite already-loaded ~14 source + up to 7 recommended songs run out without near-end refill. Screenshots from `Enhanced Stable · 34f76e7c`, not Relevance V2. This predates the unmerged Relevance experiment.
- **Root cause verified in main code:** `SharedViewModel.kt` initializes `_smartShuffleMode=OFF`, `_smartShuffleSource=null`, `_recommendedTrackIds=emptySet()`, `enhancedLikedOrder=emptyList()`, `nextEnhancedLikedIndex=0`, and used/excluded recommendation membership as new in-memory fields. `applySmartQueueKeepingCurrent()` saves queue labels ending `· Enhanced 2+1` but `playlistId=null` and `playlistType=RADIO`, so a restored queue is only a static set of songs. `maybePrefetchEnhanced()` immediately returns unless mode is ENHANCED; stars rely on in-memory `EnhancedMediaBridge.recommendedTrackIds`.
- **Fix must restore the *whole session*, not merely toggle the button or infer mode from the queue name.** Persist a versioned, queue-scoped session: original source ID/name, order/metadata of source tracks, next pair cursor, IDs of tracks already owned and recommended, and the current queue/session identity. Do **not** persist auth credentials.
- Restore only after actual player queue recreation and matching of the exact active queue identity; preserve the playing track, position and pause state. Rebuild recommended-star membership, correct toggle state, future prefetch and no duplicate recommendations. If queue/source mismatches, refuse stale restore and clear stale metadata. Clear when deliberately changing source or switching Enhanced OFF.
- Ensure persistence handles app death mid-progressive-first-batch or during near-end prefetch. Honor playback restoration disabled setting; also support albums and user playlists, not only Liked. Test crash/restart, empty/stale queue, current track match, 2+1 cadence, fast Next, near-end refill, notification stars, source switch, and saved auth/playlist continuity.
- **Implemented for testing 2026-11-19:** separate `feature/enhanced-session-restoration` branch and draft [PR #8](https://github.com/aenorbiko/SimpMusic/pull/8), from Stable `main`. New `EnhancedQueueSession` stores original source metadata/order, saved queue fingerprint, recommendation IDs and cursor in a local versioned DataStore setting, with guarded async restoration after Media3 queue becomes ready. Rehydrates Enhanced mode, stars and near-end prefetch without seeking/replaying the current song. Invalidates on mode/source change. No credentials are persisted. An initial test caught and fixed false-positive suffix ID matching; only exact IDs or the explicit Media3 `Video` prefix now match.
- **Automated CI verified:** signed isolated `SimpMusic Lab (Restore)` APK (`.dev.restore`) at `08fb4804`, [successful Android build + tests 44419997682](https://github.com/aenorbiko/SimpMusic/actions/runs/44419997682). Release: https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-restore-latest/SimpMusic-Enhanced-Restore-arm64.apk. Stable build 67 and unmerged Relevance V2 branch remain unchanged.
- **Phone acceptance still required:** install separate Restore Lab, sign in/restore Liked playlist, start Enhanced 2+1, hear several tracks, close/force-stop and reopen, verify state, starred recommendations and eventual near-end refill; also test ordinary playlist, manual mode OFF and source switch. No claim this recovery works on device until user confirms. **Do not merge without explicit approval.**

## Verified resolved / not to re-open without a regression

- Notification Like-heart flicker: user says it is already fixed (2026-11-15).
- Main-player/notification like action and scroll-induced accidental swipe: previously tested.
- Avoid generating duplicate recommendations *within one queue*. The same recommendation may legitimately appear when Enhanced is regenerated in a different queue.

## Branch map and merge status (2026-11-15)

- `dev`: **GitHub default branch**, original/upstream SimpMusic source. **No Enhanced feature merges here.**
- `main`: **our current stable Enhanced integration branch** (2+1 on all playlists, likes, icons, screenshot build labels). Created 2026-11-16 from approved `feature/enhanced-shuffle`; stable signed APK workflow moved to run from `main`. App label `SimpMusic Enhanced`, same Android package ID `com.maxrave.simpmusic.dev` and signing certificate; Android versionCode incremented to 63 for an in-place update. Verify new main-branch CI/release separately.
- `feature/enhanced-shuffle`: previous tested stable integration branch, now **historical / no longer target of future merges or releases**. Do not delete until the `main` workflow, published APK and repository default have been verified.
- `feature/enhanced-all-playlists`: branched from older Enhanced baseline; user-tested on phone, **merged by PR #1 into `feature/enhanced-shuffle`**. Branch retained for history.
- `feature/enhanced-radio-cache`: branched from older Enhanced baseline; 64 radio response lists, TTL 20 min; automatic tests only, no approved phone test; **unmerged**.
- `feature/enhanced-optimisation`: branched from older Enhanced baseline; multi-feature prototypes (including per-device Bluetooth lyrics offset and backup work); **unmerged, not fully phone-tested**. We must extract each feature into its own clean branch rather than merge everything.
- `feature/enhanced-radio-relevance`: top-15 match or second-song-radio fallback; includes visible Relevance Lab build identity; **recommendation algorithm not user-accepted; unmerged**.
- `feature/enhanced-build-identity`: created from tested stable Enhanced, passed separate CI and user's Relevance Lab screenshot test, and **merged into stable by PR #2 on 2026-11-16**. Kept for history.
- Development workflow: copy/branch from stable → implement one feature → build a separately installed Lab APK → phone test → explicit approval → merge into stable. Leave `dev` alone until an explicit future decision to reorganize branches.

## Stable branch/app naming cleanup (2026-11-16)

- **Our stable branch:** `main`; app launcher label: **SimpMusic Enhanced**; in-queue build stamp: **Enhanced Stable · <short SHA>**; signed APK release: `enhanced-latest` (`SimpMusic-Enhanced-arm64.apk`).
- **Upstream snapshot:** `dev` (do not accidentally merge experiments here; not automatically tracking the upstream `maxrave-dev/SimpMusic` repo).
- **Experimental branches:** `feature/enhanced-radio-relevance`, `feature/enhanced-radio-cache`, `feature/enhanced-optimisation` remain unmerged. Installed Lab APKs use separate package IDs and show `SimpMusic Lab (Relevance)`, etc., with build stamp `Relevance Lab · <short SHA>`.
- **Archive branches** `feature/enhanced-all-playlists`, `feature/enhanced-build-identity`, `feature/enhanced-shuffle`: retain for history, do not target with new features.
- **GitHub UI/default branch:** repo default is still `dev` unless changed in Settings → General → Default branch. The current GitHub connector cannot change repository settings; switching default to `main` remains one manual step and does not change app code.
- All future accepted PRs target **`main`**. Create independent feature branches from `main`; experimental branches created earlier must be rebased or have their feature-only changes ported to a new branch based on `main`.
- Changing the app **display label** does not change Android `applicationId`, APK signing key or stored user data. Do not rename package IDs in existing installed apps.

## Current delivery checkpoint

- **MERGED:** PR #1 merged into `feature/enhanced-shuffle` on 2026-11-15 (merge commit `42b19994`). Stable app label remains `SimpMusic Dev`; the Lab-only name is set in the Lab workflow. Stable workflow runs all-playlist source and JVM regression checks. **CI/release status must be verified separately; do not claim the newly merged APK has been published before the stable workflow succeeds.**
- Keep future improvements isolated in their own branches branched from `main`. Do not combine startup-speed, relevance, radio-preserve, cache and lyrics-profile work in the merge.


### 25 November — Anonymous Radio full-list diagnostics (phone test pending)

- User approved removing the local top-30 cap on both A/B returned radio lists. Do not fetch continuation pages yet; keep B-only fallback, anonymity, progressive startup, prefetch and Bluetooth star restoration.
- Tap the queue recommendation star to see A/B seeds, received and eligible counts, full eligible overlap, old top-30 overlap, selected source and one-based filtered ranks. Diagnostics are process-local and may be unavailable for restored recommendations; no account data is stored. Same Anonymous Radio package; experimental version code 69. Stable remains 68. No merge until user approval and phone testing.


### 25 November — conditional continuation experiment (Anonymous Radio)

- Phone screenshot f316c365: first page returned 50 tracks per radio, and one of 5–6 pairs had overlap beyond old top 30 (A24/B37, A32/B44). User approved a second page only for pairs without eligible first-page overlap, up to 100 considered tracks per seed. No Enhanced request time limit for this diagnostic experiment; transport timeouts and cancellation still apply. Keep four concurrent requests, B-only fallback and anonymous flag on every page. Copy and Copy all from the star dialog; include per-page counts, availability, request/semaphore timings and first/final overlap. Version code 70 in same Lab, no stable merge.


### 25 November — parallel prioritized radio experiment (Anonymous Radio)

- Build 70 phone test: 7 pairs, 0 first-page overlaps, 2 after continuation; first decision 19.639 s, last 43.049 s. User approved increasing concurrent requests to eight, prioritizing nearest pairs and overlapping continuations. Build 71: FIFO admission of four complete pair workers, two requests each; early continuation if no possible first-page match, recheck exact ordered exclusions for matches consumed by earlier pairs. All results still emitted in order. One shared eight-slot radio semaphore across startup/prefetch; no Enhanced timeout. Added pair admission and ordered-decision waits to diagnostics. Same Lab ID; Stable unchanged, no merge. Phone timing acceptance pending.


### 25 November — metadata overlap diagnostics (Anonymous Radio)

- Build 71 phone test: first decision 10.522 s, final 23.266 s; 26 successful requests, no semaphore waits. User approved diagnostic-only title/artist matching and possible versions, with first five examples. Build 72 compares eligible A/B tracks from the pages already retrieved, excludes equal videoIds, counts distinct cross-radio ID pairs in disjoint categories, shows original titles/artists/ranks/durations for up to five best combined-rank examples per category. Requires matching normalized artist sets; missing artists are not inferred. No new requests, no change to selection, continuation policy, concurrency, timeout, Star Fix, or stable. Same Lab package, version code 72.


### 25 November — shared artist diagnostics (Anonymous Radio)

- Build 72 phone test on mobile data: 7 pairs, 1 exact-ID overlap pair; 2 additional metadata pairings within the same seed pair, including one new composition (Training Season). User approved adding artist overlap diagnostics. Build 73 counts unique normalized artist names in both eligible radios, and separately artists with a different base-title / different-videoId track pair. Show up to five artist examples by combined best eligible ranks, up to three tracks per side with counts, and the best different-song candidate separately. Explicit collaborators count individually; missing artist metadata not inferred. No change to selection, continuation, requests, scheduling or stable. Same Lab package.


### 25 November — raw radio overlap before exclusions (Anonymous Radio)

- Build 73 phone test: 7 pairs, no common eligible song IDs or title matches, one shared eligible artist (Of The Trees), first decision 11.918 s, final 23.326 s. Build 74 adds diagnostic-only full fetched page comparison before source/owned/used ID exclusion; distinct exact matches, title/version counts, lost shared artists, and up to five examples per category with raw ranks and excluded/eligible labels. Report lost artists still having an eligible B track, plus count for A-only, without changing actual selection or requesting another page. Same Lab package; stable unchanged.


### 25 November — activate four-tier selection (Anonymous Radio)

- Build 74 phone test: 7 pairs, 0 exact or title matches, 3 shared artist names in two pairs; none lost by exclusions. First decision 7.595 s, final 19.371 s. User declined grouping shuffle pairs and third pages, approved exact ID > normalized title plus artist > shared artist with different base title > B fallback. Build 75 activates those tiers only for final eligible lists, choosing the B-side track at best combined rank. Exact-only continuation policy, 100-track cap, eight requests, anonymous mode and random shuffle unchanged. Different versions and pre-exclusion matches remain diagnostic only; retain full Copy/Copy all for testing. Shared artist names can occasionally be soundtrack/project credits, not necessarily real artists. No main merge until phone acceptance. Next intended experiment: bounded radio metadata cache, separate branch from accepted stable; old cache branch is outdated (20 min / 64 radios / 40 tracks, signed-in, no continuations).


### 25 November — Stable promotion approved

- Build 75 CI run 45589298145 success and signed APK published. Phone acceptance: 14 distinct pairs, 4 exact selections, 1 shared artist, 9 B fallback, no strict title selection encountered; all 50 page requests successful; second batch 6.693 s first and 15.767 s final. User approved Main merge. Stable 76 release preparation retains tested algorithm and session diagnostics, adds stable regression checks and changelog. Next separate feature: persistent aggregate repeat-radio request counter for 20 min / 1 h / 24 h and Copy statistics in About, no audio or radio cache; test before merging it.


### 25 November — repeat radio statistics Lab

- Stable 76 merged in PR #13, CI succeeded, APK published. Build 77 separate Radio Statistics Lab from main; no cache or radio selection changes. Count completed first-page radio requests, successful non-empty first pages, failed/empty first pages, continuation requests separately and repeats within 20m/1h/24h since previous successful first page. Cumulative windows, refreshed TTL, upper-bound estimates not simulated LRU. Local DataStore key, at most 4096 recent seed IDs, prune after 24h on next recorded event. Aggregate report only, no IDs/titles/tokens. Background-in-process coroutine persists without delaying queue; abrupt termination can lose outstanding writes. Settings > About > Enhanced statistics > Copy statistics. Separate app requires account/playlist setup or backup restore; test before Stable promotion.


### 25 November — statistics Stable promotion approved

- Build 77 CI run 45603066859 succeeded, signed APK published. Phone test: 14 successful first-page requests, 12 continuation requests, no repeats/errors/evictions/clock resets/unrecorded events. All counts persisted unchanged after Force stop and relaunch. User explicitly approved merging. Promote the tested statistics code as Stable 78 with changelog and regression test; no actual cache, no playback code changes. Collect 2–3 days of ordinary listening via Settings > About us > Enhanced statistics > Copy statistics; the separate Lab's prior counters are not transferred to Stable.


### 26 November — strict duplicate exclusion and statistics reset Lab

- Build 79: exclude alternate IDs matching normalized strict title and full artist set of source playlist or earlier recommendations (including prior batches and restored session); preserve original source order, remixes/version labels, missing metadata and distinct titles. Existing 2-page policy applies to eligible exact overlap, so some pairs may now request page 2 when page 1 duplicates are excluded. No new API type, no radio cache. Old session snapshots compatible; recover keys from current queue if available. Reset statistics with confirmation; clears local counts/history only. Android backup includes full preferences file, so restoring a newer backup can restore statistics. Use reset after restore. Separate Lab, test before Main merge.

- Build 79 Android APK compilation and signing succeeded, but JVM test compilation failed on nullable vs non-nullable List assertion at EnhancedTrackDeduplicationTest.kt:51. Build 80 fixes the test's explicit type; production code and test expectations unchanged. Retry full signed build and regressions before publishing.

- Build 81 (user approved single change): one shared coroutine mutex gates initial shuffle/Enhanced setup and prefetch generation through actual queue append and metadata checkpoint. Publish lazy jobs before starting to prevent inline reentry; reject outdated session/cursor after lock acquisition and before commit. Retain 4 pairs/8 radio requests per batch; no inferred stars, persistence redesign or extra diagnostics. Phone test: rapidly tap third/second-last and last track in queue, verify one block of 14 source tracks and up to 7 recommendations; repeat during restore. Root cause suspected concurrent requests from screenshots/logs, not conclusively reproduced. Main unchanged.

- Build 82 (user approved ARM64-only during development): limit Android NDK ABI filters and APK splits to arm64-v8a; omit universal APK. Retain all existing tests and permanent signing. Add verification of single output and ARM64 native libraries to Lab and future Stable workflows. Restore full Android ABI set and universal APK via -PsimpmusicAllAndroidAbis=true when preparing broad distribution; adjust ARM64-only CI guard if enabling full matrix. No app logic changes. Main unchanged pending Lab acceptance.

- Build 82 failed during Gradle configuration: AGP rejects overlapping single ARM64 ndk abiFilters and splits. Build 83 keeps NDK ARM64 filter, disables splits in ARM64-only mode (single androidApp-debug.apk), retains original multi-ABI split mode on opt-in and updates Lab/future Stable output paths; published asset name unchanged. No cache changes yet. Build 80 cold baseline: exportLibraryDefinitions 1m51s, APK assemble 4m52s (313 tasks executed, zero FROM-CACHE), tests 1m25s; setup-java reported cache miss. Investigate official setup-gradle and enabling Gradle build cache separately.

- Build 84 (user approved build optimization): use official gradle/actions/setup-gradle@v4 instead of setup-java's Gradle caching; cache writes enabled for trusted push/manual Lab and Stable workflows. Enable standard Gradle task-output cache (org.gradle.caching=true), no manual build-directory archives or cross-run Configuration Cache encryption key. Keep signature and ARM64 validation, force both JVM test tasks to run with --rerun (their compile dependencies may use cache). Manual workflow_dispatch clean_build disables GitHub cache restore/save, Gradle build cache and local Configuration Cache for verification. No application/runtime or recommendation-cache changes. Measure first cold build and subsequent warm build task counts, durations and FROM-CACHE hits before judging savings. Main unchanged until user approves merge.
