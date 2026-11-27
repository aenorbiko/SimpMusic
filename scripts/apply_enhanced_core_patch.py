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
    // Source of truth for identifying a recommended track on Android's media card.
    val recommendedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    var onShuffleClick: (() -> Unit)? = null
    // Android service and the UI share the same Koin singleton queue owner.
    // The app process installs this hook; no UI Activity is required for
    // Enhanced state restoration and future 2+1 queue refills.
    var onMediaServiceCreated: (() -> Unit)? = null
}
""")

# A Bluetooth media-button may start only the Media3 service, without an
# Activity. Bootstrap the *existing* SharedViewModel Koin singleton from the
# service, once the media session has been set up. The application's callback
# lazily resolves that singleton, which already owns the Enhanced 2+1 planner,
# saved snapshot and progressive prefetch. No second recommendation engine.
media_service="media/media3/src/main/java/com/maxrave/media3/service/SimpleMediaService.kt"
edit(media_service,
     "import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler\n",
     "import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler\nimport com.maxrave.domain.data.player.EnhancedMediaBridge\n")
edit(media_service,
"""        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }

        val sessionToken = SessionToken(this, ComponentName(this, SimpleMediaService::class.java))""",
"""        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }

        // A headset/car Bluetooth can resume playback with NO visible app UI.
        // Start the same process-wide Enhanced session owner used by screens;
        // its restoration observes the Media3 queue while normal audio
        // resumption remains free to start immediately.
        try {
            EnhancedMediaBridge.onMediaServiceCreated?.invoke()
        } catch (e: Exception) {
            Logger.e("Service", "Enhanced bootstrap failed: ${e.message}")
        }

        val sessionToken = SessionToken(this, ComponentName(this, SimpleMediaService::class.java))""")

service="data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt"
# 2+1 already has its OWN deterministic shuffled ordering. If native Shuffle
# (saved from a previous normal queue) is restored first, Media3 randomly
# reorders the saved 2+1 items before Enhanced can validate the queue, and
# every star/cursor is lost. Disable native Shuffle before the saved playlist
# is reloaded, but ONLY if the saved label and Enhanced snapshot are present.
edit(service,
"""        mayBeRestoreQueue()
        coroutineScope.launch {""",
"""        val pendingEnhancedSnapshot = runBlocking {
            dataStoreManager.getString("enhanced_queue_session_v1").first().orEmpty()
        }
        val pendingQueueName = runBlocking { dataStoreManager.playlistFromSaved.first() }
        if (pendingEnhancedSnapshot.isNotBlank() && pendingQueueName.endsWith(" · Enhanced 2+1")) {
            if (player.shuffleModeEnabled) {
                player.shuffleModeEnabled = false
                _controlState.value = _controlState.value.copy(isShuffle = false)
                Logger.w("EnhancedRestore", "Disabled native Shuffle for persisted Enhanced 2+1 queue")
            }
        }
        mayBeRestoreQueue()
        coroutineScope.launch {""")

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



# Start Radio used to clear and recreate the current item before appending the
# radio queue. Keep the active Media3 item in place so position and pause state
# survive. Ignore a response that arrives after playback has moved to another song.
radio_handlers = [
    "data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt",
    "data/src/jvmMain/kotlin/com/maxrave/data/mediaservice/JvmMediaPlayerHandlerImpl.kt",
]
radio_success_anchor = """                        is Resource.Success if (data != null && data.first.isNotEmpty()) -> {
                            setQueueData("""
radio_success_replacement = """                        is Resource.Success if (data != null && data.first.isNotEmpty()) -> {
                            // The radio request is asynchronous. Do not replace the queue if
                            // playback moved to a different song while it was in flight.
                            val activeVideoId = player.currentMediaItem?.mediaId
                                ?.removePrefix(MERGING_DATA_TYPE.VIDEO)
                            if (activeVideoId != currentSong.videoId) {
                                Logger.w(TAG, "toggleRadio: active track changed; keeping current queue")
                                return@collectLatest
                            }
                            val activeIndex = player.currentMediaItemIndex
                            val itemCount = player.mediaItemCount
                            if (activeIndex !in 0 until itemCount) {
                                Logger.w(TAG, "toggleRadio: no active queue item; keeping current queue")
                                return@collectLatest
                            }
                            setQueueData("""
radio_queue_anchor = """                            clearMediaItems()
                            currentSong.durationSeconds.let {
                                songRepository.updateDurationSeconds(it, currentSong.videoId)
                            }
                            addMediaItem(currentSong.toGenericMediaItem(), playWhenReady = true)
                            loadPlaylistOrAlbum(0)"""
radio_queue_replacement = """                            currentSong.durationSeconds.let {
                                songRepository.updateDurationSeconds(it, currentSong.videoId)
                            }
                            // Retain the current item and its position; discard only old history
                            // and upcoming tracks, then append the radio results.
                            player.removeMediaItems(activeIndex + 1, itemCount)
                            player.removeMediaItems(0, activeIndex)
                            loadPlaylistOrAlbum(0)"""
for radio_handler in radio_handlers:
    edit(radio_handler, radio_success_anchor, radio_success_replacement)
    edit(radio_handler, radio_queue_anchor, radio_queue_replacement)


# Android SystemUI owns the Media3 notification layout. We can still mark a
# recommendation with a one-character prefix in the current Media3 title;
# this does NOT introduce a new notification or disturb the playback queue.
adapter="media/media3/src/main/java/com/maxrave/media3/exoplayer/ExoPlayerAdapter.kt"
edit(adapter,
"""import com.maxrave.domain.data.player.GenericMediaItem
""",
"""import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.EnhancedMediaBridge
""")
edit(adapter,
"""fun GenericMediaItem.toMedia3MediaItem(): MediaItem {
    val builder =
        MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata.toMedia3MediaMetadata())""",
"""fun GenericMediaItem.toMedia3MediaItem(): MediaItem {
    // Media3 exposes the current MediaItem title to Android SystemUI.
    // Decorate only recommended tracks when the player item is first added.
    val ytId = mediaId.removePrefix("Video")
    val isRecommended = ytId in EnhancedMediaBridge.recommendedTrackIds.value
    val notificationMetadata =
        if (isRecommended && !metadata.title.isNullOrBlank()) {
            metadata.copy(
                title = "✦ " + metadata.title,
                // EM SPACE + EN SPACE: visually align the artist under the
                // title text, leaving the sparkle alone in the left margin.
                artist = metadata.artist?.let { "\u2003\u2002" + it },
            )
        } else metadata
    val builder =
        MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(notificationMetadata.toMedia3MediaMetadata())""")
edit(adapter,
"""        metadata = mediaMetadata.toGenericMediaMetadata(),
        customCacheKey = localConfiguration?.customCacheKey,""",
"""        // The sparkle belongs only in the Android notification, not in the
        // canonical song title used by app screens, searches or playback state.
        metadata = mediaMetadata.toGenericMediaMetadata().let { meta ->
            if (meta.title?.startsWith("✦ ") == true) {
                meta.copy(
                    title = meta.title?.removePrefix("✦ "),
                    artist = meta.artist?.removePrefix("\u2003\u2002"),
                )
            } else meta
        },
        customCacheKey = localConfiguration?.customCacheKey,""")

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

# All three Android notification icons now use the same original Shuffle arrows,
# uniformly reduced to 92% just like Compose. Enhanced adds a larger upper-left
# sparkle plus a small four-point sparkle centred at (11,17.8).
drawable_dir = ROOT/"media/media3/src/main/res/drawable"
drawable_dir.joinpath("baseline_simp_shuffle_off_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M14.76,19.36 Q14.374,19.36,14.107,19.093 Q13.84,18.836,13.84,18.44 Q13.84,18.044,14.107,17.787 Q14.374,17.529,14.76,17.52 L16.232,17.52 L13.96,15.248 Q13.684,14.972,13.693,14.594 Q13.702,14.217,13.978,13.932 Q14.254,13.647,14.631,13.656 Q15.008,13.665,15.284,13.932 L17.52,16.186 L17.52,14.76 Q17.52,14.364,17.787,14.107 Q18.054,13.849,18.44,13.84 Q18.826,13.831,19.093,14.107 Q19.36,14.383,19.36,14.76 L19.36,18.44 Q19.36,18.836,19.093,19.093 Q18.826,19.351,18.44,19.36 L14.76,19.36 Z M4.916,19.084 Q4.668,18.836,4.668,18.44 Q4.668,18.044,4.916,17.796 L16.232,6.48 L14.76,6.48 Q14.374,6.48,14.107,6.213 Q13.84,5.956,13.84,5.56 Q13.84,5.164,14.107,4.907 Q14.374,4.649,14.76,4.64 L18.44,4.64 Q18.836,4.64,19.093,4.907 Q19.351,5.174,19.36,5.56 L19.36,9.24 Q19.36,9.626,19.093,9.893 Q18.826,10.16,18.44,10.16 Q18.054,10.16,17.787,9.893 Q17.52,9.626,17.52,9.24 L17.52,7.768 L6.204,19.084 Q5.956,19.342,5.56,19.342 Q5.174,19.342,4.916,19.084 Z M4.898,6.204 Q4.64,5.956,4.64,5.56 Q4.64,5.164,4.898,4.916 Q5.155,4.668,5.523,4.658 Q5.891,4.649,6.186,4.916 L10.05,8.762 Q10.307,9.019,10.316,9.396 Q10.326,9.774,10.05,10.05 Q9.792,10.298,9.406,10.298 Q9.019,10.298,8.762,10.05 L4.898,6.204 Z" />
</vector>
""")
drawable_dir.joinpath("baseline_simp_shuffle_on_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M0,0 L24,0 L24,24 L0,24 Z M14.76,19.36 Q14.374,19.36,14.107,19.093 Q13.84,18.836,13.84,18.44 Q13.84,18.044,14.107,17.787 Q14.374,17.529,14.76,17.52 L16.232,17.52 L13.96,15.248 Q13.684,14.972,13.693,14.594 Q13.702,14.217,13.978,13.932 Q14.254,13.647,14.631,13.656 Q15.008,13.665,15.284,13.932 L17.52,16.186 L17.52,14.76 Q17.52,14.364,17.787,14.107 Q18.054,13.849,18.44,13.84 Q18.826,13.831,19.093,14.107 Q19.36,14.383,19.36,14.76 L19.36,18.44 Q19.36,18.836,19.093,19.093 Q18.826,19.351,18.44,19.36 L14.76,19.36 Z M4.916,19.084 Q4.668,18.836,4.668,18.44 Q4.668,18.044,4.916,17.796 L16.232,6.48 L14.76,6.48 Q14.374,6.48,14.107,6.213 Q13.84,5.956,13.84,5.56 Q13.84,5.164,14.107,4.907 Q14.374,4.649,14.76,4.64 L18.44,4.64 Q18.836,4.64,19.093,4.907 Q19.351,5.174,19.36,5.56 L19.36,9.24 Q19.36,9.626,19.093,9.893 Q18.826,10.16,18.44,10.16 Q18.054,10.16,17.787,9.893 Q17.52,9.626,17.52,9.24 L17.52,7.768 L6.204,19.084 Q5.956,19.342,5.56,19.342 Q5.174,19.342,4.916,19.084 Z M4.898,6.204 Q4.64,5.956,4.64,5.56 Q4.64,5.164,4.898,4.916 Q5.155,4.668,5.523,4.658 Q5.891,4.649,6.186,4.916 L10.05,8.762 Q10.307,9.019,10.316,9.396 Q10.326,9.774,10.05,10.05 Q9.792,10.298,9.406,10.298 Q9.019,10.298,8.762,10.05 L4.898,6.204 Z" />
</vector>
""")
drawable_dir.joinpath("baseline_simp_shuffle_enhanced_24.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M0,0 L24,0 L24,24 L0,24 Z M14.76,19.36 Q14.374,19.36,14.107,19.093 Q13.84,18.836,13.84,18.44 Q13.84,18.044,14.107,17.787 Q14.374,17.529,14.76,17.52 L16.232,17.52 L13.96,15.248 Q13.684,14.972,13.693,14.594 Q13.702,14.217,13.978,13.932 Q14.254,13.647,14.631,13.656 Q15.008,13.665,15.284,13.932 L17.52,16.186 L17.52,14.76 Q17.52,14.364,17.787,14.107 Q18.054,13.849,18.44,13.84 Q18.826,13.831,19.093,14.107 Q19.36,14.383,19.36,14.76 L19.36,18.44 Q19.36,18.836,19.093,19.093 Q18.826,19.351,18.44,19.36 L14.76,19.36 Z M4.916,19.084 Q4.668,18.836,4.668,18.44 Q4.668,18.044,4.916,17.796 L16.232,6.48 L14.76,6.48 Q14.374,6.48,14.107,6.213 Q13.84,5.956,13.84,5.56 Q13.84,5.164,14.107,4.907 Q14.374,4.649,14.76,4.64 L18.44,4.64 Q18.836,4.64,19.093,4.907 Q19.351,5.174,19.36,5.56 L19.36,9.24 Q19.36,9.626,19.093,9.893 Q18.826,10.16,18.44,10.16 Q18.054,10.16,17.787,9.893 Q17.52,9.626,17.52,9.24 L17.52,7.768 L6.204,19.084 Q5.956,19.342,5.56,19.342 Q5.174,19.342,4.916,19.084 Z M4.898,6.204 Q4.64,5.956,4.64,5.56 Q4.64,5.164,4.898,4.916 Q5.155,4.668,5.523,4.658 Q5.891,4.649,6.186,4.916 L10.05,8.762 Q10.307,9.019,10.316,9.396 Q10.326,9.774,10.05,10.05 Q9.792,10.298,9.406,10.298 Q9.019,10.298,8.762,10.05 L4.898,6.204 Z M9.6,0.2 L11.05,2.15 L13.2,3.35 L11.05,4.55 L9.6,6.5 L8.15,4.55 L6,3.35 L8.15,2.15 Z M11,16.48 L11.61,17.3 L12.51,17.8 L11.61,18.3 L11,19.12 L10.39,18.3 L9.49,17.8 L10.39,17.3 Z" />
</vector>
""")
# An Android media button can restore a recommendation while the Enhanced
# ViewModel is still waiting to validate its saved session. The Media3
# notification title is decorated at conversion time, so preseed its star IDs
# from the exact saved queue BEFORE the very first addMediaItem() call.
# This is metadata-only: never replace/seek/reload the playing media item.
enhanced_restore_helper = ROOT/"data/src/commonMain/kotlin/com/maxrave/data/mediaservice/RestoredEnhancedRecommendedIds.kt"
enhanced_restore_helper.parent.mkdir(parents=True, exist_ok=True)
enhanced_restore_helper.write_text("package com.maxrave.data.mediaservice\n\nimport kotlinx.serialization.json.Json\nimport kotlinx.serialization.json.contentOrNull\nimport kotlinx.serialization.json.intOrNull\nimport kotlinx.serialization.json.jsonArray\nimport kotlinx.serialization.json.jsonObject\nimport kotlinx.serialization.json.jsonPrimitive\n\n/**\n * Read the saved Enhanced star IDs BEFORE Media3 converts the restored tracks\n * to MediaItems. That conversion freezes the displayed notification title.\n *\n * This is only a metadata bootstrap: it must not seek, replace a playing item,\n * reset the queue, or trust a playlist label without checking the saved IDs.\n * The app's Enhanced session owner later performs full queue validation.\n */\ninternal fun restoredEnhancedRecommendedIds(\n    snapshotJson: String?,\n    savedPlaylistName: String,\n    savedQueueVideoIds: List<String>,\n    currentVideoId: String,\n): Set<String> {\n    if (snapshotJson.isNullOrBlank() ||\n        !savedPlaylistName.endsWith(\" · Enhanced 2+1\") ||\n        savedQueueVideoIds.isEmpty() ||\n        currentVideoId.isBlank()\n    ) return emptySet()\n\n    return runCatching {\n        val snapshot = Json.parseToJsonElement(snapshotJson).jsonObject\n        if (snapshot[\"schemaVersion\"]?.jsonPrimitive?.intOrNull != 1) return@runCatching emptySet<String>()\n        val sourceName = snapshot[\"sourceName\"]?.jsonPrimitive?.contentOrNull.orEmpty()\n        if (sourceName.isBlank() || savedPlaylistName != sourceName + \" · Enhanced 2+1\") {\n            return@runCatching emptySet<String>()\n        }\n        val expectedQueue = snapshot[\"queueVideoIds\"]?.jsonArray\n            ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()\n        if (currentVideoId !in expectedQueue) return@runCatching emptySet<String>()\n\n        // Saved Media3 queues can be an ordered tail or a shorter prefix of the\n        // Enhanced snapshot. If the current song was inserted at the front by\n        // Media3 recovery, accept that precise case only, like the app restore.\n        fun orderedSlice(actual: List<String>): Boolean {\n            if (actual.isEmpty()) return false\n            val start = expectedQueue.indexOf(actual.first())\n            if (start < 0) return false\n            val overlap = minOf(expectedQueue.size - start, actual.size)\n            if (start > 0 && overlap < 3) return false\n            return expectedQueue.subList(start, start + overlap) == actual.take(overlap)\n        }\n\n        val ordered = orderedSlice(savedQueueVideoIds)\n        val insertedCurrent = savedQueueVideoIds.size > 3 &&\n            savedQueueVideoIds.first() == currentVideoId &&\n            orderedSlice(savedQueueVideoIds.drop(1))\n        if (!ordered && !insertedCurrent) return@runCatching emptySet<String>()\n\n        val recommendedIds = snapshot[\"recommendedVideoIds\"]?.jsonArray\n            ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty().toSet()\n        recommendedIds.intersect(savedQueueVideoIds.toSet())\n    }.getOrDefault(emptySet())\n}\n")
enhanced_restore_test = ROOT/"data/src/jvmTest/kotlin/com/maxrave/data/mediaservice/RestoredEnhancedRecommendedIdsTest.kt"
enhanced_restore_test.parent.mkdir(parents=True, exist_ok=True)
enhanced_restore_test.write_text("package com.maxrave.data.mediaservice\n\nimport kotlin.test.Test\nimport kotlin.test.assertEquals\nimport kotlinx.serialization.json.JsonPrimitive\nimport kotlinx.serialization.json.buildJsonArray\nimport kotlinx.serialization.json.buildJsonObject\nimport kotlinx.serialization.json.put\n\nclass RestoredEnhancedRecommendedIdsTest {\n    private fun snapshot(\n        source: String = \"Liked Music\",\n        queue: List<String> = listOf(\"A\", \"B\", \"R_AB\", \"C\", \"D\", \"R_CD\"),\n        recommended: List<String> = listOf(\"R_AB\", \"R_CD\"),\n    ): String = buildJsonObject {\n        put(\"schemaVersion\", JsonPrimitive(1))\n        put(\"sourceName\", JsonPrimitive(source))\n        put(\"queueVideoIds\", buildJsonArray { queue.forEach { add(JsonPrimitive(it)) } })\n        put(\"recommendedVideoIds\", buildJsonArray { recommended.forEach { add(JsonPrimitive(it)) } })\n    }.toString()\n\n    @Test\n    fun starredCurrentTrackIsKnownBeforeFirstRestoredMediaItem() {\n        assertEquals(setOf(\"R_AB\", \"R_CD\"), restoredEnhancedRecommendedIds(\n            snapshot(), \"Liked Music · Enhanced 2+1\",\n            listOf(\"A\", \"B\", \"R_AB\", \"C\", \"D\", \"R_CD\"), \"R_AB\",\n        ))\n    }\n\n    @Test\n    fun normalPlaylistCannotInheritOldEnhancedStars() {\n        assertEquals(emptySet(), restoredEnhancedRecommendedIds(\n            snapshot(), \"Liked Music\", listOf(\"A\", \"B\", \"R_AB\"), \"R_AB\",\n        ))\n    }\n\n    @Test\n    fun mismatchedSourceNameDoesNotRestoreBadges() {\n        assertEquals(emptySet(), restoredEnhancedRecommendedIds(\n            snapshot(), \"Another Album · Enhanced 2+1\",\n            listOf(\"A\", \"B\", \"R_AB\"), \"R_AB\",\n        ))\n    }\n\n    @Test\n    fun reorderedQueueDoesNotRestoreBadges() {\n        assertEquals(emptySet(), restoredEnhancedRecommendedIds(\n            snapshot(), \"Liked Music · Enhanced 2+1\",\n            listOf(\"B\", \"A\", \"R_AB\", \"C\"), \"R_AB\",\n        ))\n    }\n\n    @Test\n    fun trimmedTailRestoresOnlyActuallyQueuedRecommendationIds() {\n        assertEquals(setOf(\"R_CD\"), restoredEnhancedRecommendedIds(\n            snapshot(), \"Liked Music · Enhanced 2+1\",\n            listOf(\"C\", \"D\", \"R_CD\"), \"R_CD\",\n        ))\n    }\n\n    @Test\n    fun restoredShorterPrefixDoesNotInventMissingRecommendationItems() {\n        assertEquals(setOf(\"R_AB\"), restoredEnhancedRecommendedIds(\n            snapshot(), \"Liked Music · Enhanced 2+1\",\n            listOf(\"A\", \"B\", \"R_AB\"), \"R_AB\",\n        ))\n    }\n\n    @Test\n    fun prependedCurrentRecommendedSongCanStillRestoreItsBadge() {\n        assertEquals(setOf(\"R_AB\", \"R_CD\"), restoredEnhancedRecommendedIds(\n            snapshot(), \"Liked Music · Enhanced 2+1\",\n            listOf(\"R_AB\", \"C\", \"D\", \"R_CD\", \"OTHER\"), \"R_AB\",\n        ))\n    }\n\n    @Test\n    fun malformedSnapshotIsIgnoredWithoutInterruptingPlayback() {\n        assertEquals(emptySet(), restoredEnhancedRecommendedIds(\n            \"{\", \"Liked Music · Enhanced 2+1\",\n            listOf(\"A\", \"B\", \"R_AB\"), \"R_AB\",\n        ))\n    }\n}\n")
edit(service, "            setQueueData(\n                QueueData.Data(\n                    listTracks = listTracks,\n                    firstPlayedTrack = currentPlayingTrack,\n                    playlistId = LOCAL_PLAYLIST_ID_SAVED_QUEUE,\n                    playlistName = dataStoreManager.playlistFromSaved.first(),", "            // Media3 freezes the notification title when addMediaItem() converts\n            // this restored track. Recover stars before that conversion, not\n            // asynchronously in SharedViewModel after Bluetooth playback starts.\n            val restoredPlaylistName = dataStoreManager.playlistFromSaved.first()\n            EnhancedMediaBridge.recommendedTrackIds.value =\n                if (restoredPlaylistName.endsWith(\" · Enhanced 2+1\")) {\n                    restoredEnhancedRecommendedIds(\n                        snapshotJson = dataStoreManager.getString(\"enhanced_queue_session_v1\").first(),\n                        savedPlaylistName = restoredPlaylistName,\n                        savedQueueVideoIds = listTracks.map { it.videoId },\n                        currentVideoId = currentPlayingTrack.videoId,\n                    )\n                } else emptySet()\n            setQueueData(\n                QueueData.Data(\n                    listTracks = listTracks,\n                    firstPlayedTrack = currentPlayingTrack,\n                    playlistId = LOCAL_PLAYLIST_ID_SAVED_QUEUE,\n                    playlistName = restoredPlaylistName,")

# Only Enhanced A/B song-radio requests are anonymous. All other YouTube Music
# operations keep their existing signed-in session and authentication. Guest
# requests exclude Cookie/SAPISIDHASH, X-Goog-Authuser, X-Goog-Pageid and the
# authenticated visitorData. Retain the guest flag across automix recursion.
edit("domain/src/commonMain/kotlin/com/maxrave/domain/repository/SongRepository.kt", "    fun getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint): Flow<Resource<Pair<List<Track>, String?>>>", "    fun getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint, anonymous: Boolean = false, continuation: String? = null): Flow<Resource<Pair<List<Track>, String?>>>")
edit("data/src/commonMain/kotlin/com/maxrave/data/repository/SongRepositoryImpl.kt", "    override fun getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint): Flow<Resource<Pair<List<Track>, String?>>> =", "    override fun getRadioFromEndpoint(endpoint: YouTubeWatchEndpoint, anonymous: Boolean, continuation: String?): Flow<Resource<Pair<List<Track>, String?>>> =")
edit("data/src/commonMain/kotlin/com/maxrave/data/repository/SongRepositoryImpl.kt", "                    .next(endpoint.toWatchEndpoint())", "                    .next(endpoint.toWatchEndpoint(), continuation = continuation, anonymous = anonymous)")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt", "        continuation: String? = null,\n    ): Result<NextResult> =", "        continuation: String? = null,\n        anonymous: Boolean = false,\n    ): Result<NextResult> =")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt", "                        endpoint.params,\n                        continuation,\n                    ).body<NextResponse>()", "                        endpoint.params,\n                        continuation,\n                        anonymous = anonymous,\n                    ).body<NextResponse>()")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt", "                            ?.watchPlaylistEndpoint!!,\n                    ).getOrThrow()", "                            ?.watchPlaylistEndpoint!!,\n                        anonymous = anonymous,\n                    ).getOrThrow()")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt", "        customCookie: String? = null,\n    ) {", "        customCookie: String? = null,\n        anonymousRequest: Boolean = false,\n    ) {")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt", "            append(\"X-Goog-Authuser\", authUser.toString())\n            pageId?.let {\n                append(\"X-Goog-Pageid\", it)\n            }", "            if (!anonymousRequest) {\n                append(\"X-Goog-Authuser\", authUser.toString())\n                pageId?.let {\n                    append(\"X-Goog-Pageid\", it)\n                }\n            }")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt", "            if (setLogin) {\n                val cookie = customCookie ?: this@Ytmusic.cookie", "            if (setLogin && !anonymousRequest) {\n                val cookie = customCookie ?: this@Ytmusic.cookie")
edit("service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt", "        continuation: String? = null,\n    ) = httpClient.post(\"next\") {\n        ytClient(client, setLogin = true)\n        setBody(\n            NextBody(\n                context = client.toContext(locale, visitorData),", "        continuation: String? = null,\n        anonymous: Boolean = false,\n    ) = httpClient.post(\"next\") {\n        ytClient(client, setLogin = !anonymous, anonymousRequest = anonymous)\n        setBody(\n            NextBody(\n                context = client.toContext(locale, if (anonymous) null else visitorData),")

print("Applied YouTube notification like, three-state shuffle and separated sparkle glyph")
