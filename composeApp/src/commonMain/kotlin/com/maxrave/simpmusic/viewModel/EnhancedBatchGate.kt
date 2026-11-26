package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A single owner for Enhanced queue work, from generation through commit.
 * The worker's radio scheduler still runs its A/B requests in parallel.
 * Revalidate a queued caller after acquiring the lock: an earlier worker
 * may already have advanced the source cursor or the user may have switched
 * sessions. The lock is released on cancellation/failure as well as success.
 */
internal class EnhancedBatchGate {
    private val mutex = Mutex()

    suspend fun runIfCurrent(isCurrent: () -> Boolean, work: suspend () -> Unit) {
        mutex.withLock {
            if (isCurrent()) work()
        }
    }
}
