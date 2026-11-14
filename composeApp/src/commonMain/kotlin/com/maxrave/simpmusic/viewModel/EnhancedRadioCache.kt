package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Bounded, in-memory cache of radio *metadata*, never audio bytes.
 * No persistence: cache disappears when the app is terminated or restarted.
 * A short TTL reduces stale personalized results after account changes.
 */
internal class EnhancedRadioCache<T>(
    private val ttlMs: Long = 20 * 60_000L,
    private val maxEntries: Int = 64,
    private val nowMs: (() -> Long)? = null,
) {
    init {
        require(ttlMs > 0)
        require(maxEntries > 0)
    }

    private data class Entry<T>(val tracks: List<T>, val fetchedAtMs: Long)
    private val started = TimeSource.Monotonic.markNow()
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry<T>>()

    private fun now(): Long = nowMs?.invoke() ?: started.elapsedNow().inWholeMilliseconds

    suspend fun get(videoId: String): List<T>? = mutex.withLock {
        val entry = entries[videoId] ?: return@withLock null
        if (now() - entry.fetchedAtMs >= ttlMs) {
            entries.remove(videoId)
            null
        } else entry.tracks
    }

    suspend fun put(videoId: String, tracks: List<T>) {
        // Do not cache failed or empty radio results, so transient failures can retry.
        if (tracks.isEmpty()) return
        mutex.withLock {
            entries.remove(videoId)
            entries[videoId] = Entry(tracks.toList(), now())
            while (entries.size > maxEntries) {
                entries.remove(entries.keys.first())
            }
        }
    }

    suspend fun clear() = mutex.withLock { entries.clear() }
}
