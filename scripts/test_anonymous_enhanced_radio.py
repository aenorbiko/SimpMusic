#!/usr/bin/env python3
"""Static privacy and behavior regression: only Enhanced A/B song radios use guest requests."""
from pathlib import Path
repo = Path("core")
domain = (repo/"domain/src/commonMain/kotlin/com/maxrave/domain/repository/SongRepository.kt").read_text()
implementation = (repo/"data/src/commonMain/kotlin/com/maxrave/data/repository/SongRepositoryImpl.kt").read_text()
youtube = (repo/"service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt").read_text()
transport = (repo/"service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt").read_text()
enhanced = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/EnhancedShuffleRecommendations.kt").read_text()
gradle = Path("androidApp/build.gradle.kts").read_text()
planner = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/EnhancedShufflePlanner.kt").read_text()
assert "getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint, anonymous: Boolean = false, continuation: String? = null)" in domain
assert "override fun getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint, anonymous: Boolean, continuation: String?)" in implementation
assert ".next(endpoint.toWatchEndpoint(), continuation = continuation, anonymous = anonymous)" in implementation
assert "anonymous: Boolean = false" in youtube
assert "anonymous = anonymous," in youtube, "Automix recursion must retain anonymous flag"
assert "anonymousRequest: Boolean = false" in transport
assert "if (!anonymousRequest) {" in transport and 'append("X-Goog-Authuser"' in transport
assert "if (setLogin && !anonymousRequest)" in transport, "No Cookie/SAPISIDHASH for guest"
assert "ytClient(client, setLogin = !anonymous, anonymousRequest = anonymous)" in transport
assert "client.toContext(locale, if (anonymous) null else visitorData)" in transport
assert "continuation = continuation," in enhanced
assert "anonymous = true," in enhanced, "Only Enhanced song-radio must explicitly opt into guest mode"
assert 'playlistId = "RDAMVM${song.videoId}"' in enhanced
assert "RADIO_CANDIDATE_LIMIT" not in planner
assert 'isAnonymousRadioExperiment -> ".dev.anonymousradio"' in gradle
assert "best?.track ?: b.firstOrNull()" in planner, "Keep the user's B-radio fallback"
print("PASS: no signed-in cookie, account header or account visitorData in Enhanced radio; other requests remain signed in.")
