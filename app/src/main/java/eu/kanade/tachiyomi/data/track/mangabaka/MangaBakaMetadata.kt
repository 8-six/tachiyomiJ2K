package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.metadata.MetadataText
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Turns a MangaBaka series into [TrackMangaMetadata], following the language, cover and tag settings in
 * [TrackPreferences].
 */
internal class MangaBakaMetadataBuilder(
    private val api: MangaBakaApi,
    private val preferences: TrackPreferences,
) {
    suspend fun build(item: MangaBakaItem): TrackMangaMetadata {
        val warnings = mutableListOf<String>()
        val title = item.chooseBestTitle(preferences.mangabakaTitleLanguage().get())
        return TrackMangaMetadata(
            remoteId = item.id,
            title = title,
            coverUrl = chooseCover(item, warnings),
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
            alternativeTitles =
                MetadataText.alternativeTitles(
                    title,
                    listOf(item.title, item.nativeTitle, item.romanizedTitle) + item.titles.orEmpty().map { it.title },
                ),
            seriesType = TrackMangaMetadata.SeriesType.from(item.type),
            warnings = warnings,
        )
    }

    private suspend fun chooseCover(
        item: MangaBakaItem,
        warnings: MutableList<String>,
    ): String? {
        val seriesCover = item.cover.bestUrl()
        val mode = preferences.mangabakaCoverMode().get()
        if (mode == MangaBaka.COVER_DEFAULT) return seriesCover

        val images =
            try {
                api.getSeriesImages(item.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Could not load the covers of MangaBaka series ${item.id}")
                warnings += "covers: ${e.message ?: e.javaClass.simpleName}"
                return seriesCover
            }

        val language =
            preferences.mangabakaCoverLanguage().get().let {
                if (it == MangaBaka.LANGUAGE_ORIGINAL) item.originalLanguage ?: MangaBaka.LANGUAGE_ANY else it
            }
        return MangaBakaCovers.choose(
            images = images,
            mode = mode,
            language = language,
            type = preferences.mangabakaCoverType().get(),
            safeOnly = preferences.mangabakaCoverSafeOnly().get(),
            originalLanguage = item.originalLanguage,
        ) ?: seriesCover
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
