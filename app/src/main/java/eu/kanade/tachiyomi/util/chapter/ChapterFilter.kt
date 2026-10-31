package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterFilter(
    val preferences: PreferencesHelper = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
) {
    /** filters chapters based on the manga values */
    fun <T : Chapter> filterChapters(
        chapters: List<T>,
        manga: Manga,
        // KMK -->
        includeBlacklisted: Boolean = false,
        // KMK <--
    ): List<T> {
        val readEnabled = manga.readFilter(preferences) == Manga.CHAPTER_SHOW_READ
        val unreadEnabled = manga.readFilter(preferences) == Manga.CHAPTER_SHOW_UNREAD
        val downloadEnabled = manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_DOWNLOADED
        val notDownloadEnabled = manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        val bookmarkEnabled = manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_BOOKMARKED
        val notBookmarkEnabled = manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED

        // if none of the filters are enabled skip the filtering of them
        val filteredChapters = chapters.filterChaptersByScanlators(manga, includeBlacklisted)
        return if (readEnabled || unreadEnabled || downloadEnabled || notDownloadEnabled || bookmarkEnabled || notBookmarkEnabled) {
            filteredChapters.filter {
                if (readEnabled &&
                    it.read.not() ||
                    (unreadEnabled && it.read) ||
                    (bookmarkEnabled && it.bookmark.not()) ||
                    (notBookmarkEnabled && it.bookmark) ||
                    (downloadEnabled && downloadManager.isChapterDownloaded(it, manga).not()) ||
                    (notDownloadEnabled && downloadManager.isChapterDownloaded(it, manga))
                ) {
                    return@filter false
                }
                return@filter true
            }
        } else {
            filteredChapters
        }
    }

    /** filter chapters for the reader */
    fun <T : Chapter> filterChaptersForReader(
        chapters: List<T>,
        manga: Manga,
        selectedChapter: T? = null,
    ): List<T> {
        var filteredChapters = chapters.filterChaptersByScanlators(manga)
        // if filter prefs aren't enabled don't even filter
        if (!preferences.skipRead() && !preferences.skipFiltered() && !preferences.skipDupe().get()) {
            // KMK -->
            // a revealed blacklisted chapter must still open in the reader
            if (selectedChapter?.id != null && filteredChapters.none { it.id == selectedChapter.id }) {
                return filteredChapters + selectedChapter
            }
            // KMK <--
            return filteredChapters
        }

        if (preferences.skipRead()) {
            filteredChapters = filteredChapters.filter { !it.read }
        }
        if (preferences.skipFiltered()) {
            filteredChapters = filterChapters(filteredChapters, manga)
        }
        if (preferences.skipDupe().get()) {
            filteredChapters =
                filteredChapters
                    .groupBy { it.chapter_number }
                    .map { (_, chapters) ->
                        chapters.find { it.id == selectedChapter?.id }
                            ?: chapters.find { it.scanlator == selectedChapter?.scanlator }
                            ?: chapters.find {
                                val mainScans =
                                    it.scanlator?.split(ChapterUtil.scanlatorSeparator)
                                        ?: return@find false
                                val currScans =
                                    selectedChapter?.scanlator?.split(ChapterUtil.scanlatorSeparator)
                                        ?: return@find false
                                mainScans.any { scanlator -> currScans.contains(scanlator) }
                            }
                            ?: chapters.first()
                    }
        }

        // add the selected chapter to the list in case it was filtered out
        if (selectedChapter?.id != null) {
            val find = filteredChapters.find { it.id == selectedChapter.id }
            if (find == null) {
                val mutableList = filteredChapters.toMutableList()
                mutableList.add(selectedChapter)
                filteredChapters = mutableList.toList()
            }
        }

        return filteredChapters
    }

    companion object {
        /** filters chapters for scanlators */
        fun <T : Chapter> List<T>.filterChaptersByScanlators(
            manga: Manga,
            // KMK -->
            includeBlacklisted: Boolean = false,
            smartMerge: Boolean = smartMergeEnabled(manga),
            // KMK <--
        ): List<T> {
            // KMK -->
            val visible = if (includeBlacklisted) this else filter { !it.blacklisted }
            // KMK <--
            val scanlatorFiltered =
                manga.filtered_scanlators?.let { filteredScanlatorString ->
                    val filteredScanlators = ChapterUtil.getScanlators(filteredScanlatorString)
                    visible.filter { ChapterUtil.getScanlators(it.scanlator).none { group -> filteredScanlators.contains(group) } }
                } ?: visible
            // KMK -->
            return if (smartMerge) scanlatorFiltered.smartMerged(ChapterUtil.getScanlators(manga.scanlator_priority)) else scanlatorFiltered
            // KMK <--
        }

        // KMK -->

        /** Per-manga mode first, then the global setting. */
        fun smartMergeEnabled(manga: Manga): Boolean =
            when (manga.smart_merge_mode) {
                1 -> true
                2 -> false
                else -> Injekt.get<PreferencesHelper>().smartScanlatorMerge().get()
            }

        /**
         * Keeps one chapter per recognized chapter number: the one whose scanlators rank best in [priority]
         * (unlisted scanlators rank last), then the earliest in the source list. Nothing is deleted.
         */
        fun <T : Chapter> List<T>.smartMerged(priority: List<String>): List<T> {
            fun rank(chapter: T): Int =
                ChapterUtil
                    .getScanlators(chapter.scanlator)
                    .minOfOrNull { priority.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                    ?: Int.MAX_VALUE
            val best =
                filter { it.isRecognizedNumber }
                    .groupBy { it.chapter_number }
                    .mapValues { (_, group) -> group.minWithOrNull(compareBy<T>({ rank(it) }, { it.source_order })) }
            return filter { !it.isRecognizedNumber || best[it.chapter_number] === it }
        }
        // KMK <--
    }
}
