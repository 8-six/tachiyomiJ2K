package eu.kanade.tachiyomi.data.track.model

// KMK -->

/**
 * A series a tracker links to or recommends for another one.
 *
 * @param relation how it relates, like SEQUEL or PREQUEL, or null for a recommendation.
 */
data class Suggestion(
    val relation: String?,
    val title: String,
    val coverUrl: String?,
)
// KMK <--
