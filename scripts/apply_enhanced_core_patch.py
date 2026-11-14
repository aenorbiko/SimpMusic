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
    // Keep a confirmed remote like stable while older status requests finish.
    val youtubeLikeVideoId = MutableStateFlow<String?>(null)
    val likeMutationRevision = MutableStateFlow(0L)
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
                )
            }.collectLatest { buttons -> onUpdateNotification.invoke(buttons) }
        }
        loadJob = Job()""")
replace_range(service,
"                val liked =\n                    songRepository\n                        .getSongById(id)",
"            }\n    }\n\n",
"""                // Do not infer the YouTube like from the offline local favourite.
                if (EnhancedMediaBridge.youtubeLikeVideoId.value != id) {
                    EnhancedMediaBridge.youtubeLikeVideoId.value = id
                    EnhancedMediaBridge.youtubeLiked.value = null
                }
                val revisionAtStart = EnhancedMediaBridge.likeMutationRevision.value
                if (EnhancedMediaBridge.youtubeLiked.value == null) {
                    val liked = try {
                        if (id.isBlank()) null
                        else songRepository.getLikeStatus(id).first() == YouTubeLikeStatus.LIKE
                    } catch (_: Exception) { null }
                    if ((player.currentMediaItem?.mediaId ?: "").removePrefix("Video") == id &&
                        EnhancedMediaBridge.likeMutationRevision.value == revisionAtStart
                    ) {
                        EnhancedMediaBridge.youtubeLiked.value = liked
                        _controlState.value = _controlState.value.copy(isLiked = liked == true)
                    }
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
                    if ((player.currentMediaItem?.mediaId ?: "").removePrefix("Video") == id) {
                        EnhancedMediaBridge.likeMutationRevision.value++
                        EnhancedMediaBridge.youtubeLikeVideoId.value = id
                        EnhancedMediaBridge.youtubeLiked.value = !previouslyLiked
                        _controlState.value = _controlState.value.copy(isLiked = !previouslyLiked)
                    }
                } else Logger.e(TAG, "YouTube Music like request failed: " + result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "YouTube Music like request failed: " + e.message)
            }
        }
    }

""")

# A user pressing Next means leave Repeat One and continue the playlist.
# Natural end-of-track playback still repeats the current song.
edit(service,
"""            PlayerEvent.Next -> {
                resetCrossfade()
                player.seekToNext()
            }""",
"""            PlayerEvent.Next -> {
                resetCrossfade()
                if (player.repeatMode == PlayerConstants.REPEAT_MODE_ONE) {
                    player.repeatMode = PlayerConstants.REPEAT_MODE_OFF
                    _controlState.value = _controlState.value.copy(repeatState = RepeatState.None)
                }
                player.seekToNext()
            }""")

callback="media/media3/src/main/java/com/maxrave/media3/service/callback/SimpleMediaSessionCallback.kt"
edit(callback,"import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler\n",
     "import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler\nimport com.maxrave.domain.data.player.EnhancedMediaBridge\n")
edit(callback,
"""                COMMAND_SEEK_TO_NEXT -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                }""",
"""                COMMAND_SEEK_TO_NEXT -> {
                    // Android notification, Bluetooth and other Media3 controllers
                    // also cancel Repeat One on an explicit Next press.
                    if (session.player.repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
                        session.player.repeatMode = ExoPlayer.REPEAT_MODE_OFF
                    }
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                }""")
edit(callback,
"""            MEDIA_CUSTOM_COMMAND.REPEAT -> {
                session.player.repeatMode =
                    when (session.player.repeatMode) {
                        ExoPlayer.REPEAT_MODE_OFF -> ExoPlayer.REPEAT_MODE_ONE
                        ExoPlayer.REPEAT_MODE_ONE -> ExoPlayer.REPEAT_MODE_ALL
                        ExoPlayer.REPEAT_MODE_ALL -> ExoPlayer.REPEAT_MODE_OFF
                        else -> ExoPlayer.REPEAT_MODE_OFF
                    }
            }""",
"""            MEDIA_CUSTOM_COMMAND.REPEAT -> {
                // Share the Off -> All -> One -> Off order used by the full player.
                scope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat) }
            }""")
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
# Repeat stays available in the full player, but must NOT occupy Android 13
# SLOT_BACK: it hides the system Previous button. Media notification uses only
# built-in Previous/Play-Next plus custom Shuffle and YouTube Like.
edit(icons,
"""                    if (this.isShuffled) {
                        R.drawable.baseline_shuffle_on_24
                    } else {
                        R.drawable.baseline_shuffle_24
                    },""",
"""                    when {
                        this.isEnhanced -> R.drawable.baseline_simp_shuffle_enhanced_24
                        this.isShuffled -> R.drawable.baseline_simp_shuffle_on_24
                        else -> R.drawable.baseline_simp_shuffle_off_24
                    },""")
edit(icons,
"""                .setDisplayName(context.getString(R.string.shuffle))
                .setSessionCommand(""",
"""                .setDisplayName(if (this.isEnhanced) "Enhanced 2+1" else context.getString(R.string.shuffle))
                .setSessionCommand(""")

# Native icons match full-size original SimpMusic arrows (no squeezing).
# Enhanced adds one small four-point star above-left and a dot below.
drawable_dir = ROOT/"media/media3/src/main/res/drawable"
drawable_dir.joinpath("baseline_simp_shuffle_off_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M15,20 q-0.42,0,-0.71,-0.29 Q14,19.43,14,19 t0.29,-0.71 T15,18 h1.6 L14.13,15.53 q-0.3,-0.3,-0.29,-0.71 T14.15,14.1 t0.71,-0.3 t0.71,0.3 L18,16.55 V15 q0,-0.43,0.29,-0.71 T19,14 t0.71,0.29 T20,15 v4 q0,0.43,-0.29,0.71 T19,20 L15 Z M4.3,19.7 Q4.03,19.43,4.03,19 T4.3,18.3 L16.6,6 L15 Q14.58,6,14.29,5.71 Q14,5.43,14,5 T14.29,4.29 T15,4 h4 q0.43,0,0.71,0.29 T20,5 V9 q0,0.42,-0.29,0.71 T19,10 T18.29,9.71 T18,9 V7.4 L5.7,19.7 Q5.43,19.98,5,19.98 q-0.42,0,-0.7,-0.28 Z M4.28,5.7 Q4,5.43,4,5 T4.28,4.3 T4.96,4.02 T5.68,4.3 l4.2,4.18 q0.28,0.28,0.29,0.69 T9.88,9.88 Q9.6,10.15,9.18,10.15 T8.48,9.88 L4.28,5.7 Z" />
</vector>
""")
drawable_dir.joinpath("baseline_simp_shuffle_on_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M0,0 L24,0 L24,24 L0,24 Z M15,20 q-0.42,0,-0.71,-0.29 Q14,19.43,14,19 t0.29,-0.71 T15,18 h1.6 L14.13,15.53 q-0.3,-0.3,-0.29,-0.71 T14.15,14.1 t0.71,-0.3 t0.71,0.3 L18,16.55 V15 q0,-0.43,0.29,-0.71 T19,14 t0.71,0.29 T20,15 v4 q0,0.43,-0.29,0.71 T19,20 L15 Z M4.3,19.7 Q4.03,19.43,4.03,19 T4.3,18.3 L16.6,6 L15 Q14.58,6,14.29,5.71 Q14,5.43,14,5 T14.29,4.29 T15,4 h4 q0.43,0,0.71,0.29 T20,5 V9 q0,0.42,-0.29,0.71 T19,10 T18.29,9.71 T18,9 V7.4 L5.7,19.7 Q5.43,19.98,5,19.98 q-0.42,0,-0.7,-0.28 Z M4.28,5.7 Q4,5.43,4,5 T4.28,4.3 T4.96,4.02 T5.68,4.3 l4.2,4.18 q0.28,0.28,0.29,0.69 T9.88,9.88 Q9.6,10.15,9.18,10.15 T8.48,9.88 L4.28,5.7 Z" />
</vector>
""")
drawable_dir.joinpath("baseline_simp_shuffle_enhanced_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M0,0 L24,0 L24,24 L0,24 Z M15,20 q-0.42,0,-0.71,-0.29 Q14,19.43,14,19 t0.29,-0.71 T15,18 h1.6 L14.13,15.53 q-0.3,-0.3,-0.29,-0.71 T14.15,14.1 t0.71,-0.3 t0.71,0.3 L18,16.55 V15 q0,-0.43,0.29,-0.71 T19,14 t0.71,0.29 T20,15 v4 q0,0.43,-0.29,0.71 T19,20 L15 Z M4.3,19.7 Q4.03,19.43,4.03,19 T4.3,18.3 L16.6,6 L15 Q14.58,6,14.29,5.71 Q14,5.43,14,5 T14.29,4.29 T15,4 h4 q0.43,0,0.71,0.29 T20,5 V9 q0,0.42,-0.29,0.71 T19,10 T18.29,9.71 T18,9 V7.4 L5.7,19.7 Q5.43,19.98,5,19.98 q-0.42,0,-0.7,-0.28 Z M4.28,5.7 Q4,5.43,4,5 T4.28,4.3 T4.96,4.02 T5.68,4.3 l4.2,4.18 q0.28,0.28,0.29,0.69 T9.88,9.88 Q9.6,10.15,9.18,10.15 T8.48,9.88 L4.28,5.7 Z M9.5,0.45 L10.32,2.25 L12.1,3.05 L10.32,3.85 L9.5,5.65 L8.68,3.85 L6.9,3.05 L8.68,2.25 Z M9.45,21.05 Q10.25,21.05,10.25,21.85 Q10.25,22.65,9.45,22.65 Q8.65,22.65,8.65,21.85 Q8.65,21.05,9.45,21.05 Z" />
</vector>
""")
print("Applied YouTube notification like, three-state shuffle and separated sparkle glyph")
