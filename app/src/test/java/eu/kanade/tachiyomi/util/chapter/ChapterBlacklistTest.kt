package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.util.chapter.ChapterFilter.Companion.filterChaptersByScanlators
import eu.kanade.tachiyomi.util.chapter.ChapterFilter.Companion.smartMerged
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterBlacklistTest {
    private val chapters =
        listOf(
            ChapterImpl().apply { url = "/c/1" },
            ChapterImpl().apply {
                url = "/c/1-extra"
                blacklisted = true
            },
        )

    @Test
    fun `blacklisted chapters are hidden unless revealed`() {
        val manga = MangaImpl()
        assertEquals(listOf("/c/1"), chapters.filterChaptersByScanlators(manga, smartMerge = false).map { it.url })
        assertEquals(2, chapters.filterChaptersByScanlators(manga, includeBlacklisted = true, smartMerge = false).size)
    }

    @Test
    fun `blacklist flag survives a protobuf round trip and defaults to false`() {
        val encoded = ProtoBuf.encodeToByteArray(BackupChapter.serializer(), BackupChapter(url = "/c/1", name = "One", blacklisted = true))
        assertTrue(ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), encoded).toChapterImpl().blacklisted)
        val old = ProtoBuf.encodeToByteArray(BackupChapter.serializer(), BackupChapter(url = "/c/2", name = "Two"))
        assertEquals(false, ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), old).blacklisted)
    }

    @Test
    fun `smart merge keeps the best ranked scanlator per chapter number`() {
        fun chapter(
            number: Float,
            group: String,
            order: Int,
        ) = ChapterImpl().apply {
            url = "/$group/$number"
            chapter_number = number
            scanlator = group
            source_order = order
        }
        val list = listOf(chapter(1f, "A", 0), chapter(1f, "B", 1), chapter(2f, "A", 2), chapter(-1f, "A", 3))
        assertEquals(listOf("/B/1.0", "/A/2.0", "/A/-1.0"), list.smartMerged(listOf("B", "A")).map { it.url })
        assertEquals(listOf("/A/1.0", "/A/2.0", "/A/-1.0"), list.smartMerged(emptyList()).map { it.url })
    }
}
