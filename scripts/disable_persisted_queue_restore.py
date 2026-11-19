#!/usr/bin/env python3
"""Disable saved playback queue resurrection in this lab build only.

This patch is applied to the *temporary* pinned core checkout by CI, after
apply_enhanced_core_patch.py. The main branch and the upstream core are untouched.

While a Media3 service process lives, the live player queue and Enhanced 2+1
keep working. After the whole process dies (reboot / Force stop / OS kill),
a new process starts with no queue and cannot resume it via media buttons.
"""
from pathlib import Path

service = Path("core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt")
source = service.read_text()

old_startup = """    override fun mayBeRestoreQueue() {
        coroutineScope.launch { restoreSavedQueue(playWhenReady = false) }
    }
"""
new_startup = """    override fun mayBeRestoreQueue() {
        // Ephemeral playback lab: do not restore an old queue on process start.
    }
"""
old_resumption = """    override suspend fun restoreQueueAndPlay(): Boolean {
        playRequestedDuringRestore = true
        // A restore that is already past its addMediaItem has the saved track in the player, so
        // the request is served right here instead of queueing behind restoreQueueMutex. That
        // restore is sitting on loadJob.join(), and the load it joins paces itself in chunks and
        // can reach for the network, so waiting for the lock would put the whole queue in front
        // of the first note - and in front of the BUFFERING/READY that lets Media3 call
        // startForeground() before the system's deadline. Falling through is still correct when
        // the player has not been primed yet: the restore has not read the flag either, so it
        // starts the track playing itself.
        if (restoreHasPrimedPlayer && player.mediaItemCount > 0) {
            requestPlayWhenReady()
            return true
        }
        return restoreSavedQueue(playWhenReady = true)
    }
"""
new_resumption = """    override suspend fun restoreQueueAndPlay(): Boolean {
        // Do not resurrect the saved queue via MediaSession/Bluetooth resumption.
        // Normal Play/Pause for an active session still uses the live Media3 player.
        return false
    }
"""

for name, old, new in [
    ("startup recovery", old_startup, new_startup),
    ("media-session playback resumption", old_resumption, new_resumption),
]:
    matches = source.count(old)
    if matches != 1:
        raise SystemExit(f"{name}: expected exactly one matching anchor, found {matches}")
    source = source.replace(old, new)

service.write_text(source)
print("Ephemeral Playback Lab: disabled persisted-queue startup and media-session recovery.")
