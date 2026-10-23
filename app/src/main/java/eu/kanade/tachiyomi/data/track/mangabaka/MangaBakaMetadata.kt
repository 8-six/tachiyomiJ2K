package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaImage
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.source.model.SManga
import timber.log.Timber

/**
 * Turns a MangaBaka series into [TrackMangaMetadata], following the language, cover and tag settings in
 * [TrackPreferences].
 */
internal class MangaBakaMetadataBuilder(
    private val api: MangaBakaApi,
    private val preferences: TrackPreferences,
) {
    suspend fun build(item: MangaBakaItem): TrackMangaMetadata =
        TrackMangaMetadata(
            remoteId = item.id,
            title = item.chooseBestTitle(preferences.mangabakaTitleLanguage().get()),
            coverUrl = chooseCover(item),
            description = item.description?.trim()?.takeIf { it.isNotEmpty() },
            authors =
                item.authors
                    .orEmpty()
                    .joinToString(", ")
                    .ifBlank { null },
            artists =
                item.artists
                    .orEmpty()
                    .joinToString(", ")
                    .ifBlank { null },
            tags = chooseTags(item),
            status = item.status.toMangaStatus(),
        )

    private suspend fun chooseCover(item: MangaBakaItem): String? {
        val seriesCover = item.cover.bestUrl()
        val mode = preferences.mangabakaCoverMode().get()
        if (mode == MangaBaka.COVER_DEFAULT) return seriesCover

        val images =
            try {
                api.getSeriesImages(item.id)
            } catch (e: Exception) {
                Timber.w(e, "Could not load the covers of MangaBaka series ${item.id}")
                return seriesCover
            }

        val language =
            preferences.mangabakaCoverLanguage().get().let {
                if (it == MangaBaka.LANGUAGE_ORIGINAL) item.originalLanguage.orEmpty() else it
            }
        val type = preferences.mangabakaCoverType().get()
        val safeOnly = preferences.mangabakaCoverSafeOnly().get()

        val candidates =
            images
                .filter { it.image?.raw?.url != null }
                .filter { !safeOnly || it.contentRating == null || it.contentRating == "safe" }
                .filter { type == MangaBaka.COVER_TYPE_ANY || it.type.equals(type, ignoreCase = true) }
                .filter { language.isEmpty() || language == MangaBaka.LANGUAGE_ANY || it.matchesLanguage(language) }

        val order = compareBy<MangaBakaImage>({ it.indexNumeric ?: Double.NEGATIVE_INFINITY }, { it.createdAt.orEmpty() })
        val chosen =
            when (mode) {
                MangaBaka.COVER_LATEST -> candidates.maxWithOrNull(order)
                MangaBaka.COVER_FIRST -> candidates.minWithOrNull(order)
                else -> null
            }
        return chosen?.image?.raw?.url ?: seriesCover
    }

    private fun MangaBakaImage.matchesLanguage(code: String): Boolean {
        val lang = language ?: return false
        return lang.equals(code, ignoreCase = true) || lang.startsWith("$code-", ignoreCase = true)
    }

    /**
     * Genres plus every tag of the selected groups. MangaBaka splits its tags into groups (themes, narrative tropes,
     * audience demographics, ...) and genres are a separate list, so each can be picked on its own.
     */
    private suspend fun chooseTags(item: MangaBakaItem): List<String> {
        val categories = preferences.mangabakaTagCategories().get()

        fun allows(category: String) = categories.isEmpty() || category in categories

        val result = LinkedHashSet<String>()

        if (allows(MangaBaka.TAG_GENRES)) {
            val labels = api.getGenreLabels()
            item.genres.orEmpty().forEach { slug ->
                result += labels[slug] ?: slug.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
            }
        }

        val hideSpoilers = preferences.mangabakaHideSpoilerTags().get()
        val hideExplicit = preferences.mangabakaHideExplicitTags().get()
        val detailed = item.tagsV2
        if (detailed != null) {
            detailed
                .filter { !(hideSpoilers && it.isSpoiler) && !(hideExplicit && it.isExplicit) }
                .filter { allows(it.category) }
                .forEach { result += it.name }
        } else if (categories.isEmpty()) {
            // Only the flat list is available, which doesn't say which group a tag is in
            item.tags.orEmpty().forEach { result += it }
        }
        return result.toList()
    }

    private fun String.toMangaStatus(): Int? =
        when (this) {
            "releasing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> null
        }
}
