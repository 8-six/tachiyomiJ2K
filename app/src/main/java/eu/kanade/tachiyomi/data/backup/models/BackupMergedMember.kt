package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * A manga a merged manga is made of. Those aren't in the library, so the merged manga's backup has to carry them.
 * Only what is needed to find or recreate the manga is kept, its chapters are fetched again.
 */
@Serializable
data class BackupMergedMember(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var thumbnailUrl: String? = null,
    @ProtoNumber(5) var priority: Int = 0,
    @ProtoNumber(6) var isInfoManga: Boolean = false,
    @ProtoNumber(7) var getChapterUpdates: Boolean = true,
)
