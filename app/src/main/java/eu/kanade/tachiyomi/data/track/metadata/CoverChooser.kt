package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.CoverCandidate

/**
 * Picks the best cover of those the trackers of a series offer: the one with the most pixels.
 */
object CoverChooser {
    /**
     * @param candidates in the order of the trackers, which decides between covers of the same size.
     * @param measure the size of an image, asked for those that were offered without one. Null if it can't be told.
     */
    suspend fun choose(
        candidates: List<CoverCandidate>,
        measure: suspend (url: String) -> Pair<Int, Int>?,
    ): CoverCandidate? {
        // A cover that has nothing to be compared with isn't looked at
        if (candidates.size <= 1) return candidates.firstOrNull()

        val measured =
            candidates.map { candidate ->
                if (candidate.width != null && candidate.height != null) {
                    candidate
                } else {
                    measure(candidate.url)?.let { (w, h) -> candidate.copy(width = w, height = h) } ?: candidate
                }
            }

        // Covers are portrait. A wide image is a banner, it is only used when there is nothing else
        return measured
            .withIndex()
            .maxWithOrNull(
                compareBy<IndexedValue<CoverCandidate>>(
                    { !it.value.isLandscape() },
                    { it.value.pixels },
                    { -it.index },
                ),
            )?.value
    }

    private fun CoverCandidate.isLandscape() = width != null && height != null && width > height * 1.1
}
