package com.maxrave.simpmusic.viewModel

/** Mix two liked tracks followed by one radio recommendation; never mutate the playlist. */
internal object EnhancedShufflePlanner {
    fun <T> mix(
        liked: List<T>,
        recommended: List<T>,
        id: (T) -> String,
    ): List<T> {
        val likedUnique = liked.distinctBy(id)
        val existingIds = likedUnique.map(id).toHashSet()
        val recommendations = recommended.filter { existingIds.add(id(it)) }
        val result = ArrayList<T>(likedUnique.size + minOf(recommendations.size, likedUnique.size / 2))
        var recommendationIndex = 0
        likedUnique.forEachIndexed { index, track ->
            result.add(track)
            if ((index + 1) % 2 == 0 && recommendationIndex < recommendations.size) {
                result.add(recommendations[recommendationIndex++])
            }
        }
        return result
    }
}
