package eu.kanade.tachiyomi.data.merge

import eu.kanade.tachiyomi.source.model.SChapter

/** The chapters one manga contributes to a merge. */
class MergeMemberChapters(
    val mangaId: Long,
    val mangaUrl: String,
    val sourceId: Long,
    val sourceName: String,
    val priority: Int,
    val chapters: List<SChapter>,
)

object MergedChapters {
    /**
     * Combines the chapters of several manga into one list, newest first.
     *
     * A chapter number is provided by the member with the lowest priority that has it, and the same chapter from the
     * others is dropped. All chapters with that number are kept from that member, as it may list the same chapter
     * once per scanlator. Chapters without a recognized number can't be compared, so they only match by name.
     */
    fun merge(members: List<MergeMemberChapters>): List<SChapter> {
        val ordered = members.sortedBy { it.priority }

        val ownerOfNumber = HashMap<Float, Long>()
        val ownerOfName = HashMap<String, Long>()
        ordered.forEach { member ->
            member.chapters.forEach { chapter ->
                if (chapter.chapter_number >= 0f) {
                    ownerOfNumber.putIfAbsent(chapter.chapter_number, member.mangaId)
                } else {
                    ownerOfName.putIfAbsent(chapter.name.normalizedName(), member.mangaId)
                }
            }
        }

        val numbered = mutableListOf<SChapter>()
        val unnumbered = mutableListOf<SChapter>()
        ordered.forEach { member ->
            member.chapters.forEach { chapter ->
                val isRecognized = chapter.chapter_number >= 0f
                val owner =
                    if (isRecognized) ownerOfNumber[chapter.chapter_number] else ownerOfName[chapter.name.normalizedName()]
                if (owner != member.mangaId) return@forEach
                val merged = member.toMergedChapter(chapter)
                if (isRecognized) numbered += merged else unnumbered += merged
            }
        }

        // Stable, so chapters with the same number stay in the order their source listed them
        return numbered.sortedByDescending { it.chapter_number } + unnumbered
    }

    private fun String.normalizedName() = trim().lowercase()

    private fun MergeMemberChapters.toMergedChapter(chapter: SChapter): SChapter =
        SChapter.create().apply {
            url = MergedChapterUrl.encode(sourceId, mangaUrl, chapter.url)
            name = chapter.name
            scanlator = chapter.scanlator?.takeIf { it.isNotBlank() } ?: sourceName
            date_upload = chapter.date_upload
            chapter_number = chapter.chapter_number
        }
}
