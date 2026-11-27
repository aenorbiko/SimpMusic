"""Apply Start Radio changes to the pinned core, preserving upstream anchors."""
from pathlib import Path


def apply_start_radio_patch(root):
    def edit(path, old, new):
        file = root / path
        source = file.read_text()
        if source.count(old) != 1:
            raise SystemExit(f"Start Radio anchor mismatch: {path}: {old[:100]!r}")
        file.write_text(source.replace(old, new))

    snippets = Path(__file__).resolve().parent / "core-patches"
    target = root / "domain/src/commonMain/kotlin/com/maxrave/domain/mediaservice/handler/StartRadioQueuePlan.kt"
    target.write_text((snippets / "StartRadioQueuePlan.kt").read_text())
    radio_body = (snippets / "StartRadioHandler.kt.fragment").read_text()
    bridge = "domain/src/commonMain/kotlin/com/maxrave/domain/data/player/EnhancedMediaBridge.kt"
    edit(bridge, "    var onShuffleClick: (() -> Unit)? = null\n",
         "    var onShuffleClick: (() -> Unit)? = null\n"
         "    val queueIntentRevision = MutableStateFlow(0L)\n"
         "    var onRadioQueueCommitted: (() -> Unit)? = null\n")

    interface = "domain/src/commonMain/kotlin/com/maxrave/domain/mediaservice/player/MediaPlayerInterface.kt"
    edit(interface, "    fun removeMediaItem(index: Int)\n",
         "    fun removeMediaItem(index: Int)\n\n"
         "    /** Only call from the playback owner's dispatcher; completes the list mutation inline. */\n"
         "    fun removeMediaItemOnOwner(index: Int) = removeMediaItem(index)\n")

    adapters = (
        "media/media3/src/main/java/com/maxrave/media3/exoplayer/CrossfadeExoPlayerAdapter.kt",
        "media/media-jvm/src/main/java/com/simpmusic/media_jvm/mpv/MpvPlayerAdapter.kt",
    )
    for path in adapters:
        file = root / path
        source = file.read_text()
        start = source.index("    override fun removeMediaItem(index: Int) {")
        end = source.index("\n    /**", start)
        old = source[start:end]
        prefix = "    override fun removeMediaItem(index: Int) {\n        coroutineScope.launch {\n"
        suffix = "        }\n    }\n"
        assert old.startswith(prefix) and old.endswith(suffix), path
        body = old[len(prefix):-len(suffix)]
        body = "\n".join(line[4:] if line.startswith("    ") else line
                         for line in body.split("\n")).replace("return@launch", "return")
        new = ("    override fun removeMediaItem(index: Int) {\n"
               "        coroutineScope.launch { removeMediaItemOnOwner(index) }\n"
               "    }\n\n"
               "    override fun removeMediaItemOnOwner(index: Int) {\n"
               + body + "    }\n")
        edit(path, old, new)

    handlers = (
        "data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt",
        "data/src/jvmMain/kotlin/com/maxrave/data/mediaservice/JvmMediaPlayerHandlerImpl.kt",
    )
    for path in handlers:
        file = root / path
        source = file.read_text()
        imports = ("import com.maxrave.domain.mediaservice.handler.planStartRadioQueue\n"
                   "import com.maxrave.domain.mediaservice.handler.replaceUpcomingKeepingCurrent\n"
                   "import com.maxrave.domain.mediaservice.handler.startRadioRequestIsCurrent\n")
        if "import com.maxrave.domain.data.player.EnhancedMediaBridge\n" not in source:
            imports += "import com.maxrave.domain.data.player.EnhancedMediaBridge\n"
        edit(path, "import com.maxrave.domain.mediaservice.handler.QueueData\n",
             "import com.maxrave.domain.mediaservice.handler.QueueData\n" + imports)
        edit(path, "    private var loadJob: Job? = null\n",
             "    private var loadJob: Job? = null\n"
             "    private val startRadioRequestRevision = AtomicLong(0L)\n"
             "    private val startRadioCommitRevision = AtomicLong(0L)\n")
        source = file.read_text()
        start = source.index("    override fun toggleRadio() {")
        end = source.index("    override fun toggleLike()", start)
        edit(path, source[start:end], radio_body)
        for anchor in (
            "    override fun setQueueData(queueData: QueueData.Data) {\n",
            "    override fun resetSongAndQueue() {\n",
            "    override fun clearMediaItems() {\n",
            "    override fun reset() {\n",
            "    override fun release() {\n",
        ):
            edit(path, anchor, anchor + "        startRadioRequestRevision.incrementAndGet()\n")
        edit(path, "            PlayerEvent.Shuffle -> {\n",
             "            PlayerEvent.Shuffle -> {\n                startRadioRequestRevision.incrementAndGet()\n")
        edit(path, "        if (mediaItem?.mediaId != _nowPlaying.value?.mediaId) {\n",
             "        if (mediaItem?.mediaId != _nowPlaying.value?.mediaId) {\n"
             "            startRadioRequestRevision.incrementAndGet()\n")

        # Share handler metadata updates; only the adapter dispatch differs.
        anchor = ("        player.removeMediaItem(if (player.shuffleModeEnabled) "
                  "player.getUnshuffledIndex(position) else position)\n"
                  "        _queueData.update {\n")
        edit(path, anchor, anchor.split("\n")[0] + "\n"
             "        removeQueueTrackAt(position)\n"
             "    }\n\n"
             "    private fun removeUpcomingRadioItemOnOwner(position: Int) {\n"
             "        if (position !in queueData.value.data.listTracks.indices) return\n"
             "        val index = if (player.shuffleModeEnabled) player.getUnshuffledIndex(position) else position\n"
             "        player.removeMediaItemOnOwner(index)\n"
             "        removeQueueTrackAt(position)\n"
             "    }\n\n"
             "    private fun removeQueueTrackAt(position: Int) {\n"
             "        _queueData.update {\n")

        source = file.read_text()
        start = source.index("    override fun getRelated(videoId: String) {")
        end = source.index("    override fun setQueueData(", start)
        old = source[start:end]
        new = old.replace("        coroutineScope.launch {\n", "        val radioRevision = startRadioCommitRevision.get()\n"
                          "        coroutineScope.launch {\n"
                          "            if (radioRevision != startRadioCommitRevision.get()) return@launch\n", 1)
        new = new.replace(".collect { response ->\n", ".collect { response ->\n"
                          "                if (radioRevision != startRadioCommitRevision.get()) return@collect\n", 1)
        edit(path, old, new)
        source = file.read_text()
        start = source.index("    override fun loadMore() {")
        end = source.index("    override fun getRelated(", start)
        old = source[start:end]
        new = old.replace("    override fun loadMore() {\n",
                          "    override fun loadMore() {\n        val radioRevision = startRadioCommitRevision.get()\n", 1)
        new = new.replace("                coroutineScope.launch {\n",
                          "                coroutineScope.launch {\n"
                          "                    if (radioRevision != startRadioCommitRevision.get()) return@launch\n")
        edit(path, old, new)
    print("Applied Start Radio in-place queue replacement to Android and JVM")
