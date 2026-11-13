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

# All notification glyphs use the identical new arrow geometry as Compose.
# Only Enhanced adds two separate sparkle cutouts; regular Shuffle still has
# ample space above and below. Active icons both use a filled square background.
drawable_dir = ROOT/"media/media3/src/main/res/drawable"
drawable_dir.joinpath("baseline_simp_shuffle_off_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M21.15,8.9 16.95,6.55 16.95,7.94 15.44,8 14.06,8.48 12.59,9.45 10.77,11.06 8.6,9.21 6.75,8.2 5.34,7.94 2.73,8.01 2.36,8.29 2.14,8.81 2.25,9.35 2.57,9.7 3.01,9.86 5.49,9.87 6.31,10.09 7.75,10.96 9.39,12.4 7.75,13.84 6.31,14.71 5.49,14.93 2.65,15.05 2.21,15.53 2.21,16.27 2.57,16.7 3.01,16.86 5.34,16.86 6.41,16.71 8.33,15.79 10.77,13.74 12.59,15.35 14.06,16.32 15.44,16.8 16.95,16.86 16.95,18.25 21.15,15.9 16.95,13.55 16.95,14.94 15.37,14.8 14.03,14.03 12.16,12.4 14.03,10.77 15.37,10 16.95,9.86 16.95,11.25Z" />
</vector>
""")
drawable_dir.joinpath("baseline_simp_shuffle_on_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M1,1 L23,1 L23,23 L1,23 Z M21.15,8.9 16.95,6.55 16.95,7.94 15.44,8 14.06,8.48 12.59,9.45 10.77,11.06 8.6,9.21 6.75,8.2 5.34,7.94 2.73,8.01 2.36,8.29 2.14,8.81 2.25,9.35 2.57,9.7 3.01,9.86 5.49,9.87 6.31,10.09 7.75,10.96 9.39,12.4 7.75,13.84 6.31,14.71 5.49,14.93 2.65,15.05 2.21,15.53 2.21,16.27 2.57,16.7 3.01,16.86 5.34,16.86 6.41,16.71 8.33,15.79 10.77,13.74 12.59,15.35 14.06,16.32 15.44,16.8 16.95,16.86 16.95,18.25 21.15,15.9 16.95,13.55 16.95,14.94 15.37,14.8 14.03,14.03 12.16,12.4 14.03,10.77 15.37,10 16.95,9.86 16.95,11.25Z" />
</vector>
""")
drawable_dir.joinpath("baseline_simp_shuffle_enhanced_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M1,1 L23,1 L23,23 L1,23 Z M21.15,8.9 16.95,6.55 16.95,7.94 15.44,8 14.06,8.48 12.59,9.45 10.77,11.06 8.6,9.21 6.75,8.2 5.34,7.94 2.73,8.01 2.36,8.29 2.14,8.81 2.25,9.35 2.57,9.7 3.01,9.86 5.49,9.87 6.31,10.09 7.75,10.96 9.39,12.4 7.75,13.84 6.31,14.71 5.49,14.93 2.65,15.05 2.21,15.53 2.21,16.27 2.57,16.7 3.01,16.86 5.34,16.86 6.41,16.71 8.33,15.79 10.77,13.74 12.59,15.35 14.06,16.32 15.44,16.8 16.95,16.86 16.95,18.25 21.15,15.9 16.95,13.55 16.95,14.94 15.37,14.8 14.03,14.03 12.16,12.4 14.03,10.77 15.37,10 16.95,9.86 16.95,11.25Z M12,1.57 12.8,3.15 14.2,3.95 12.8,4.75 12,6.33 11.2,4.75 9.8,3.95 11.2,3.15Z M12,17.67 12.8,19.25 14.2,20.05 12.8,20.85 12,22.43 11.2,20.85 9.8,20.05 11.2,19.25Z" />
</vector>
""")
print("Applied YouTube notification like, three-state shuffle and separated sparkle glyph")
