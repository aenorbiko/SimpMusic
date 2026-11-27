#!/usr/bin/env python3
"""Integration guard; JVM tests exercise the shared planner and queue mutation."""
from pathlib import Path

root = Path("core")
for path in (
    "data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt",
    "data/src/jvmMain/kotlin/com/maxrave/data/mediaservice/JvmMediaPlayerHandlerImpl.kt",
):
    text = (root / path).read_text()
    radio = text.split("    override fun toggleRadio() {", 1)[1].split("    override fun captureSelectedRadioRequest()", 1)[0]
    for required in ("startRadioRequestIsCurrent(", "planStartRadioQueue(", "replaceUpcomingKeepingCurrent(",
                     "removeUpcomingItem = ::removeUpcomingRadioItemOnOwner", "startRadioCommitRevision.incrementAndGet()",
                     "if (!stillCurrent()) return@collectLatest"):
        assert required in radio, (path, required)
    for forbidden in ("clearMediaItems(", "seekTo(", "player.prepare(", "playWhenReady =", "loadPlaylistOrAlbum(", "removeMediaItems("):
        assert forbidden not in radio, (path, forbidden)
    assert "player.removeMediaItemOnOwner(index)" in text
    assert "if (radioRevision != startRadioCommitRevision.get()) return@collect" in text
print("PASS: both ordinary radio handlers use the shared in-place mutation and reject stale requests.")

# Verify the real song menu entry point, not only the service implementation.
# This fails on the previous Lab: its current-song action bypassed toggleRadio.
app = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic")
menu = (app / "ui/component/ModalBottomSheet.kt").read_text()
menu_radio = menu.split("text = Res.string.start_radio,", 1)[1].split("hideModalBottomSheet()", 1)[0]
assert "NowPlayingBottomSheetUIEvent.StartRadio(" in menu_radio
assert "videoId = uiState.songUIState.videoId" in menu_radio
vm = (app / "viewModel/NowPlayingBottomSheetViewModel.kt").read_text()
dispatch = vm.split("fun onUIEvent(ev: NowPlayingBottomSheetUIEvent) {", 1)[1].split("viewModelScope.launch", 1)[0]
assert "ev is NowPlayingBottomSheetUIEvent.StartRadio" in dispatch
assert "mediaPlayerHandler.getCurrentMediaItem()?.mediaId?.removePrefix(MERGING_DATA_TYPE.VIDEO) == ev.videoId" in dispatch
assert "mediaPlayerHandler.toggleRadio()\n            return" in dispatch
assert "loadMediaItem(" not in dispatch and "setQueueData(" not in dispatch
print("PASS: current-song Start Radio menu routes directly to the protected service handler.")

# The selected-song response must be checked at the menu and again after the
# suspending metadata writes on the playback owner, before changing the queue.
assert "mediaPlayerHandler.captureSelectedRadioRequest()" in dispatch
selected = vm.split("is NowPlayingBottomSheetUIEvent.StartRadio -> {", 1)[1]
assert "if (!mediaPlayerHandler.isSelectedRadioRequestCurrent(request)) return@collectLatest" in selected
assert "mediaPlayerHandler.applySelectedRadioIfCurrent(" in selected
assert "loadMediaItem(" not in selected and "setQueueData(" not in selected
for path in (
    "data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt",
    "data/src/jvmMain/kotlin/com/maxrave/data/mediaservice/JvmMediaPlayerHandlerImpl.kt",
):
    source = (root / path).read_text()
    apply = source.split("override suspend fun applySelectedRadioIfCurrent(", 1)[1].split("override fun toggleLike()", 1)[0]
    check = "if (!isSelectedRadioRequestCurrent(request)) return@withContext false"
    assert apply.count(check) == 2
    assert apply.index("songRepository.updateDurationSeconds(") < apply.rindex(check) < apply.index("reset()") < apply.index("addMediaItem(")
    assert "withContext(coroutineScope.coroutineContext.minusKey(Job))" in apply
print("PASS: selected-song radio checks shared request validity immediately before replacing playback.")
