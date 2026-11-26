package com.maxrave.simpmusic.viewModel

/**
 * Complete an existing playlist snapshot without mutating playback.
 * Failed/empty pages and repeated continuation tokens are not successful
 * completion: a truncated snapshot must never become an Enhanced source.
 */
internal suspend fun <T> completeShuffleSource(
    initial: List<T>,
    continuation: String?,
    id: (T) -> String,
    fetch: suspend (String) -> Pair<List<T>?, String?>?,
): List<T>? {
    val all = initial.toMutableList()
    val visited = hashSetOf<String>()
    var next = continuation
    while (!next.isNullOrBlank()) {
        val token = next
        if (!visited.add(token)) return null
        val page = fetch(token) ?: return null
        val tracks = page.first ?: return null
        if (tracks.isEmpty()) return null
        all.addAll(tracks)
        next = page.second
    }
    return all.distinctBy(id)
}

internal fun <T> shuffleSourceStillMatches(
    original: List<T>,
    current: List<T>,
    id: (T) -> String,
): Boolean = current.size >= original.size &&
    original.indices.all { index -> id(original[index]) == id(current[index]) }
