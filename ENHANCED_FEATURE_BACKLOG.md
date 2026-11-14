# SimpMusic Enhanced — feature backlog

## Development rules
- Keep `feature/enhanced-shuffle` as the tested working baseline.
- Implement **one behavior change per feature branch**.
- A successful CI build is not acceptance: install and test each APK on a phone.
- **Only merge after explicit user approval**. Preserve unmerged branches so we can return to them.
- Lab builds must have their own app ID, APK, and GitHub Release. Do not overwrite the stable APK or another Lab.

## 1. Enhanced Shuffle in every playlist and album
**Status:** Implemented on branch `feature/enhanced-all-playlists`; Android testing and user approval pending.

**Scope:** Off → Shuffle → Enhanced for YouTube playlists, albums, local playlists, and library song collections. Radio should use each selected collection's songs, not Liked Music. Preserve current playback when cycling modes inside one source; start the chosen source when pressing Shuffle in a different album/playlist. Notification button should follow the same 3-state behavior where the full source queue is known.

**Test:** album of one artist; normal playlist; local playlist; source switching; 2 source songs + 1 recommendation; Liked regression; prefetch and Next. Then ask for merge approval.

## 2. Reduce the ~10-second Enhanced startup delay
**Status:** Planned only; **not included** in the all-playlists branch.

**Observed problem:** The app waits for an entire initial 14-song batch (up to 14 radio lookups in groups of 4) before displaying the Enhanced queue; on a 300+ Liked playlist user observes about 10 seconds. Short-lived radio metadata caching alone cannot eliminate first-load latency for uncached songs.

**Candidate implementation:** Prioritize the current/first source pair A+B; show or append their recommendation promptly, then build subsequent 2+1 pairs progressively in the background. Keep the player responsive without restarting the current song or inserting duplicates. Continue prefetch before reaching the end of the queue; ensure cancellation and stale-results safety when switching modes or sources.

**Acceptance tests:** compare first recommendation visibility to present 10-second baseline, use large Liked playlist and several short albums, and check switching from notification, order 2+1, pause/skip, offline or empty radio replies, and prefetch near end.

## 3. Session radio metadata cache
**Status:** Implemented on separate branch `feature/enhanced-radio-cache`; phone testing not accepted or merged. Do not combine this branch with feature 1 until approval.

Caches up to 64 distinct radio responses for 20 minutes in memory (metadata only); `EnhancedRadioCacheTest` covers expiry and eviction. Confirm re-use with recurring seeds; do not claim it improves large-playlist first-load latency.

## Later independent improvements
- Device-specific Bluetooth Lyrics Offset (prototype on archived `feature/enhanced-optimisation`; not merged).
- Backup restore safety, security, and device-profile portability (prototype on archived experiment; validate independently).
- Display useful build/version identity and upstream licensing for distributing APKs.
- Reduce unnecessary debug ABIs, refactor oversized view models, and improve automatic UI tests.
