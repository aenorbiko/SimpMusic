package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
) {
    fun asText(nowMs: Long): String = buildString {
        fun percent(n: Long): String {
            val tenths = if (firstPageRequests == 0L) 0L else kotlin.math.round(n * 1000.0 / firstPageRequests).toLong()
            return "${tenths / 10}.${tenths % 10}%"
        }
        val minutes = if (startedAtMs == 0L) 0L else ((nowMs - startedAtMs).coerceAtLeast(0) / MINUTE_MS)
        appendLine("Enhanced statistics")
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
        appendLine("Upper-bound estimates with refreshed TTL; no actual cache or continuation reuse. Cancelled requests not counted.")
        append("Local statistics only; no song IDs, titles or account data in this report.")
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
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var unrecorded = 0L

    private suspend fun load(): EnhancedRadioStatisticsState {
        val value = read()
        if (value.isNullOrBlank()) return EnhancedRadioStatisticsState()
        return json.decodeFromString<EnhancedRadioStatisticsState>(value)
    }

    suspend fun record(videoId: String, continuation: Boolean, success: Boolean, completedAtMs: Long) {
        mutex.withLock {
            try {
                val old = load()
                val clockBack = old.lastEventAtMs > completedAtMs
                val now = completedAtMs.coerceAtLeast(1L)
                val recent = if (clockBack) mutableMapOf() else old.lastSuccessfulFirstPage
                    .filterValues { it <= now && now - it <= DAY_MS }.toMutableMap()
                val age = recent[videoId]?.let { now - it }
                val repeated = !continuation && age != null
                if (!continuation && success) recent[videoId] = now
                val excess = (recent.size - MAX_RECENT_IDS).coerceAtLeast(0)
                if (excess > 0) recent.entries.sortedBy { it.value }.take(excess).forEach { recent.remove(it.key) }
                val next = old.copy(
                    startedAtMs = if (old.startedAtMs == 0L || clockBack) now else old.startedAtMs,
                    lastEventAtMs = now,
                    firstPageRequests = old.firstPageRequests + if (continuation) 0 else 1,
                    successfulFirstPages = old.successfulFirstPages + if (!continuation && success) 1 else 0,
                    continuationRequests = old.continuationRequests + if (continuation) 1 else 0,
                    repeat20Minutes = old.repeat20Minutes + if (repeated && age!! < 20 * MINUTE_MS) 1 else 0,
                    repeat1Hour = old.repeat1Hour + if (repeated && age!! < 60 * MINUTE_MS) 1 else 0,
                    repeat24Hours = old.repeat24Hours + if (repeated && age!! < DAY_MS) 1 else 0,
                    lastSuccessfulFirstPage = recent,
                    historyEvictions = old.historyEvictions + excess,
                    clockResets = old.clockResets + if (clockBack) 1 else 0,
                )
                write(json.encodeToString(next))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A statistics failure must not stop playback or erase previous history.
                unrecorded++
            }
        }
    }

    suspend fun report(): String = mutex.withLock {
        try {
            load().asText(nowMs()) + "\nUnrecorded events this process: $unrecorded"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "Enhanced statistics unavailable: local storage could not be read."
        }
    }
}
