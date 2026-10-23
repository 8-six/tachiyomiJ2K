package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaImage

/**
 * Picks one of the extra covers (volumes, seasons, ...) MangaBaka keeps for a series.
 */
object MangaBakaCovers {
    /**
     * @param mode [MangaBaka.COVER_LATEST] for the highest numbered cover, [MangaBaka.COVER_FIRST] for the lowest.
     * @param language only covers in this language ([MangaBaka.LANGUAGE_ANY] for all of them). "zh" also matches "zh-Hant".
     * @param type only covers of this type ("volume", "season", ...) or [MangaBaka.COVER_TYPE_ANY].
     * @param safeOnly skip covers that aren't rated safe.
     * @param originalLanguage language the series was published in. Of covers with the same number, the one in this language wins.
     * @return the url of the cover, or null if none is left after filtering.
     */
    fun choose(
        images: List<MangaBakaImage>,
        mode: String,
        language: String,
        type: String,
        safeOnly: Boolean,
        originalLanguage: String?,
    ): String? = chooseImage(images, mode, language, type, safeOnly, originalLanguage)?.image?.raw?.url

    /** Like [choose], but gives the cover with its size. */
    fun chooseImage(
        images: List<MangaBakaImage>,
        mode: String,
        language: String,
        type: String,
        safeOnly: Boolean,
        originalLanguage: String?,
    ): MangaBakaImage? {
        val candidates =
            images
                .filter { it.image?.raw?.url != null }
                .filter { !safeOnly || it.contentRating == null || it.contentRating == "safe" }
                .filter { type == MangaBaka.COVER_TYPE_ANY || it.type.equals(type, ignoreCase = true) }
                .filter { language == MangaBaka.LANGUAGE_ANY || language.isEmpty() || it.matchesLanguage(language) }

        // Of covers with the same number there is often one per language and per site, so what's left decides
        fun MangaBakaImage.isOriginalLanguage() = originalLanguage != null && matchesLanguage(originalLanguage)

        // The same cover is on several sites in different sizes
        fun MangaBakaImage.pixels() = (image?.raw?.width ?: 0).toLong() * (image?.raw?.height ?: 0)

        val newestFirst =
            compareBy<MangaBakaImage>(
                { it.indexNumeric ?: Double.NEGATIVE_INFINITY },
                { it.isOriginalLanguage() },
                { it.pixels() },
                { it.createdAt.orEmpty() },
            )
        val oldestFirst =
            compareBy<MangaBakaImage>(
                { it.indexNumeric ?: Double.POSITIVE_INFINITY },
                { !it.isOriginalLanguage() },
                { -it.pixels() },
                { it.createdAt.orEmpty() },
            )

        return when (mode) {
            MangaBaka.COVER_LATEST -> candidates.maxWithOrNull(newestFirst)
            MangaBaka.COVER_FIRST -> candidates.minWithOrNull(oldestFirst)
            else -> null
        }
    }

    private fun MangaBakaImage.matchesLanguage(code: String): Boolean {
        val lang = language ?: return false
        return lang.equals(code, ignoreCase = true) || lang.startsWith("$code-", ignoreCase = true)
    }
}
