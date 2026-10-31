package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.util.chapter.ChapterFilter.Companion.filterChaptersByScanlators
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
        assertEquals(listOf("/c/1"), chapters.filterChaptersByScanlators(manga).map { it.url })
        assertEquals(2, chapters.filterChaptersByScanlators(manga, includeBlacklisted = true).size)
    }

    @Test
    fun `blacklist flag survives a protobuf round trip and defaults to false`() {
        val encoded = ProtoBuf.encodeToByteArray(BackupChapter.serializer(), BackupChapter(url = "/c/1", name = "One", blacklisted = true))
        assertTrue(ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), encoded).toChapterImpl().blacklisted)
        val old = ProtoBuf.encodeToByteArray(BackupChapter.serializer(), BackupChapter(url = "/c/2", name = "Two"))
        assertEquals(false, ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), old).blacklisted)
    }
}
