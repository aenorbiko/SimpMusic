package com.maxrave.simpmusic.viewModel

import android.os.Debug
import android.os.Process

internal actual fun enhancedProcessSample(includePss: Boolean): EnhancedProcessSample {
    val runtime = Runtime.getRuntime()
    return EnhancedProcessSample(
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        heapMaxBytes = runtime.maxMemory(),
        cpuMs = Process.getElapsedCpuTime(),
        pssKiB = if (includePss) Debug.getPss() else null,
    )
}
