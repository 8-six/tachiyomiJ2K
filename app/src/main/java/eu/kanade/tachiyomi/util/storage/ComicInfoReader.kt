package eu.kanade.tachiyomi.util.storage

import com.github.junrar.Archive
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Reads chapter level metadata from a ComicInfo.xml file stored in a directory, zip/cbz or rar/cbr.
 */
object ComicInfoReader {
    const val FILE_NAME = "ComicInfo.xml"

    data class ChapterInfo(
        val title: String?,
        val number: Float?,
        val scanlator: String?,
    )

    fun read(file: File): ChapterInfo? =
        try {
            when {
                file.isDirectory -> {
                    File(file, FILE_NAME)
                        .takeIf { it.isFile }
                        ?.inputStream()
                        ?.use { parse(it) }
                }
                file.extension.equals("zip", true) || file.extension.equals("cbz", true) -> {
                    ZipFile(file).use { zip ->
                        val entry =
                            zip.entries().asSequence().firstOrNull {
                                !it.isDirectory && it.name.substringAfterLast('/').equals(FILE_NAME, true)
                            }
                        entry?.let { e -> zip.getInputStream(e).use { parse(it) } }
                    }
                }
                file.extension.equals("rar", true) || file.extension.equals("cbr", true) -> {
                    Archive(file).use { archive ->
                        val header =
                            archive.fileHeaders.firstOrNull {
                                !it.isDirectory &&
                                    it.fileName
                                        .substringAfterLast('/')
                                        .substringAfterLast('\\')
                                        .equals(FILE_NAME, true)
                            }
                        header?.let { h -> archive.getInputStream(h).use { parse(it) } }
                    }
                }
                else -> null
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed to read $FILE_NAME from ${file.name}")
            null
        }

    private fun parse(stream: InputStream): ChapterInfo? {
        val doc = Jsoup.parse(stream.bufferedReader().readText(), "", Parser.xmlParser())
        val root = doc.selectFirst("ComicInfo") ?: return null

        fun text(tag: String) =
            root
                .selectFirst(tag)
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        return ChapterInfo(
            title = text("Title"),
            number = text("Number")?.toFloatOrNull(),
            scanlator = text("Translator"),
        )
    }

    fun apply(
        file: File,
        chapter: SChapter,
    ) {
        val info = read(file) ?: return
        info.title?.let { chapter.name = it }
        info.number?.let { chapter.chapter_number = it }
        info.scanlator?.let { chapter.scanlator = it }
    }
}
