package eu.kanade.tachiyomi.util.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComicInfoReaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val xml =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <Title>The Beginning &amp; More</Title>
          <Series>Some Series</Series>
          <Number>12.5</Number>
          <Translator>Group A</Translator>
        </ComicInfo>
        """.trimIndent()

    @Test
    fun `reads from a cbz`() {
        val cbz = tmp.newFile("chapter.cbz")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("ComicInfo.xml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }

        val info = ComicInfoReader.read(cbz)!!
        assertEquals("The Beginning & More", info.title)
        assertEquals(12.5f, info.number)
        assertEquals("Group A", info.scanlator)
    }

    @Test
    fun `reads from a folder`() {
        val dir = tmp.newFolder("chapter")
        File(dir, "ComicInfo.xml").writeText(xml)
        assertEquals("Group A", ComicInfoReader.read(dir)!!.scanlator)
    }

    @Test
    fun `missing file or fields give nothing`() {
        assertNull(ComicInfoReader.read(tmp.newFolder("empty")))

        val dir = tmp.newFolder("partial")
        File(dir, "ComicInfo.xml").writeText("<ComicInfo><Number>abc</Number><Title> </Title></ComicInfo>")
        val info = ComicInfoReader.read(dir)!!
        assertNull(info.title)
        assertNull(info.number)
        assertNull(info.scanlator)
    }

    @Test
    fun `broken xml is ignored`() {
        val dir = tmp.newFolder("broken")
        File(dir, "ComicInfo.xml").writeText("not xml at all")
        assertNull(ComicInfoReader.read(dir))
    }
}
