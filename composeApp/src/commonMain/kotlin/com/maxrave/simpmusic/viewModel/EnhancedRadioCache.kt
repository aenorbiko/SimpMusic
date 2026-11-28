package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Process-local anonymous radio metadata only. Reuse a first page only if it
 * has no continuation, or if its exact second page is already cached.
 * Never send an old continuation token to the server. Each batch pins its
 * cached second page, so expiry/eviction cannot split a cached pair.
 */
internal class EnhancedRadioCache<T>(
    private val ttlMs: Long = 20 * 60_000L,
    private val maxEntries: Int = 64,
    private val nowMs: (() -> Long)? = null,
) {
    init { require(ttlMs > 0); require(maxEntries > 0) }

    private data class Entry<T>(
        val first: EnhancedRadioPage<T>,
        val second: EnhancedRadioPage<T>?,
        val fetchedAt: Long,
    )
    private data class Pending<T>(
        val first: EnhancedRadioPage<T>,
        val second: EnhancedRadioPage<T>?,
        val fetchedAt: Long,
    )
    private val started = TimeSource.Monotonic.markNow()
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Entry<T>>()
    private var firstHits = 0L
    private var secondHits = 0L
    private var misses = 0L
    private var evictions = 0L
    private var expired = 0L
    private fun now() = nowMs?.invoke() ?: started.elapsedNow().inWholeMilliseconds
    private fun valid(page: EnhancedRadioPage<T>) = page.status == "OK" && page.tracks.isNotEmpty()
    private fun cached(page: EnhancedRadioPage<T>) =
        page.copy(requestMs = 0, semaphoreWaitMs = 0, fromCache = true)

    private suspend fun lookup(seed: String): Entry<T>? = lock.withLock {
        val entry = entries.remove(seed)
        if (entry == null || now() - entry.fetchedAt >= ttlMs) {
            if (entry != null) expired++
            misses++
            null
        } else {
            entries[seed] = entry // LRU; reads do not refresh TTL.
            firstHits++
            entry
        }
    }

    private suspend fun store(seed: String, first: EnhancedRadioPage<T>, second: EnhancedRadioPage<T>?, fetchedAt: Long) = lock.withLock {
        if (!valid(first) || now() - fetchedAt >= ttlMs) return@withLock
        if (!first.continuation.isNullOrBlank() && (second == null || !valid(second))) return@withLock
        if ((entries[seed]?.fetchedAt ?: Long.MIN_VALUE) > fetchedAt) return@withLock
        entries.remove(seed)
        entries[seed] = Entry(first.copy(tracks = first.tracks.toList()), second?.copy(tracks = second.tracks.toList()), fetchedAt)
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
            evictions++
        }
    }

    inner class Batch {
        private val pendingLock = Mutex()
        private val pending = mutableMapOf<Pair<String, String>, Pending<T>>()

        suspend fun load(seed: String, token: String?, network: suspend () -> EnhancedRadioPage<T>): EnhancedRadioPage<T> {
            if (token == null) {
                val hit = lookup(seed)
                if (hit != null) {
                    hit.first.continuation?.takeIf { it.isNotBlank() }?.let { next ->
                        pendingLock.withLock { pending[seed to next] = Pending(hit.first, hit.second, hit.fetchedAt) }
                    }
                    return cached(hit.first)
                }
                val first = network()
                if (valid(first)) {
                    val fetchedAt = now()
                    val next = first.continuation?.takeIf { it.isNotBlank() }
                    if (next == null) store(seed, first, null, fetchedAt)
                    else pendingLock.withLock { pending[seed to next] = Pending(first, null, fetchedAt) }
                }
                return first
            }
            val previous = pendingLock.withLock { pending[seed to token] }
            if (previous?.second != null) {
                lock.withLock { secondHits++ }
                return cached(previous.second)
            }
            val second = network()
            if (previous != null && valid(second)) store(seed, previous.first, second, previous.fetchedAt)
            return second
        }
    }

    fun newBatch() = Batch()

    suspend fun report(): String = lock.withLock {
        val attempts = firstHits + misses
        val pct = if (attempts == 0L) 0L else firstHits * 1000L / attempts
        buildString {
            appendLine("Radio metadata cache (this process / since reset)")
            appendLine("First-page hits: $firstHits of $attempts (${pct / 10}.${pct % 10}%)")
            appendLine("Continuation hits: $secondHits")
            appendLine("Network requests avoided: ${firstHits + secondHits}")
            appendLine("First-page misses: $misses")
            append("Capacity evictions: $evictions; expired lookups: $expired; capacity: $maxEntries; TTL: ${ttlMs / 60_000} min")
        }
    }

    suspend fun resetCounters() = lock.withLock {
        firstHits = 0; secondHits = 0; misses = 0; evictions = 0; expired = 0
    }
}
