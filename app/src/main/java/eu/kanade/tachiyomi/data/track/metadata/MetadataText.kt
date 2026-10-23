package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.source.model.SManga

/** What the user chose about the details taken from trackers, as far as the parsers need to know. */
data class MetadataOptions(
    val titleStyle: String = TITLE_AUTO,
    val hideSpoilerTags: Boolean = true,
    /** AniList only: tags the community finds less relevant than this (0 to 100) are left out. */
    val minTagRank: Int = 50,
) {
    companion object {
        const val TITLE_AUTO = "auto"
        const val TITLE_ENGLISH = "english"
        const val TITLE_ROMAJI = "romaji"
        const val TITLE_NATIVE = "native"
    }
}

/** Helpers shared by the trackers that provide [TrackMangaMetadata]. */
object MetadataText {
    /** The title in the style the user prefers, falling back to the others in a sensible order. */
    fun pickTitle(
        style: String,
        english: String?,
        romaji: String?,
        native: String?,
    ): String? {
        val order =
            when (style) {
                MetadataOptions.TITLE_ROMAJI -> listOf(romaji, english, native)
                MetadataOptions.TITLE_NATIVE -> listOf(native, romaji, english)
                else -> listOf(english, romaji, native)
            }
        return order.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private val lineBreak = Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE)
    private val paragraphEnd = Regex("""<\s*/\s*p\s*>""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]+>""")
    private val bbCode =
        Regex("""\[/?(?:b|i|u|s|spoiler|quote|center|size|color|url|img|list|code)(?:=[^\]]*)?]""", RegexOption.IGNORE_CASE)
    private val manyBlankLines = Regex("""\n{3,}""")

    /** A description without the HTML or BBCode some services return it with, or null if nothing is left. */
    fun cleanDescription(raw: String?): String? =
        raw
            ?.replace(lineBreak, "\n")
            ?.replace(paragraphEnd, "\n\n")
            ?.replace(tag, "")
            ?.replace(bbCode, "")
            ?.replace("&amp;", "&")
            ?.replace("&quot;", "\"")
            ?.replace("&#039;", "'")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace(manyBlankLines, "\n\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Names for a field like authors: no blanks, no duplicates, in order. */
    fun names(vararg values: String?): String? =
        values
            .flatMap { it.orEmpty().split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
            .ifEmpty { null }

    /** Tags without blanks or duplicates, in order. */
    fun tags(values: Iterable<String?>): List<String>? =
        values
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase() }
            .ifEmpty { null }

    /** Other titles without blanks, the main [title] and duplicates, in order. */
    fun alternativeTitles(
        title: String?,
        values: Iterable<String?>,
    ): List<String>? =
        values
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .filterNot { it.equals(title?.trim(), ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .ifEmpty { null }

    /** Which of a series' people wrote it and which drew it, from the role a service gives each of them. */
    class Credits {
        private val authors = mutableListOf<String>()
        private val artists = mutableListOf<String>()

        fun add(
            name: String?,
            role: String?,
        ) {
            if (name.isNullOrBlank()) return
            val r = role.orEmpty().lowercase()
            val writes = listOf("story", "writer", "author", "original", "script", "creator").any { it in r }
            val draws = listOf("art", "illustrat", "pencil", "design", "drawing").any { it in r }
            when {
                writes && draws -> {
                    authors += name
                    artists += name
                }
                writes -> authors += name
                draws -> artists += name
                // Some services don't say what a person did
                r.isBlank() -> authors += name
            }
        }

        fun authors() = names(*authors.toTypedArray())

        fun artists() = names(*artists.toTypedArray())
    }

    /** The statuses services use, as SManga constants. Null for ones that tell nothing, like "not yet released". */
    fun status(value: String?): Int? =
        when (value?.trim()?.lowercase()?.replace(' ', '_')) {
            "releasing", "ongoing", "current", "currently_publishing", "publishing" -> SManga.ONGOING
            "finished", "completed", "complete", "released", "ended" -> SManga.COMPLETED
            "hiatus", "on_hiatus", "paused" -> SManga.ON_HIATUS
            "cancelled", "canceled", "discontinued", "abandoned" -> SManga.CANCELLED
            else -> null
        }
}
