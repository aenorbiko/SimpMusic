#!/usr/bin/env python3
"""Patch a temporary checkout of the pinned core submodule for Android media controls.

This does not change the upstream submodule commit. Anchors fail fast if upstream
files change; no credential or generated binaries are committed.
"""
from pathlib import Path
ROOT = Path("core")
def edit(path, old, new):
    file=ROOT/path
    source=file.read_text()
    count=source.count(old)
    if count != 1:
        raise SystemExit("Unexpected patch anchor in %s (%s matches): %r" % (file,count,old[:110]))
    file.write_text(source.replace(old,new))
def replace_range(path, start, end, replacement):
    file=ROOT/path
    source=file.read_text()
    if source.count(start)!=1: raise SystemExit("Missing start in "+str(file))
    a=source.index(start)
    b=source.find(end,a+len(start))
    if b<0: raise SystemExit("Missing end in "+str(file))
    file.write_text(source[:a]+replacement+source[b:])

generic="domain/src/commonMain/kotlin/com/maxrave/domain/data/player/GenericCommandButton.kt"
edit(generic,
"""    data class Shuffle(
        val isShuffled: Boolean,
    ) : GenericCommandButton()""",
"""    data class Shuffle(
        val isShuffled: Boolean,
        val isEnhanced: Boolean = false,
    ) : GenericCommandButton()""")
bridge=ROOT/"domain/src/commonMain/kotlin/com/maxrave/domain/data/player/EnhancedMediaBridge.kt"
bridge.write_text("""package com.maxrave.domain.data.player

import kotlinx.coroutines.flow.MutableStateFlow

/** Shared notification/UI state: off=0, shuffle=1, Enhanced=2. */
object EnhancedMediaBridge {
    val shuffleMode = MutableStateFlow(0)
    val youtubeLiked = MutableStateFlow<Boolean?>(null)
    var onShuffleClick: (() -> Unit)? = null
}
""")

service="data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt"
edit(service,"import com.maxrave.domain.data.player.GenericCommandButton\n",
     "import com.maxrave.domain.data.player.GenericCommandButton\nimport com.maxrave.domain.data.player.EnhancedMediaBridge\n")
edit(service,"import com.maxrave.domain.utils.Resource\n",
     "import com.maxrave.domain.utils.Resource\nimport com.maxrave.domain.utils.YouTubeLikeStatus\n")
edit(service,
"""        toggleLikeJob = Job()
        loadJob = Job()""",
"""        toggleLikeJob = Job()
        // Notifications listen to the same mode and YouTube like as the app UI.
        coroutineScope.launch {
            combine(EnhancedMediaBridge.shuffleMode, EnhancedMediaBridge.youtubeLiked, controlState) { mode, ytLiked, control ->
                listOf(
                    GenericCommandButton.Shuffle(
                        isShuffled = mode != 0 || control.isShuffle,
                        isEnhanced = mode == 2,
                    ),
                    GenericCommandButton.Like(ytLiked == true),
                    GenericCommandButton.Repeat(repeatState = control.repeatState),
                    GenericCommandButton.Radio,
                )
            }.collectLatest { buttons -> onUpdateNotification.invoke(buttons) }
        }
        loadJob = Job()""")
replace_range(service,
"                val liked =\n                    songRepository\n                        .getSongById(id)",
"            }\n    }\n\n",
"""                // Do not infer the YouTube like from the offline local favourite.
                EnhancedMediaBridge.youtubeLiked.value = null
                val liked = try {
                    if (id.isBlank()) null
                    else songRepository.getLikeStatus(id).first() == YouTubeLikeStatus.LIKE
                } catch (_: Exception) { null }
                if ((player.currentMediaItem?.mediaId ?: "").removePrefix("Video") == id) {
                    EnhancedMediaBridge.youtubeLiked.value = liked
                    _controlState.value = _controlState.value.copy(isLiked = liked == true)
                }
""")
replace_range(service,
"    override fun toggleLike() {",
"    override fun like(liked: Boolean) {",
"""    override fun toggleLike() {
        // The notification and all visible hearts toggle the remote YouTube Music like.
        // Recheck remote status before mutation; never touch local SongEntity.liked.
        toggleLikeJob?.cancel()
        toggleLikeJob = coroutineScope.launch {
            val id = (player.currentMediaItem?.mediaId ?: "").removePrefix("Video")
            if (id.isBlank()) return@launch
            try {
                val previouslyLiked = songRepository.getLikeStatus(id).first() == YouTubeLikeStatus.LIKE
                val result = if (previouslyLiked) {
                    songRepository.removeFromYouTubeLiked(id).first()
                } else {
                    songRepository.addToYouTubeLiked(id).first()
                }
                if (result == 200) {
                    if ((player.currentMediaItem?.mediaId ?: "").removePrefix("Video") == id) updateNotification()
                } else Logger.e(TAG, "YouTube Music like request failed: " + result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "YouTube Music like request failed: " + e.message)
            }
        }
    }

""")

callback="media/media3/src/main/java/com/maxrave/media3/service/callback/SimpleMediaSessionCallback.kt"
edit(callback,"import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler\n",
     "import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler\nimport com.maxrave.domain.data.player.EnhancedMediaBridge\n")
edit(callback,
"""            MEDIA_CUSTOM_COMMAND.SHUFFLE -> {
                scope.launch {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle)
                }
            }""",
"""            MEDIA_CUSTOM_COMMAND.SHUFFLE -> {
                val inAppCycle = EnhancedMediaBridge.onShuffleClick
                if (inAppCycle != null) inAppCycle() else scope.launch {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle)
                }
            }""")

icons="media/media3/src/main/java/com/maxrave/media3/extension/Media3Ext.kt"
edit(icons,
"""                    if (this.isShuffled) {
                        R.drawable.baseline_shuffle_on_24
                    } else {
                        R.drawable.baseline_shuffle_24
                    },""",
"""                    when {
                        this.isEnhanced -> R.drawable.baseline_enhanced_shuffle_24
                        this.isShuffled -> R.drawable.baseline_shuffle_on_24
                        else -> R.drawable.baseline_shuffle_24
                    },""")
edit(icons,
"""                .setDisplayName(context.getString(R.string.shuffle))
                .setSessionCommand(""",
"""                .setDisplayName(if (this.isEnhanced) "Enhanced 2+1" else context.getString(R.string.shuffle))
                .setSessionCommand(""")

icon=ROOT/"media/media3/src/main/res/drawable/baseline_enhanced_shuffle_24.xml"
icon.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <group android:scaleX="0.7" android:scaleY="0.7"
        android:translateX="0.5" android:translateY="4">
        <path android:fillColor="#FFFFFFFF"
            android:pathData="M17,3h4v4h-2V5h-2c-1.1,0 -2.1,0.5 -2.8,1.4L6.8,17.6C5.8,19.1 4.3,20 2.5,20H2v-2h0.5c1.1,0 2.1,-0.5 2.8,-1.4L12.7,5.4C13.7,3.9 15.2,3 17,3zM21,17v4h-4v-2h2v-2h2zM2,4h0.5c1.8,0 3.3,0.9 4.3,2.4l2.1,3.1 -1.2,1.8 -2.4,-3.7C4.6,6.5 3.6,6 2.5,6H2V4zM13.2,15.2l1.1,-1.7 0.1,0.1C15.1,14.5 16.1,15 17.2,15H19v-2h2v4h-4c-1.8,0 -3.3,-0.9 -3.8,-1.8z"/>
    </group>
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M19,0.6 L20.5,4.3 L23.7,5.7 L20.5,7.1 L19,10.8 L17.5,7.1 L14.3,5.7 L17.5,4.3 Z"/>
</vector>
""")
print("Applied YouTube notification like, three-state shuffle and separated sparkle glyph")
