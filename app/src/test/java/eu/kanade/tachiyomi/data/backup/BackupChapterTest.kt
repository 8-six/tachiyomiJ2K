package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupChapterTest {
    @Test
    fun `bookmark color survives a protobuf round trip`() {
        val chapter = BackupChapter(url = "/c/1", name = "One", bookmark = true, bookmarkColor = 0xFFE53935.toInt())
        val decoded =
            ProtoBuf.decodeFromByteArray(
                BackupChapter.serializer(),
                ProtoBuf.encodeToByteArray(BackupChapter.serializer(), chapter),
            )
        assertEquals(chapter.bookmarkColor, decoded.bookmarkColor)
        assertEquals(chapter.bookmarkColor, decoded.toChapterImpl().bookmark_color)
    }

    @Test
    fun `chapters from older backups default to no bookmark color`() {
        val old = BackupChapter(url = "/c/1", name = "One", bookmark = true)
        val decoded = ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), ProtoBuf.encodeToByteArray(BackupChapter.serializer(), old))
        assertEquals(0, decoded.toChapterImpl().bookmark_color)
    }
}
