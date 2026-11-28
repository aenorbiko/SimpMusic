package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Process-local anonymous metadata. Store every successful first page immediately.
 * A continuation belongs to its exact first-page snapshot; only that snapshot
 * may be extended. A failed deferred token requests one fresh first page.
 * TTL is measured from the first page, never renewed by hits or extensions.
 */
internal class EnhancedRadioCache<T>(
    private val ttlMs: Long = 60 * 60_000L,
    private val maxEntries: Int = 64,
    private val nowMs: (() -> Long)? = null,
) {
    init { require(ttlMs > 0); require(maxEntries > 0) }

    private data class Entry<T>(
        val first: EnhancedRadioPage<T>,
        val second: EnhancedRadioPage<T>?,
        val fetchedAt: Long,
    )
    private data class Pending<T>(val entry: Entry<T>, val reused: Boolean)
    private val started = TimeSource.Monotonic.markNow()
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Entry<T>>()
    private var firstHits = 0L
    private var secondHits = 0L
    private var misses = 0L
    private var evictions = 0L
    private var expired = 0L
    private var expiryPruned = 0L
    private var expiryPrunedTrackRecords = 0L
    private var deferredSuccess = 0L
    private var deferredFailure = 0L
    private var recoveryRequests = 0L
    private fun now() = nowMs?.invoke() ?: started.elapsedNow().inWholeMilliseconds
    private fun valid(page: EnhancedRadioPage<T>) = page.status == "OK" && page.tracks.isNotEmpty()
    private fun cached(page: EnhancedRadioPage<T>) =
        page.copy(requestMs = 0, semaphoreWaitMs = 0, fromCache = true)

    // Called only with lock held at existing cache/report events; no timer.
    // The batch's pinned snapshot is intentionally independent of the resident map.
    private fun pruneExpiredLocked(at: Long) {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (at - entry.fetchedAt >= ttlMs) {
                iterator.remove()
                expiryPruned++
                expiryPrunedTrackRecords += entry.first.tracks.size.toLong() + (entry.second?.tracks?.size ?: 0)
            }
        }
    }

    private suspend fun lookup(seed: String): Entry<T>? = lock.withLock {
        val at = now()
        // Retain the direct expired-lookup counter, separately from unrelated expiry pruning.
        val entry = entries.remove(seed)
        pruneExpiredLocked(at)
        if (entry == null || at - entry.fetchedAt >= ttlMs) {
            if (entry != null) expired++
            misses++
            null
        } else {
            entries[seed] = entry
            firstHits++
            entry
        }
    }

    private suspend fun storeFirst(seed: String, page: EnhancedRadioPage<T>): Entry<T> = lock.withLock {
        val at = now()
        pruneExpiredLocked(at)
        val entry = Entry(page.copy(tracks = page.tracks.toList()), null, at)
        entries.remove(seed)
        entries[seed] = entry
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
            evictions++
        }
        entry
    }

    private suspend fun storeSecond(seed: String, previous: Entry<T>, second: EnhancedRadioPage<T>) = lock.withLock {
        val at = now()
        pruneExpiredLocked(at)
        val current = entries[seed]
        // No late extension may resurrect an expired/evicted or replaced snapshot.
        if (current?.first !== previous.first || at - previous.fetchedAt >= ttlMs) return@withLock
        entries[seed] = previous.copy(second = second.copy(tracks = second.tracks.toList()))
    }

    inner class Batch {
        private val pendingLock = Mutex()
        private val pending = mutableMapOf<Pair<String, String>, Pending<T>>()

        private suspend fun remember(seed: String, entry: Entry<T>, reused: Boolean) {
            entry.first.continuation?.takeIf { it.isNotBlank() }?.let { token ->
                pendingLock.withLock { pending[seed to token] = Pending(entry, reused) }
            }
        }

        suspend fun load(seed: String, token: String?, network: suspend () -> EnhancedRadioPage<T>): EnhancedRadioPage<T> {
            if (token == null) {
                val hit = lookup(seed)
                if (hit != null) {
                    remember(seed, hit, true)
                    return cached(hit.first)
                }
                val first = network()
                if (valid(first)) remember(seed, storeFirst(seed, first), false)
                return first
            }
            val previous = pendingLock.withLock { pending[seed to token] }
            val pinned = previous?.entry?.second
            if (pinned != null) {
                lock.withLock {
                    pruneExpiredLocked(now())
                    secondHits++
                }
                return cached(pinned)
            }
            val second = network()
            lock.withLock { pruneExpiredLocked(now()) }
            if (previous == null) return second
            if (previous.reused) {
                lock.withLock {
                    if (valid(second)) deferredSuccess++
                    else {
                        deferredFailure++
                        val current = entries[seed]
                        if (current?.first === previous.entry.first && current.second == null) entries.remove(seed)
                    }
                }
            }
            if (valid(second)) {
                storeSecond(seed, previous.entry, second)
                pendingLock.withLock {
                    pending[seed to token] = previous.copy(entry = previous.entry.copy(second = second))
                }
            }
            return if (previous.reused && !valid(second)) second.copy(retryFirst = true) else second
        }

        /** Called only by the pair scheduler after a failed deferred continuation. */
        suspend fun refreshFirst(seed: String, network: suspend () -> EnhancedRadioPage<T>): EnhancedRadioPage<T> {
            lock.withLock {
                pruneExpiredLocked(now())
                recoveryRequests++
            }
            val first = network()
            if (valid(first)) remember(seed, storeFirst(seed, first), false)
            return first
        }
    }

    fun newBatch() = Batch()

    suspend fun report(): String = lock.withLock {
        val at = now()
        pruneExpiredLocked(at)
        val attempts = firstHits + misses
        val pct = if (attempts == 0L) 0L else firstHits * 1000L / attempts
        buildString {
            appendLine("Radio metadata cache (this process / since reset)")
            appendLine("First-page hits: $firstHits of $attempts (${pct / 10}.${pct % 10}%)")
            appendLine("Continuation hits: $secondHits")
            appendLine("Cache-served pages (gross avoided requests): ${firstHits + secondHits}")
            appendLine("First-page misses: $misses")
            appendLine("Deferred continuation requests: ${deferredSuccess + deferredFailure}; successful: $deferredSuccess; failed or empty: $deferredFailure")
            appendLine("Recovery first-page requests: $recoveryRequests")
            appendLine("Capacity evictions: $evictions; expired lookups: $expired; capacity: $maxEntries; TTL: ${ttlMs / 60_000} min")
            appendLine("Expired entries pruned: $expiryPruned; track records removed: $expiryPrunedTrackRecords (excludes direct expired lookups)")
            append("Resident cache entries: ${entries.size}; live: ${entries.size}; expired resident: 0; stored track records: ${entries.values.sumOf { it.first.tracks.size + (it.second?.tracks?.size ?: 0) }}")
        }
    }

    suspend fun resetCounters() = lock.withLock {
        pruneExpiredLocked(now())
        firstHits = 0; secondHits = 0; misses = 0; evictions = 0; expired = 0
        expiryPruned = 0; expiryPrunedTrackRecords = 0
        deferredSuccess = 0; deferredFailure = 0; recoveryRequests = 0
    }
}
