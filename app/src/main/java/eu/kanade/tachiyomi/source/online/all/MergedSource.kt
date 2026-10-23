package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.merge.MergeMemberChapters
import eu.kanade.tachiyomi.data.merge.MergedChapterUrl
import eu.kanade.tachiyomi.data.merge.MergedChapters
import eu.kanade.tachiyomi.data.merge.MergedMangaReference
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Response
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

const val MERGED_SOURCE_ID = 6969L

/**
 * A source that doesn't exist online: a manga of this source is made from several manga of other sources.
 * Its chapters are the combined chapters of those manga (see [MergedChapters]) and reading, downloading or fetching
 * the images of a chapter is handed to the source the chapter came from.
 */
class MergedSource(
    private val context: Context,
) : HttpSource() {
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    override val id: Long = MERGED_SOURCE_ID
    override val name: String = context.getString(R.string.merged_source)
    override val lang: String = "other"
    override val baseUrl: String = ""
    override val supportsLatest: Boolean = false

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate =
        withIOContext {
            val merged = requireMergedManga(manga)
            val references = requireReferences(merged)
            val details = if (fetchDetails) fetchDetails(manga, merged, references) else null
            val mergedChapters = if (fetchChapters) fetchChapters(references) else null
            SMangaUpdate(details ?: manga, mergedChapters ?: chapters)
        }

    override suspend fun getMangaDetails(manga: SManga): SManga = getMangaUpdate(manga, emptyList(), true, false).manga

    override suspend fun getChapterList(manga: SManga): List<SChapter> = getMangaUpdate(manga, emptyList(), false, true).chapters

    private fun requireMergedManga(manga: SManga): Manga =
        db.getManga(manga.url, id).executeAsBlocking() ?: throw Exception(context.getString(R.string.merged_manga_missing))

    private fun requireReferences(merged: Manga): List<MergedMangaReference> =
        db.getMergedReferences(merged.id!!).also {
            if (it.isEmpty()) throw Exception(context.getString(R.string.merged_no_manga))
        }

    /**
     * The details of the merged manga follow the manga that is marked as its info manga, or the first one.
     */
    private suspend fun fetchDetails(
        manga: SManga,
        merged: Manga,
        references: List<MergedMangaReference>,
    ): SManga {
        val reference = references.firstOrNull { it.isInfoManga } ?: references.first()
        val info = db.getManga(reference.mangaId).executeAsBlocking() ?: return manga
        val source = sourceManager.get(info.source)

        if (source != null) {
            try {
                val updated = source.getMangaUpdate(info.copy(), emptyList(), fetchDetails = true, fetchChapters = false).manga
                info.copyFrom(updated)
                info.initialized = true
                db.insertManga(info).executeAsBlocking()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Could not refresh the details of ${info.title}, using the saved ones")
            }
        }

        return SManga.create().apply {
            url = merged.url
            title = merged.originalTitle
            author = info.originalAuthor
            artist = info.originalArtist
            description = info.originalDescription
            genre = info.originalGenre
            status = info.originalStatus
            thumbnail_url = info.thumbnail_url
            initialized = true
        }
    }

    /**
     * Brings the chapters of every member up to date, then combines them. A member that fails to update is skipped
     * unless none of them worked.
     */
    private suspend fun fetchChapters(references: List<MergedMangaReference>): List<SChapter> {
        val semaphore = Semaphore(MAX_PARALLEL_MEMBERS)
        var error: Exception? = null

        val members =
            coroutineScope {
                references
                    .map { reference ->
                        async {
                            val manga = db.getManga(reference.mangaId).executeAsBlocking() ?: return@async null
                            val source = sourceManager.getOrStub(manga.source)
                            if (reference.getChapterUpdates) {
                                semaphore.withPermit {
                                    try {
                                        val update =
                                            source.getMangaUpdate(
                                                manga.copy(),
                                                emptyList(),
                                                fetchDetails = false,
                                                fetchChapters = true,
                                            )
                                        syncChaptersWithSource(db, update.chapters, manga, source)
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Timber.w(e, "Could not update the chapters of ${manga.title}")
                                        error = error ?: e
                                    }
                                }
                            }
                            MergeMemberChapters(
                                mangaId = manga.id!!,
                                mangaUrl = manga.url,
                                sourceId = manga.source,
                                sourceName = source.name,
                                priority = reference.priority,
                                chapters = db.getChapters(manga).executeAsBlocking(),
                            )
                        }
                    }.awaitAll()
                    .filterNotNull()
            }

        val merged = MergedChapters.merge(members)
        if (merged.isEmpty()) throw error ?: Exception("No chapters found")
        return merged
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val origin = decodeChapter(chapter)
        val source = sourceManager.getOrStub(origin.sourceId)
        val pages =
            source.getPageList(
                SChapter.create().apply {
                    url = origin.url
                    name = chapter.name
                    scanlator = chapter.scanlator
                    chapter_number = chapter.chapter_number
                    date_upload = chapter.date_upload
                },
            )
        return pages.mapIndexed { index, page -> Page(index, encodePageUrl(origin.sourceId, page.url), page.imageUrl) }
    }

    override suspend fun getImageUrl(page: Page): String {
        val (source, forwarded) = forwardPage(page)
        return source.getImageUrl(forwarded)
    }

    override suspend fun getImage(page: Page): Response {
        val (source, forwarded) = forwardPage(page)
        return source.getImage(forwarded)
    }

    override fun getMangaUrl(manga: SManga): String {
        val merged = db.getManga(manga.url, id).executeAsBlocking() ?: return ""
        val references = db.getMergedReferences(merged.id!!)
        val reference = references.firstOrNull { it.isInfoManga } ?: references.firstOrNull() ?: return ""
        val info = db.getManga(reference.mangaId).executeAsBlocking() ?: return ""
        return (sourceManager.get(info.source) as? HttpSource)?.getMangaUrl(info).orEmpty()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val origin = MergedChapterUrl.decode(chapter.url) ?: return ""
        val source = sourceManager.get(origin.sourceId) as? HttpSource ?: return ""
        val original =
            SChapter.create().apply {
                url = origin.url
                name = chapter.name
            }
        // The source may not build chapter urls itself, then it's made from the manga's url like it normally would be
        return source.getChapterUrl(original).ifBlank {
            source.getChapterUrl(db.getManga(origin.mangaUrl, origin.sourceId).executeAsBlocking(), original).orEmpty()
        }
    }

    private fun decodeChapter(chapter: SChapter) =
        MergedChapterUrl.decode(chapter.url) ?: throw Exception(context.getString(R.string.merged_invalid_chapter))

    /**
     * Pages of a merged chapter have the id of their source in front of their url, as the page list can be cached and
     * restored without anything else about the page. The source gets a page that looks like its own again, which also
     * reports the download progress back to the page it was made from.
     */
    private fun forwardPage(page: Page): Pair<HttpSource, Page> {
        val (sourceId, url) = decodePageUrl(page.url) ?: throw Exception(context.getString(R.string.merged_invalid_chapter))
        val source =
            sourceManager.getOrStub(sourceId) as? HttpSource
                ?: throw Exception(context.getString(R.string.source_not_installed_, sourceId.toString()))
        return source to ForwardingPage(page.index, url, page.imageUrl, page)
    }

    private class ForwardingPage(
        index: Int,
        url: String,
        imageUrl: String?,
        private val target: Page,
    ) : Page(index, url, imageUrl) {
        override fun update(
            bytesRead: Long,
            contentLength: Long,
            done: Boolean,
        ) {
            target.update(bytesRead, contentLength, done)
        }
    }

    companion object {
        private const val MAX_PARALLEL_MEMBERS = 3

        fun encodePageUrl(
            sourceId: Long,
            url: String,
        ) = "$sourceId|$url"

        fun decodePageUrl(encoded: String): Pair<Long, String>? {
            val parts = encoded.split('|', limit = 2)
            if (parts.size != 2) return null
            return (parts[0].toLongOrNull() ?: return null) to parts[1]
        }
    }
}
