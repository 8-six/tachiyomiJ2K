package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val TITLE_PRIORITIES = listOf("en", "ja-Latn", "ja", "ko-Latn", "ko", "zh-Latn", "zh")

@Serializable
data class MangaBakaItemResult(
    val data: MangaBakaItem,
)

@Serializable
data class MangaBakaSearchResult(
    val data: List<MangaBakaItem>,
)

@Serializable
data class MangaBakaItem(
    val id: Long,
    val cover: MangaBakaCover,
    val description: String?,
    val published: MangaBakaPublishData,
    val status: String,
    val type: String,
    val rating: Double?,
    val titles: List<MangaBakaItemTitle>?,
    val title: String? = null,
    @SerialName("native_title")
    val nativeTitle: String? = null,
    @SerialName("romanized_title")
    val romanizedTitle: String? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    @SerialName("tags_v2")
    val tagsV2: List<MangaBakaTag>? = null,
    @SerialName("content_rating")
    val contentRating: String? = null,
    @SerialName("original_language")
    val originalLanguage: String? = null,
) {
    /**
     * Picks the title to show.
     *
     * [preferredLanguage] is tried first, then the default order from
     * https://mangabaka.org/pages/announcements/15-titles-v2#finding-the-title-you-want (extended with zh-Latn and zh).
     * It is either a title language code, [TITLE_NATIVE], [TITLE_ROMANIZED] or null/[LANGUAGE_AUTO_CODE] to only use the default order.
     */
    fun chooseBestTitle(preferredLanguage: String? = null): String {
        when (preferredLanguage) {
            TITLE_NATIVE -> nativeTitle?.takeIf { it.isNotBlank() }?.let { return it }
            TITLE_ROMANIZED -> romanizedTitle?.takeIf { it.isNotBlank() }?.let { return it }
        }

        val languages =
            buildList {
                if (!preferredLanguage.isNullOrBlank() && preferredLanguage !in SPECIAL_TITLES) add(preferredLanguage)
                addAll(TITLE_PRIORITIES)
            }.distinct()

        return languages.firstNotNullOfOrNull { bestTitleIn(it) }
            ?: title?.takeIf { it.isNotBlank() }
            ?: titles?.firstOrNull()?.title
            ?: "ID: $id - Could not find name! (report on the MangaBaka Discord)"
    }

    private fun bestTitleIn(language: String): String? =
        titles
            ?.filter { it.matchesLanguage(language) }
            ?.minByOrNull {
                when {
                    it.language == language && it.isPrimary -> 0
                    it.language == language && "official" in it.traits -> 1
                    it.language == language && "native" in it.traits -> 2
                    it.language == language -> 3
                    it.isPrimary -> 4
                    else -> 5
                }
            }?.title

    companion object {
        const val TITLE_NATIVE = "native"
        const val TITLE_ROMANIZED = "romanized"
        const val LANGUAGE_AUTO_CODE = "auto"
        private val SPECIAL_TITLES = setOf(TITLE_NATIVE, TITLE_ROMANIZED, LANGUAGE_AUTO_CODE)
    }
}

@Serializable
data class MangaBakaTag(
    val id: Long,
    val name: String,
    @SerialName("name_path")
    val namePath: String? = null,
    @SerialName("is_spoiler")
    val isSpoiler: Boolean = false,
    @SerialName("is_genre")
    val isGenre: Boolean = false,
    @SerialName("content_rating")
    val contentRating: String? = null,
) {
    /** The top level group of MangaBaka's tag tree this tag lives in, like "Themes" or "Narrative Tropes". */
    val category: String
        get() = namePath?.substringBefore(" > ")?.takeIf { it.isNotBlank() } ?: name

    val isExplicit: Boolean
        get() = contentRating != null && contentRating != "safe"
}

@Serializable
data class MangaBakaCover(
    val raw: MangaBakaRawCover? = null,
    val x150: MangaBakaScaledCover? = null,
    val x250: MangaBakaScaledCover? = null,
    val x350: MangaBakaScaledCover? = null,
) {
    /** The largest cover available. */
    fun bestUrl(): String? = raw?.url ?: x350?.x1 ?: x250?.x1 ?: x150?.x1
}

@Serializable
data class MangaBakaScaledCover(
    val x1: String?,
)

@Serializable
data class MangaBakaRawCover(
    val url: String? = null,
)

@Serializable
data class MangaBakaPublishData(
    @SerialName("start_date")
    val startDate: String?,
)

@Serializable
data class MangaBakaItemTitle(
    val language: String,
    val traits: List<String>,
    val title: String,
    @SerialName("is_primary")
    val isPrimary: Boolean,
) {
    // "zh" also covers "zh-Hant", but "ja" must not cover the romanized "ja-Latn"
    fun matchesLanguage(code: String): Boolean =
        language == code || (!code.contains('-') && language.startsWith("$code-") && !language.endsWith("-Latn"))
}

@Serializable
data class MangaBakaImagesResult(
    val data: List<MangaBakaImage>,
    val pagination: MangaBakaPagination? = null,
)

@Serializable
data class MangaBakaPagination(
    val next: String? = null,
)

/** One of the extra covers (volume, season, ...) MangaBaka keeps for a series. */
@Serializable
data class MangaBakaImage(
    val id: Long,
    val type: String? = null,
    val language: String? = null,
    @SerialName("index_numeric")
    val indexNumeric: Double? = null,
    @SerialName("content_rating")
    val contentRating: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val image: MangaBakaImageFile? = null,
)

@Serializable
data class MangaBakaImageFile(
    val raw: MangaBakaRawCover? = null,
)

@Serializable
data class MangaBakaGenresResult(
    val data: List<MangaBakaGenre>,
)

@Serializable
data class MangaBakaGenre(
    val label: String,
    val value: String,
)
