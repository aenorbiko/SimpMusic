package com.maxrave.simpmusic.viewModel

/**
 * Pure queue planner for in-place Shuffle/Enhanced transitions.
 *
 * Already played tracks and the currently playing track stay in their original
 * timeline positions. Only tracks scheduled after the current position change.
 * IDs are used to prevent replaying tracks already heard in this queue.
 */
internal object UpcomingQueuePlanner {
    data class Plan<T>(
        val preserved: List<T>,
        val upcoming: List<T>,
    )

    fun <T> preserveCurrent(
        existingQueue: List<T>,
        currentIndex: Int,
        proposedUpcoming: List<T>,
        id: (T) -> String,
    ): Plan<T>? {
        if (currentIndex !in existingQueue.indices) return null

        val playedAndCurrent = existingQueue.take(currentIndex + 1)
        val used = playedAndCurrent.mapTo(hashSetOf(), id)
        val tail = proposedUpcoming.filter { used.add(id(it)) }
        return Plan(playedAndCurrent, tail)
    }

    fun <T> originalOrderAfterCurrent(
        liked: List<T>,
        currentId: String?,
        playedIds: Set<String>,
        id: (T) -> String,
    ): List<T> {
        val position = liked.indexOfFirst { id(it) == currentId }
        val candidates = if (position >= 0) liked.drop(position + 1) else liked
        return candidates.filter { id(it) !in playedIds }
    }
}
