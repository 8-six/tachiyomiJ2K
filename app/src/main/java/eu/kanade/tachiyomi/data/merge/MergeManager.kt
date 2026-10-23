package eu.kanade.tachiyomi.data.merge

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.inTransaction
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.MERGED_SOURCE_ID
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

/**
 * Creates and edits merged manga: one library entry made from the same manga in several sources.
 *
 * The merged manga is a regular manga of the [MergedSource]. The manga it is made of stay in the database, out of the
 * library, and keep being updated from their own sources.
 */
class MergeManager(
    private val db: DatabaseHelper = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {
    fun isMerged(manga: Manga) = manga.source == MERGED_SOURCE_ID

    fun canMerge(manga: Manga) = manga.source != LocalSource.ID && manga.source != MERGED_SOURCE_ID

    /** The manga a merge is made of, with how they take part in it, best priority first. */
    fun getMembers(merged: Manga): List<Pair<MergedMangaReference, Manga>> =
        db.getMergedReferences(merged.id!!).mapNotNull { reference ->
            db.getManga(reference.mangaId).executeAsBlocking()?.let { reference to it }
        }

    /**
     * Merges [other] into [base] and returns the merged manga. If [base] already is a merged manga, [other] is added to
     * it, otherwise a new merged manga takes the place of [base] in the library. Chapters are only fetched by [refresh].
     */
    fun merge(
        base: Manga,
        other: Manga,
    ): Manga {
        require(canMerge(other)) { "This manga can't be merged" }
        require(base.id != other.id) { "A manga can't be merged with itself" }
        require(isMerged(base) || canMerge(base)) { "This manga can't be merged" }

        if (isMerged(base)) {
            addMember(base, other)
            return base
        }

        return db.db.inTransactionReturn {
            val merged =
                Manga.create("merged:${UUID.randomUUID()}", base.title, MERGED_SOURCE_ID).apply {
                    author = base.originalAuthor
                    artist = base.originalArtist
                    description = base.originalDescription
                    genre = base.originalGenre
                    status = base.originalStatus
                    thumbnail_url = base.thumbnail_url
                    initialized = true
                    favorite = base.favorite
                    date_added = base.date_added
                    chapter_flags = base.chapter_flags
                    viewer_flags = base.viewer_flags
                    hide_title = base.hide_title
                }
            merged.id = db.insertManga(merged).executeAsBlocking().insertedId()

            // The merged manga takes over everything the library knows about the manga it replaces
            val categories = db.getCategoriesForManga(base).executeAsBlocking()
            if (categories.isNotEmpty()) {
                db.setMangaCategories(categories.map { MangaCategory.create(merged, it) }, listOf(merged))
            }
            db.getTracks(base).executeAsBlocking().forEach { track ->
                track.manga_id = merged.id!!
                db.insertTrack(track).executeAsBlocking()
            }

            db.insertMergedReference(MergedMangaReference(mergeId = merged.id!!, mangaId = base.id!!, priority = 0, isInfoManga = true))
            addReference(merged, other, priority = 1)
            hideFromLibrary(base)
            hideFromLibrary(other)
            merged
        }
    }

    private fun addMember(
        merged: Manga,
        other: Manga,
    ) {
        val references = db.getMergedReferences(merged.id!!)
        if (references.any { it.mangaId == other.id }) return
        addReference(merged, other, priority = (references.maxOfOrNull { it.priority } ?: -1) + 1)
        hideFromLibrary(other)
    }

    private fun addReference(
        merged: Manga,
        member: Manga,
        priority: Int,
    ) {
        db.insertMergedReference(MergedMangaReference(mergeId = merged.id!!, mangaId = member.id!!, priority = priority))
    }

    private fun hideFromLibrary(manga: Manga) {
        if (!manga.favorite) return
        manga.favorite = false
        db.insertManga(manga).executeAsBlocking()
    }

    /**
     * Removes a manga from the merge. Its chapters leave the merged manga on the next [refresh].
     * The last manga can't be removed, use [unmerge] for that.
     */
    fun removeMember(
        merged: Manga,
        member: Manga,
    ) {
        val references = db.getMergedReferences(merged.id!!)
        require(references.size > 1) { "A merge needs at least one manga" }
        val removed = references.firstOrNull { it.mangaId == member.id } ?: return
        db.deleteMergedReference(merged.id!!, member.id!!)
        if (removed.isInfoManga) {
            references.firstOrNull { it.mangaId != member.id }?.let { db.updateMergedReference(it.copy(isInfoManga = true)) }
        }
    }

    /** Moves a manga one place up (checked first, wins duplicated chapters) or down. */
    fun moveMember(
        merged: Manga,
        member: Manga,
        up: Boolean,
    ) {
        val references = db.getMergedReferences(merged.id!!)
        val index = references.indexOfFirst { it.mangaId == member.id }
        val otherIndex = if (up) index - 1 else index + 1
        if (index < 0 || otherIndex !in references.indices) return

        // Rewrite all priorities so equal ones can't get in the way
        val reordered = references.toMutableList().apply { add(otherIndex, removeAt(index)) }
        db.db.inTransaction {
            reordered.forEachIndexed { priority, reference -> db.updateMergedReference(reference.copy(priority = priority)) }
        }
    }

    fun setInfoManga(
        merged: Manga,
        member: Manga,
    ) {
        db.db.inTransaction {
            db.getMergedReferences(merged.id!!).forEach {
                db.updateMergedReference(it.copy(isInfoManga = it.mangaId == member.id))
            }
        }
    }

    fun setGetChapterUpdates(
        merged: Manga,
        member: Manga,
        enabled: Boolean,
    ) {
        db
            .getMergedReferences(merged.id!!)
            .firstOrNull { it.mangaId == member.id }
            ?.let { db.updateMergedReference(it.copy(getChapterUpdates = enabled)) }
    }

    /**
     * Undoes a merge: the manga the details came from takes the place of the merged manga in the library, with its
     * categories and tracking, and the merged manga with its chapters and history is deleted.
     */
    fun unmerge(merged: Manga): Manga {
        val references = db.getMergedReferences(merged.id!!)
        val reference = references.firstOrNull { it.isInfoManga } ?: references.firstOrNull() ?: throw Exception("Nothing to restore")
        val primary = db.getManga(reference.mangaId).executeAsBlocking() ?: throw Exception("Nothing to restore")

        db.db.inTransaction {
            if (merged.favorite) {
                primary.favorite = true
                if (primary.date_added == 0L) primary.date_added = merged.date_added
                db.insertManga(primary).executeAsBlocking()
            }
            val categories = db.getCategoriesForManga(merged).executeAsBlocking()
            if (categories.isNotEmpty()) {
                db.setMangaCategories(categories.map { MangaCategory.create(primary, it) }, listOf(primary))
            }
            val trackedBy =
                db
                    .getTracks(primary)
                    .executeAsBlocking()
                    .map { it.sync_id }
                    .toSet()
            db.getTracks(merged).executeAsBlocking().filter { it.sync_id !in trackedBy }.forEach { track ->
                track.manga_id = primary.id!!
                db.insertTrack(track).executeAsBlocking()
            }
            // Chapters, history, categories and the references go with it
            db.deleteManga(merged).executeAsBlocking()
        }
        return primary
    }

    /**
     * Fetches the details and chapters of every manga in the merge and updates the merged manga from them.
     * Chapters that were read in any of the manga are marked as read in the merged manga.
     */
    suspend fun refresh(merged: Manga) {
        val source = sourceManager.get(MERGED_SOURCE_ID) as MergedSource
        val update = source.getMangaUpdate(merged.copy(), emptyList(), fetchDetails = true, fetchChapters = true)

        merged.copyFrom(update.manga)
        merged.initialized = true
        db.insertManga(merged).executeAsBlocking()

        syncChaptersWithSource(db, update.chapters, merged, source)
        importReadProgress(merged)
    }

    /**
     * Marks chapters of the merged manga as read (and bookmarked) when the same chapter is in any of its manga, so a
     * merge doesn't lose what was already read. Never marks anything as unread.
     */
    fun importReadProgress(merged: Manga) {
        val members = db.getMergedReferences(merged.id!!).mapNotNull { db.getManga(it.mangaId).executeAsBlocking() }
        val memberChapters = members.associateWith { db.getChapters(it).executeAsBlocking() }

        val readNumbers =
            memberChapters.values
                .flatten()
                .filter { it.read && it.isRecognizedNumber }
                .map { it.chapter_number }
                .toSet()
        // Chapters by where they came from, the same way the merged chapters point to them
        val originals: Map<String, Chapter> =
            memberChapters
                .flatMap { (manga, chapters) ->
                    chapters.map { MergedChapterUrl.encode(manga.source, manga.url, it.url) to it }
                }.toMap()

        val changed =
            db
                .getChapters(merged)
                .executeAsBlocking()
                .filter { chapter ->
                    val original = originals[chapter.url]
                    var updated = false
                    if (!chapter.read &&
                        (original?.read == true || (chapter.isRecognizedNumber && chapter.chapter_number in readNumbers))
                    ) {
                        chapter.read = true
                        original?.takeIf { it.read }?.let { chapter.last_page_read = it.last_page_read }
                        updated = true
                    }
                    if (!chapter.bookmark && original?.bookmark == true) {
                        chapter.bookmark = true
                        updated = true
                    }
                    updated
                }
        if (changed.isNotEmpty()) db.updateChaptersProgress(changed).executeAsBlocking()
    }
}
