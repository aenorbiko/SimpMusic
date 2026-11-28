package com.maxrave.simpmusic.viewModel

import kotlin.time.TimeSource

internal data class EnhancedProcessSample(
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val cpuMs: Long? = null,
    val pssKiB: Long? = null,
)

internal expect fun enhancedProcessSample(includePss: Boolean): EnhancedProcessSample

/** Event-driven, process-local diagnostics. Called on the existing queue/view-model
 * coroutine context; no background job, disk writes, or requests of its own. */
internal class EnhancedPerformanceDiagnostics(
    private val sampler: (Boolean) -> EnhancedProcessSample = ::enhancedProcessSample,
    private val nowMs: () -> Long = run {
        val start = TimeSource.Monotonic.markNow()
        val clock: () -> Long = { start.elapsedNow().inWholeMilliseconds }
        clock
    },
) {
    private var pairs = 0L
    private var selected = 0L
    private var committed = 0L
    private var selectionCount = 0L
    private var selectionTotal = 0L
    private var selectionMax = 0L
    private var diagnosticCount = 0L
    private var diagnosticTotal = 0L
    private var diagnosticMax = 0L
    private var statsWrites = 0L
    private var sessionWrites = 0L
    private var sessionChars = 0L
    private var sessionEncodeTotal = 0L
    private var queueSaveCalls = 0L
    private var samples = 0L
    private var heapPeak = 0L
    private var first: EnhancedProcessSample? = null
    private var firstAt = 0L
    private var last: EnhancedProcessSample? = null
    private var lastAt = 0L
    private val reasons = linkedMapOf<String, Long>()
    private val recent = ArrayDeque<String>()

    fun sample(includePss: Boolean = false) {
        val value = runCatching { sampler(includePss) }.getOrNull() ?: return
        val now = nowMs()
        if (first == null) { first = value; firstAt = now }
        last = value
        lastAt = now
        samples++
        heapPeak = maxOf(heapPeak, value.heapUsedBytes)
    }

    fun selection(ms: Long) {
        selectionCount++
        selectionTotal += ms
        selectionMax = maxOf(selectionMax, ms)
    }
    fun diagnostic(ms: Long) {
        diagnosticCount++
        diagnosticTotal += ms
        diagnosticMax = maxOf(diagnosticMax, ms)
    }
    fun statsWrite() { statsWrites++ }
    fun sessionWrite(chars: Int, encodeMs: Long) {
        sessionWrites++
        sessionBytes += bytes
        sessionEncodeTotal += encodeMs
    }
    fun queueSave() { queueSaveCalls++ }
    fun committed(count: Int) { committed += count }

    fun <T> pair(a: String, b: String, decision: EnhancedShufflePlanner.RadioSelection<T>, timing: EnhancedPairTiming<T>) {
        pairs++
        if (decision.recommendation != null) {
            selected++
            return
        }
        val pagesB = listOfNotNull(timing.firstB, timing.secondB)
        val reason = when {
            decision.receivedB == 0 && pagesB.any { it.status != "OK" } -> "Radio B failed or no response"
            decision.receivedB == 0 -> "Radio B empty"
            decision.eligibleB == 0 -> "No eligible B candidates"
            else -> "No selection despite eligible B candidates"
        }
        reasons[reason] = (reasons[reason] ?: 0L) + 1
        fun page(p: EnhancedRadioPage<T>?) = p?.let { "${it.tracks.size} ${it.status}" } ?: "not requested"
        val line = "${a.take(100)} + ${b.take(100)}: $reason; A1=${page(timing.firstA)}, A2=${page(timing.secondA)}, B1=${page(timing.firstB)}, B2=${page(timing.secondB)}; eligible A/B=${decision.eligibleA}/${decision.eligibleB}"
        if (recent.size == 10) recent.removeFirst()
        recent.addLast(line)
    }

    fun reset() {
        pairs = 0; selected = 0; committed = 0
        selectionCount = 0; selectionTotal = 0; selectionMax = 0
        diagnosticCount = 0; diagnosticTotal = 0; diagnosticMax = 0
        statsWrites = 0; sessionWrites = 0; sessionBytes = 0; sessionEncodeTotal = 0; queueSaveCalls = 0
        samples = 0; heapPeak = 0; first = null; last = null; firstAt = 0; lastAt = 0
        reasons.clear(); recent.clear()
        sample()
    }

    fun report(): String = buildString {
        fun avg(total: Long, count: Long) = if (count == 0L) 0L else total / count
        val end = last
        val cpu = end?.cpuMs?.let { e -> first?.cpuMs?.let { e - it } }
        appendLine("Performance statistics (this process / since reset)")
        appendLine("Pairs decided: $pairs; selected: $selected; missing: ${pairs - selected}; recommendations committed: $committed")
        appendLine("Final selection wall time avg/max ($selectionCount): ${avg(selectionTotal, selectionCount)} / $selectionMax ms")
        appendLine("Detailed diagnostics wall time avg/max ($diagnosticCount): ${avg(diagnosticTotal, diagnosticCount)} / $diagnosticMax ms")
        appendLine("Successful statistics writes: $statsWrites")
        appendLine("Successful Enhanced session writes: $sessionWrites; UTF-8 payload total: $sessionBytes bytes; encoding total: $sessionEncodeTotal ms")
        appendLine("Queue persistence calls completed: $queueSaveCalls (not physical disk writes)")
        appendLine("Managed heap samples: $samples; current / sampled peak / limit: ${(end?.heapUsedBytes ?: 0L) / 1024} / ${heapPeak / 1024} / ${(end?.heapMaxBytes ?: 0L) / 1024} KiB")
        appendLine("Process PSS at report: ${end?.pssKiB?.let { "$it KiB" } ?: "unavailable"}")
        appendLine("Whole-process CPU time: ${cpu?.let { "$it ms over ${lastAt - firstAt} ms elapsed" } ?: "unavailable"} (includes playback and other work)")
        appendLine("Missing recommendation reasons:")
        if (reasons.isEmpty()) appendLine("None") else reasons.forEach { (reason, count) -> appendLine("$reason: $count") }
        appendLine("Recent missing pairs (max 10, local titles):")
        if (recent.isEmpty()) appendLine("None") else recent.forEach { appendLine(it) }
        append("No background sampling; sampled peaks are not absolute peaks. Counters reset on process death.")
    }
}
