#!/usr/bin/env python3
"""Build-time guard: cold Bluetooth restore must preseed recommended stars before Media3 title conversion."""
from pathlib import Path

patch = Path("scripts/apply_enhanced_core_patch.py").read_text()
handler = Path("core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt").read_text()
helper = Path("core/data/src/commonMain/kotlin/com/maxrave/data/mediaservice/RestoredEnhancedRecommendedIds.kt").read_text()
adapter = Path("core/media/media3/src/main/java/com/maxrave/media3/exoplayer/ExoPlayerAdapter.kt").read_text()
vm = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt").read_text()

restore_pos = handler.index("restoredEnhancedRecommendedIds(")
player_pos = handler.index("addMediaItem(\n                currentPlayingTrack.toGenericMediaItem(),", restore_pos)
assert restore_pos < player_pos, "star IDs must be ready before first Media3 item conversion"
assert 'EnhancedMediaBridge.recommendedTrackIds.value =' in handler
assert "snapshotJson = dataStoreManager.getString(\"enhanced_queue_session_v1\").first()" in handler
assert 'savedPlaylistName.endsWith(" · Enhanced 2+1")' in helper
assert 'savedPlaylistName != sourceName + " · Enhanced 2+1"' in helper
assert "currentVideoId !in expectedQueue" in helper
assert "if (!ordered && !insertedCurrent)" in helper
assert "recommendedIds.intersect(savedQueueVideoIds.toSet())" in helper
assert 'EnhancedMediaBridge.recommendedTrackIds.value' in adapter
assert 'title = "✦ " + metadata.title' in adapter
assert "setRecommendedTrackIds(plan.recommendedIds)" in vm
assert "replaceMediaItem(" not in helper, "do not touch playing item"
assert "seekTo(" not in helper, "do not seek during badge restoration"
print("PASS: restored recommendation badges preseeded before Media3 conversion; no playback mutation.")
