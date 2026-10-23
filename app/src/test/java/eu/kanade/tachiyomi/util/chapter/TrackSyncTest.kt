package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSyncTest {
    /** Chapters as the source lists them: the newest has source order 0. */
    private fun chapters(
        vararg numbers: Float,
        read: Set<Float> = emptySet(),
    ): List<Chapter> =
        numbers.sortedDescending().mapIndexed { index, number ->
            Chapter.create().apply {
                name = "Chapter $number"
                url = "/$number"
                chapter_number = number
                source_order = index
                this.read = number in read
            }
        }

    private fun numbers(list: List<Chapter>) = list.map { it.chapter_number }.sorted()

    @Test
    fun `local chapters follow the tracker`() {
        val result = computeTrackSync(chapters(1f, 2f, 3f, 4f, 5f), remoteLastRead = 3f)
        assertEquals(listOf(1f, 2f, 3f), numbers(result.chaptersToMarkRead))
        assertEquals(0f, result.localLastRead)
    }

    @Test
    fun `tracker is pushed forward when local is ahead`() {
        val result = computeTrackSync(chapters(1f, 2f, 3f, 4f, read = setOf(1f, 2f, 3f)), remoteLastRead = 1f)
        assertEquals(3f, result.localLastRead)
        assertEquals(emptyList<Float>(), numbers(result.chaptersToMarkRead).filter { it > 1f })
    }

    @Test
    fun `already read chapters are not marked again`() {
        val result = computeTrackSync(chapters(1f, 2f, 3f, read = setOf(1f, 2f, 3f)), remoteLastRead = 3f)
        assertEquals(emptyList<Float>(), numbers(result.chaptersToMarkRead))
    }

    @Test
    fun `a gap in reading stops the local progress`() {
        val result = computeTrackSync(chapters(1f, 2f, 3f, 4f, read = setOf(1f, 2f, 4f)), remoteLastRead = 0f)
        assertEquals(2f, result.localLastRead)
    }

    @Test
    fun `tracker never lowers progress and nothing is marked past it`() {
        val result = computeTrackSync(chapters(1f, 2f, 3f, read = setOf(1f)), remoteLastRead = 5f)
        assertEquals(listOf(2f, 3f), numbers(result.chaptersToMarkRead))
        assertEquals(1f, result.localLastRead)
    }

    @Test
    fun `odd numbering stops marking instead of marking everything`() {
        // Volume style numbering: the source lists 1, 2, 1, 2 (oldest first), so the second "1" breaks the run
        val list =
            listOf(1f, 2f, 1f, 2f).mapIndexed { i, n ->
                Chapter.create().apply {
                    name = "c$i"
                    url = "/$i"
                    chapter_number = n
                    source_order = 3 - i
                }
            }
        val result = computeTrackSync(list, remoteLastRead = 2f)
        assertEquals(2, result.chaptersToMarkRead.size)
    }

    @Test
    fun `unrecognized chapter numbers are ignored`() {
        val list =
            chapters(1f, 2f) +
                Chapter.create().apply {
                    name = "Extra"
                    url = "/x"
                    chapter_number = -1f
                    source_order = 9
                }
        val result = computeTrackSync(list, remoteLastRead = 2f)
        assertEquals(listOf(1f, 2f), numbers(result.chaptersToMarkRead))
    }
}
