package com.maxrave.simpmusic.viewModel

internal actual fun enhancedProcessSample(includePss: Boolean): EnhancedProcessSample {
    val runtime = Runtime.getRuntime()
    return EnhancedProcessSample(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory())
}
