package eu.kanade.tachiyomi.data.merge

import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergedChaptersTest {
    private fun chapter(
        number: Float,
        name: String = "Chapter $number",
        scanlator: String? = null,
        url: String = "/c/$number",
    ): SChapter =
        SChapter.create().apply {
            this.url = url
            this.name = name
            this.scanlator = scanlator
            chapter_number = number
        }

    private fun member(
        mangaId: Long,
        priority: Int,
        vararg chapters: SChapter,
        sourceId: Long = mangaId * 10,
        sourceName: String = "Source $mangaId",
    ) = MergeMemberChapters(mangaId, "/manga/$mangaId", sourceId, sourceName, priority, chapters.toList())

    private fun numbers(list: List<SChapter>) = list.map { it.chapter_number }

    @Test
    fun `chapters of both members are combined newest first`() {
        val merged =
            MergedChapters.merge(
                listOf(
                    member(1, 0, chapter(1f), chapter(2f), chapter(3f)),
                    member(2, 1, chapter(3f), chapter(4f), chapter(5f)),
                ),
            )
        assertEquals(listOf(5f, 4f, 3f, 2f, 1f), numbers(merged))
    }

    @Test
    fun `the member with the lowest priority provides a duplicated chapter`() {
        val merged =
            MergedChapters.merge(
                listOf(
                    member(2, 1, chapter(1f, url = "/b/1")),
                    member(1, 0, chapter(1f, url = "/a/1")),
                ),
            )
        val only = merged.single()
        assertEquals(MergedChapterUrl.encode(10, "/manga/1", "/a/1"), only.url)
    }

    @Test
    fun `all chapters with a number are kept from the member that provides it`() {
        val merged =
            MergedChapters.merge(
                listOf(
                    member(1, 0, chapter(1f, scanlator = "A", url = "/a1"), chapter(1f, scanlator = "B", url = "/a2")),
                    member(2, 1, chapter(1f, url = "/b1")),
                ),
            )
        assertEquals(2, merged.size)
        assertEquals(listOf("A", "B"), merged.map { it.scanlator })
    }

    @Test
    fun `chapters without a number only match by name`() {
        val merged =
            MergedChapters.merge(
                listOf(
                    member(1, 0, chapter(-1f, name = "Extra"), chapter(1f)),
                    member(2, 1, chapter(-1f, name = " extra "), chapter(-1f, name = "Omake")),
                ),
            )
        assertEquals(listOf(1f, -1f, -1f), numbers(merged))
        assertEquals(listOf("Chapter 1.0", "Extra", "Omake"), merged.map { it.name })
    }

    @Test
    fun `the source is kept in the url and used as scanlator when there is none`() {
        val merged =
            MergedChapters.merge(
                listOf(member(7, 0, chapter(1f, url = "/x?a=1|2"), chapter(2f, scanlator = "Group"), sourceId = 99, sourceName = "Foo")),
            )
        val withoutGroup = merged.first { it.chapter_number == 1f }
        assertEquals("Foo", withoutGroup.scanlator)
        assertEquals("Group", merged.first { it.chapter_number == 2f }.scanlator)

        val origin = MergedChapterUrl.decode(withoutGroup.url)!!
        assertEquals(99L, origin.sourceId)
        assertEquals("/manga/7", origin.mangaUrl)
        assertEquals("/x?a=1|2", origin.url)
    }

    @Test
    fun `no members or no chapters give an empty list`() {
        assertEquals(emptyList<SChapter>(), MergedChapters.merge(emptyList()))
        assertEquals(emptyList<SChapter>(), MergedChapters.merge(listOf(member(1, 0))))
    }

    @Test
    fun `malformed urls are not decoded`() {
        assertNull(MergedChapterUrl.decode("/just/a/path"))
        assertNull(MergedChapterUrl.decode("a\n/manga\n/c"))
        assertNull(MergedChapterUrl.decode("1\n/manga"))
    }
}
