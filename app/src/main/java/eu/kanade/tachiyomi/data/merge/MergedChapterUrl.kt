package eu.kanade.tachiyomi.data.merge

/**
 * Chapters of a merged manga keep the url of the chapter in its original source, together with where it came from, so
 * the right source can be asked for the pages: `sourceId \n mangaUrl \n chapterUrl`. The manga is identified by its url
 * and not its database id, so this keeps working after a restore. Urls can't contain a line break.
 */
object MergedChapterUrl {
    private const val SEPARATOR = '\n'

    class Origin(
        val sourceId: Long,
        val mangaUrl: String,
        val url: String,
    )

    fun encode(
        sourceId: Long,
        mangaUrl: String,
        url: String,
    ) = "$sourceId$SEPARATOR$mangaUrl$SEPARATOR$url"

    fun decode(encoded: String): Origin? {
        val parts = encoded.split(SEPARATOR, limit = 3)
        if (parts.size != 3) return null
        return Origin(
            sourceId = parts[0].toLongOrNull() ?: return null,
            mangaUrl = parts[1],
            url = parts[2],
        )
    }
}
