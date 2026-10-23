package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.CoverCandidate
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.Field
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCombinerTest {
    private val all = Field.entries.toSet()

    /** What each tracker says. A tracker is a number. */
    private val says =
        mutableMapOf<Int, TrackMangaMetadata>().apply {
            this[1] =
                TrackMangaMetadata(
                    title = "One",
                    authors = "A1",
                    tags = listOf("t1"),
                    covers = listOf(CoverCandidate("c1", 690, 1000, source = 1)),
                    coverUrl = "c1",
                )
            this[2] =
                TrackMangaMetadata(
                    title = "Two",
                    artists = "R2",
                    description = "D2",
                    covers = listOf(CoverCandidate("c2", 960, 1246, source = 2)),
                    coverUrl = "c2",
                )
            this[3] = TrackMangaMetadata(title = "Three", status = 1)
        }

    private val asked = mutableListOf<Int>()

    private fun combine(
        vararg sources: Pair<Int, Set<Field>>,
        wanted: Set<Field> = all,
        fillGaps: Boolean = true,
        wantAllCovers: Boolean = false,
        failing: Set<Int> = emptySet(),
    ) = runBlocking {
        asked.clear()
        MetadataCombiner.combine(
            sources = sources.map { MetadataCombiner.Source(it.first, it.second) },
            wanted = wanted,
            fillGaps = fillGaps,
            wantAllCovers = wantAllCovers,
            idOf = { it },
            fetch = { id ->
                asked += id
                if (id in failing) throw Exception("down $id")
                // like a real tracker only gives what it may
                says.getValue(id).let { m -> sources.first { it.first == id }.second.let(m::filter) }
            },
        )
    }

    @Test
    fun `a tracker that has everything is the only one asked`() {
        says[1] =
            TrackMangaMetadata(
                title = "t",
                authors = "a",
                artists = "r",
                description = "d",
                coverUrl = "c",
                tags = listOf("x"),
                status = 1,
            )
        val result = combine(1 to all, 2 to all)
        assertEquals(listOf(1), asked)
        assertEquals("t", result.metadata!!.title)
    }

    @Test
    fun `what a tracker lacks is taken from the next`() {
        val m = combine(1 to all, 2 to all).metadata!!
        assertEquals(listOf(1, 2), asked)
        // the first has the title and authors, the second adds what is missing
        assertEquals("One", m.title)
        assertEquals("A1", m.authors)
        assertEquals("R2", m.artists)
        assertEquals("D2", m.description)
    }

    @Test
    fun `without filling gaps only the first is asked`() {
        val m = combine(1 to all, 2 to all, fillGaps = false).metadata!!
        assertEquals(listOf(1), asked)
        assertNull(m.artists)
    }

    @Test
    fun `a tracker that can't add anything isn't asked`() {
        // tracker 2 may only give the cover, and the wanted details are all there once the cover is
        says[1] = says.getValue(1).copy(artists = "r", description = "d", status = 1)
        combine(1 to all, 2 to setOf(Field.COVER))
        assertEquals(listOf(1), asked)
        // not even the first one when it has nothing to give
        combine(1 to emptySet(), 2 to all)
        assertEquals(listOf(2), asked)
    }

    @Test
    fun `all trackers with a cover are asked when covers are compared`() {
        says[1] = says.getValue(1).copy(artists = "r", description = "d", status = 1)
        val m = combine(1 to all, 2 to all, 3 to setOf(Field.TITLE), wantAllCovers = true).metadata!!
        // 3 has no cover to offer, and has nothing missing to give
        assertEquals(listOf(1, 2), asked)
        assertEquals(listOf("c1", "c2"), m.coverCandidates().map { it.url })
    }

    @Test
    fun `a tracker can be the source of only the cover`() {
        // details from 1, the cover from 2
        says[1] = says.getValue(1).copy(artists = "r", description = "d", status = 1)
        val m = combine(1 to (all - Field.COVER), 2 to setOf(Field.COVER), wantAllCovers = true).metadata!!
        assertEquals("One", m.title)
        assertEquals(listOf("c2"), m.coverCandidates().map { it.url })
        // and nothing else of 2 was taken
        assertNull(says.getValue(2).filter(setOf(Field.COVER)).title)
    }

    @Test
    fun `a tracker that may not give the cover can't win it`() {
        val filtered = says.getValue(2).filter(all - Field.COVER)
        assertEquals(emptyList<CoverCandidate>(), filtered.coverCandidates())
        assertNull(filtered.coverUrl)
    }

    @Test
    fun `a tracker that fails doesn't stop the others and is reported`() {
        val result = combine(1 to all, 2 to all, failing = setOf(1))
        assertEquals(listOf(1, 2), asked)
        assertEquals("Two", result.metadata!!.title)
        assertEquals(listOf(1 to "down 1"), result.failures)
    }

    @Test
    fun `when every tracker fails there is nothing`() {
        val result = combine(1 to all, 2 to all, failing = setOf(1, 2))
        assertNull(result.metadata)
        assertEquals(listOf(1, 2), result.failures.map { it.first })
    }

    @Test
    fun `without any source there is nothing`() {
        assertNull(combine().metadata)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `the order of the trackers decides which one provides a detail`() {
        assertEquals("Two", combine(2 to all, 1 to all).metadata!!.title)
        assertEquals("One", combine(1 to all, 2 to all).metadata!!.title)
    }
}
