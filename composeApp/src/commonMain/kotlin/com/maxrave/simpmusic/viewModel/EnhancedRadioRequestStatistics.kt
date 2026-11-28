package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.time.TimeSource

internal const val ENHANCED_RADIO_STATISTICS_KEY = "enhanced_radio_statistics_v1"
private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS
private const val MAX_RECENT_IDS = 4096

@Serializable
internal data class EnhancedRadioStatisticsState(
    val startedAtMs: Long = 0,
    val lastEventAtMs: Long = 0,
    val firstPageRequests: Long = 0,
    val successfulFirstPages: Long = 0,
    val continuationRequests: Long = 0,
    val repeat20Minutes: Long = 0,
    val repeat1Hour: Long = 0,
    val repeat24Hours: Long = 0,
    val lastSuccessfulFirstPage: Map<String, Long> = emptyMap(),
    val historyEvictions: Long = 0,
    val clockResets: Long = 0,
    val resetAtMs: Long = 0,
    val fullTrack: EnhancedFullTrackStatistics = EnhancedFullTrackStatistics(),
    val lastObservedAtMs: Long = 0,
    val elapsedClockCorrectionMs: Long = 0,
    val pairsDecided: Long = 0,
    val missingReasons: Map<String, Long> = emptyMap(),
) {
    fun asText(nowMs: Long): String = buildString {
        fun percent(n: Long): String {
            val tenths = if (firstPageRequests == 0L) 0L else kotlin.math.round(n * 1000.0 / firstPageRequests).toLong()
            return "${tenths / 10}.${tenths % 10}%"
        }
        val minutes = if (startedAtMs == 0L) 0L else ((maxOf(nowMs, lastObservedAtMs) - startedAtMs + elapsedClockCorrectionMs).coerceAtLeast(0) / MINUTE_MS)
        appendLine("Enhanced statistics")
        appendLine("Observation started (UTC): ${if (startedAtMs == 0L) "—" else Instant.fromEpochMilliseconds(startedAtMs).toString()}")
        appendLine("Observation period: ${minutes / 1440} d ${minutes % 1440 / 60} h ${minutes % 60} min")
        appendLine("First-page radio requests: $firstPageRequests")
        appendLine("Successful non-empty first pages: $successfulFirstPages")
        appendLine("Failed or empty first pages: ${firstPageRequests - successfulFirstPages}")
        appendLine("Continuation page requests: $continuationRequests")
        appendLine("Total completed page requests: ${firstPageRequests + continuationRequests}")
        appendLine("Repeat within 20 minutes: $repeat20Minutes (${percent(repeat20Minutes)})")
        appendLine("Repeat within 1 hour: $repeat1Hour (${percent(repeat1Hour)})")
        appendLine("Repeat within 24 hours: $repeat24Hours (${percent(repeat24Hours)})")
        appendLine("History capacity evictions: $historyEvictions; clock resets: $clockResets")
        appendLine("Repeat = previous successful non-empty first page for the same seed. Windows are cumulative, not additive.")
        appendLine("Repeat windows describe network requests only; cache hits are reported separately when enabled. Cancelled requests not counted.")
        appendLine("Local statistics only; no song IDs, titles or account data in this report.")
        appendLine(fullTrack.asText())
        appendLine("Persistent recommendation statistics (since reset / since supported)")
        val missing = missingReasons.values.sum()
        appendLine("Pairs decided: $pairsDecided; selected: ${pairsDecided - missing}; missing: $missing")
        if (missingReasons.isEmpty()) append("Missing reasons: None")
        else missingReasons.toSortedMap().forEach { (reason, count) -> appendLine("$reason: $count") }
    }
}

@Serializable
internal data class EnhancedFullTrackStatistics(
    val shortRecommendations: Long = 0,
    val replaced: Long = 0,
    val noMatch: Long = 0,
    val timeout: Long = 0,
    val error: Long = 0,
    val missingMetadata: Long = 0,
    val searchTotalMs: Long = 0,
    val searchMaxMs: Long = 0,
    val requestCount: Long = 0,
    val requestTotalMs: Long = 0,
    val requestMaxMs: Long = 0,
) {
    fun add(d: FullTrackDiagnostic): EnhancedFullTrackStatistics {
        val attempted = d.outcome != FullTrackOutcome.MISSING_METADATA
        val request = d.requestMs
        return copy(
            shortRecommendations = shortRecommendations + 1,
            replaced = replaced + if (d.outcome == FullTrackOutcome.REPLACED) 1 else 0,
            noMatch = noMatch + if (d.outcome == FullTrackOutcome.NO_MATCH) 1 else 0,
            timeout = timeout + if (d.outcome == FullTrackOutcome.TIMEOUT) 1 else 0,
            error = error + if (d.outcome == FullTrackOutcome.ERROR) 1 else 0,
            missingMetadata = missingMetadata + if (attempted) 0 else 1,
            searchTotalMs = searchTotalMs + if (attempted) d.elapsedMs else 0,
            searchMaxMs = if (attempted) maxOf(searchMaxMs, d.elapsedMs) else searchMaxMs,
            requestCount = requestCount + if (request != null) 1 else 0,
            requestTotalMs = requestTotalMs + (request ?: 0),
            requestMaxMs = maxOf(requestMaxMs, request ?: 0),
        )
    }
    fun asText(): String = buildString {
        val attempts = shortRecommendations - missingMetadata
        appendLine("Full Track statistics")
        appendLine("Short recommendations: $shortRecommendations")
        appendLine("Search attempts: $attempts")
        appendLine("Replaced: $replaced")
        appendLine("No suitable match: $noMatch")
        appendLine("Timeouts: $timeout")
        appendLine("Errors: $error")
        appendLine("Skipped (missing metadata): $missingMetadata")
        appendLine("Search incl. slot wait average/max: ${if (attempts == 0L) 0L else searchTotalMs / attempts} / $searchMaxMs ms")
        append("Search request average/max ($requestCount started): ${if (requestCount == 0L) 0L else requestTotalMs / requestCount} / $requestMaxMs ms")
    }
}

/** Persistent local aggregate counters, never cached tracks or continuation tokens.
 * Serialize read/modify/write, bound recent seed IDs to 24 hours and 4096 entries.
 * Record completed requests asynchronously from the playback path.
 */
internal class EnhancedRadioRequestStatistics(
    private val read: suspend () -> String?,
    private val write: suspend (String) -> Unit,
    private val nowMs: () -> Long,
    private val monotonicMs: () -> Long = run {
        val start = TimeSource.Monotonic.markNow()
        val clock: () -> Long = { start.elapsedNow().inWholeMilliseconds }
        clock
    },
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var unrecorded = 0L
    private var state: EnhancedRadioStatisticsState? = null
    private var pending = 0L
    private var failedWrites = 0L
    private var lastSavedMs = monotonicMs()

    private suspend fun load(): EnhancedRadioStatisticsState {
        state?.let { return it }
        val value = read()
        val decoded = if (value.isNullOrBlank()) EnhancedRadioStatisticsState()
            else json.decodeFromString<EnhancedRadioStatisticsState>(value)
        state = decoded
        return decoded
    }

    // All calls under mutex. Keep the current aggregate and pending count on
    // failure; retry on the next event or explicit flush. Never replay events.
    private suspend fun flushLocked(): Boolean {
        if (pending == 0L) return true
        val current = state ?: return false
        return try {
            write(json.encodeToString(current))
            pending = 0
            lastSavedMs = monotonicMs()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failedWrites++
            false
        }
    }

    private suspend fun accept(next: EnhancedRadioStatisticsState) {
        state = next
        pending++
        // Count all accepted events, including Full Track and pair decisions,
        // so the unsaved-event bound covers more than radio requests alone.
        if (pending >= 8 || monotonicMs() - lastSavedMs >= 5000) flushLocked()
    }

    suspend fun flush(): Boolean = mutex.withLock { flushLocked() }

    suspend fun record(videoId: String, continuation: Boolean, success: Boolean, completedAtMs: Long) {
        mutex.withLock {
            try {
                val old = load()
                // Observe the wall clock under the lock, independently of the
                // asynchronously delivered completion timestamps.
                val observedNow = nowMs().coerceAtLeast(1L)
                val previousObservation = old.lastObservedAtMs.takeIf { it > 0 } ?: old.lastEventAtMs
                val clockBack = previousObservation > observedNow
                if (!clockBack && completedAtMs < old.resetAtMs && observedNow >= old.resetAtMs) return@withLock
                val completed = completedAtMs.coerceIn(1L, observedNow)
                // Late writes cannot rewind the history watermark. On a genuine
                // rollback start a fresh repeat window; keep aggregate counters.
                val now = if (clockBack) observedNow else maxOf(old.lastEventAtMs, completed)
                val recent = if (clockBack) mutableMapOf() else old.lastSuccessfulFirstPage
                    .filterValues { it <= now && now - it <= DAY_MS }.toMutableMap()
                val age = recent[videoId]?.let { now - it }
                val repeated = !continuation && age != null
                if (!continuation && success) recent[videoId] = now
                val excess = (recent.size - MAX_RECENT_IDS).coerceAtLeast(0)
                if (excess > 0) recent.entries.sortedBy { it.value }.take(excess).forEach { recent.remove(it.key) }
                val next = old.copy(
                    startedAtMs = when {
                        old.startedAtMs == 0L -> completed
                        !clockBack && old.clockResets == 0L && old.resetAtMs == 0L -> minOf(old.startedAtMs, completed)
                        else -> old.startedAtMs
                    },
                    lastEventAtMs = now,
                    lastObservedAtMs = observedNow,
                    elapsedClockCorrectionMs = old.elapsedClockCorrectionMs + if (clockBack) previousObservation - observedNow else 0,
                    firstPageRequests = old.firstPageRequests + if (continuation) 0 else 1,
                    successfulFirstPages = old.successfulFirstPages + if (!continuation && success) 1 else 0,
                    continuationRequests = old.continuationRequests + if (continuation) 1 else 0,
                    repeat20Minutes = old.repeat20Minutes + if (repeated && age!! < 20 * MINUTE_MS) 1 else 0,
                    repeat1Hour = old.repeat1Hour + if (repeated && age!! < 60 * MINUTE_MS) 1 else 0,
                    repeat24Hours = old.repeat24Hours + if (repeated && age!! < DAY_MS) 1 else 0,
                    lastSuccessfulFirstPage = recent,
                    historyEvictions = old.historyEvictions + excess,
                    clockResets = old.clockResets + if (clockBack) 1 else 0,
                    resetAtMs = if (clockBack) 0 else old.resetAtMs,
                )
                accept(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A statistics failure must not stop playback or erase previous history.
                unrecorded++
            }
        }
    }

    suspend fun recordFullTrack(diagnostic: FullTrackDiagnostic, completedAtMs: Long) {
        mutex.withLock {
            try {
                val old = load()
                if (completedAtMs < old.resetAtMs && nowMs() >= old.resetAtMs) return@withLock
                accept(old.copy(fullTrack = old.fullTrack.add(diagnostic)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                unrecorded++
            }
        }
    }

    suspend fun recordPair(missingReason: String?, completedAtMs: Long) {
        mutex.withLock {
            try {
                val old = load()
                if (completedAtMs < old.resetAtMs && nowMs() >= old.resetAtMs) return@withLock
                val reasons = if (missingReason == null) old.missingReasons else {
                    val reason = when (missingReason) {
                        "Radio B failed or no response", "Radio B empty", "No eligible B candidates",
                        "No selection despite eligible B candidates" -> missingReason
                        else -> "Other"
                    }
                    old.missingReasons + (reason to ((old.missingReasons[reason] ?: 0L) + 1))
                }
                accept(old.copy(pairsDecided = old.pairsDecided + 1, missingReasons = reasons))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                unrecorded++
            }
        }
    }

    suspend fun reset(): Boolean = mutex.withLock {
        try {
            val now = nowMs().coerceAtLeast(1L)
            val fresh = EnhancedRadioStatisticsState(startedAtMs = now, resetAtMs = now, lastObservedAtMs = now)
            write(json.encodeToString(fresh))
            state = fresh
            pending = 0
            failedWrites = 0
            lastSavedMs = monotonicMs()
            unrecorded = 0
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun report(): String = mutex.withLock {
        try {
            val current = load()
            flushLocked()
            current.asText(nowMs()) + "\nUnrecorded events this process: $unrecorded" +
                "\nPending statistics events: $pending; failed flush attempts: $failedWrites"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "Enhanced statistics unavailable: local storage could not be read."
        }
    }
}
