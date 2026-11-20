#!/usr/bin/env python3
"""Build-time guard for headless Enhanced session restoration via Bluetooth.

Run AFTER apply_enhanced_core_patch.py on the temporary pinned core checkout.
Keeps the normal Media3 saved-queue recovery and a *single* Enhanced owner.
"""
from pathlib import Path

app = Path("androidApp/src/main/java/com/maxrave/simpmusic/SimpMusicApplication.kt").read_text()
bridge = Path("core/domain/src/commonMain/kotlin/com/maxrave/domain/data/player/EnhancedMediaBridge.kt").read_text()
service = Path("core/media/media3/src/main/java/com/maxrave/media3/service/SimpleMediaService.kt").read_text()
handler = Path("core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt").read_text()
vm = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt").read_text()
di = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/di/ViewModelModule.kt").read_text()

assert "single {\n            SharedViewModel(" in di, "SharedViewModel must remain a Koin singleton"
assert "var onMediaServiceCreated: (() -> Unit)? = null" in bridge
assert "EnhancedMediaBridge.onMediaServiceCreated = {" in app
assert "get<SharedViewModel>()" in app
assert "EnhancedMediaBridge.onMediaServiceCreated?.invoke()" in service
assert "restoreEnhancedSessionIfSaved()" in vm, "headless owner must restore saved Enhanced session"
assert "maybePrefetchEnhanced()" in vm, "headless owner must extend 2+1 queue"
assert "coroutineScope.launch { restoreSavedQueue(playWhenReady = false) }" in handler, "keep normal queue restore"
assert "return restoreSavedQueue(playWhenReady = true)" in handler, "keep car media-button auto-resume"
assert "onMediaServiceCreated" not in handler, "bootstrap belongs in the MediaSession service, not the handler"

print("PASS: Bluetooth-only service starts existing Enhanced owner; saved-queue resumption still works.")
