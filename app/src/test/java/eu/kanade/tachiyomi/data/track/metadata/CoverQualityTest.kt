package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.CoverCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverQualityTest {
    private fun resource(name: String) = javaClass.getResource("/covers/$name")!!.readBytes()

    // region ImageDimensions
    @Test
    fun `a real png from comick`() {
        // The start of the cover of the series in the tests of the other trackers
        assertEquals(960 to 1246, ImageDimensions.parse(resource("comick_960x1246.png")))
    }

    @Test
    fun `a real jpeg from mangabaka`() {
        assertEquals(690 to 1000, ImageDimensions.parse(resource("mangabaka_690x1000.jpg")))
    }

    @Test
    fun `a jpeg is read even if it is cut off after the header`() {
        val head = resource("mangabaka_690x1000.jpg")
        // the size is in the first segments, the rest of the file isn't needed
        val cut = head.copyOf(2048)
        assertEquals(690 to 1000, ImageDimensions.parse(cut))
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `gif and the kinds of webp`() {
        val gif = bytes('G'.code, 'I'.code, 'F'.code, '8'.code, '9'.code, 'a'.code, 0x2C, 0x01, 0x90, 0x01, 0, 0)
        assertEquals(300 to 400, ImageDimensions.parse(gif))

        fun riff(
            chunk: String,
            body: ByteArray,
        ) = "RIFF".toByteArray() + bytes(0, 0, 0, 0) + "WEBP".toByteArray() + chunk.toByteArray() + bytes(0, 0, 0, 0) + body

        // lossy 640x480: tag, start code, sizes
        val lossy = riff("VP8 ", bytes(0, 0, 0, 0x9D, 0x01, 0x2A, 0x80, 0x02, 0xE0, 0x01) + ByteArray(8))
        assertEquals(640 to 480, ImageDimensions.parse(lossy))

        // lossless 640x480, stored as 639 and 479
        val bits = 639 or (479 shl 14)
        val lossless =
            riff(
                "VP8L",
                bytes(0x2F, bits and 0xFF, (bits shr 8) and 0xFF, (bits shr 16) and 0xFF, (bits shr 24) and 0xFF) + ByteArray(8),
            )
        assertEquals(640 to 480, ImageDimensions.parse(lossless))

        // extended 1000x1500, stored as 999 and 1499
        val extended = riff("VP8X", bytes(0, 0, 0, 0, 999 and 0xFF, 999 shr 8, 0, 1499 and 0xFF, 1499 shr 8, 0) + ByteArray(4))
        assertEquals(1000 to 1500, ImageDimensions.parse(extended))
    }

    @Test
    fun `things that aren't images`() {
        assertNull(ImageDimensions.parse(ByteArray(0)))
        assertNull(ImageDimensions.parse("<html>nope</html>".toByteArray()))
        assertNull(ImageDimensions.parse(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 4, 0, 0)))
    }
    // endregion

    // region CoverChooser
    private fun choose(
        vararg candidates: CoverCandidate,
        sizes: Map<String, Pair<Int, Int>> = emptyMap(),
        measured: MutableList<String> = mutableListOf(),
    ) = runBlocking {
        CoverChooser.choose(candidates.toList()) { url ->
            measured += url
            sizes[url]
        }
    }

    @Test
    fun `the cover with the most pixels wins`() {
        val best =
            choose(
                CoverCandidate("mangabaka", 690, 1000, source = 11),
                CoverCandidate("comick", 960, 1246, source = 12),
                CoverCandidate("anilist", 460, 650, source = 2),
            )
        assertEquals("comick", best!!.url)
    }

    @Test
    fun `covers offered without a size are measured`() {
        val measured = mutableListOf<String>()
        val best =
            choose(
                CoverCandidate("known", 690, 1000),
                CoverCandidate("unknown-big"),
                CoverCandidate("unknown-small"),
                sizes = mapOf("unknown-big" to (1200 to 1800), "unknown-small" to (200 to 300)),
                measured = measured,
            )
        assertEquals("unknown-big", best!!.url)
        assertEquals(listOf("unknown-big", "unknown-small"), measured)
        assertEquals(1200, best.width)
    }

    @Test
    fun `a size that can't be told is the same as not knowing`() {
        // the first tracker stays when nothing is known about any of them
        assertEquals("a", choose(CoverCandidate("a"), CoverCandidate("b"))!!.url)
        // but a cover that is known to be big beats one that can't be measured
        assertEquals("b", choose(CoverCandidate("a"), CoverCandidate("b", 100, 150))!!.url)
    }

    @Test
    fun `of covers of the same size the first tracker wins`() {
        assertEquals("first", choose(CoverCandidate("first", 600, 900), CoverCandidate("second", 600, 900))!!.url)
    }

    @Test
    fun `a banner doesn't beat a cover`() {
        val best = choose(CoverCandidate("cover", 400, 600), CoverCandidate("banner", 1900, 400))
        assertEquals("cover", best!!.url)
        // unless it is all there is
        assertEquals("banner", choose(CoverCandidate("banner", 1900, 400))!!.url)
    }

    @Test
    fun `one cover isn't measured and no cover is none`() {
        val measured = mutableListOf<String>()
        assertEquals("only", choose(CoverCandidate("only"), measured = measured)!!.url)
        assertEquals(emptyList<String>(), measured)
        assertNull(choose())
    }
    // endregion
}
