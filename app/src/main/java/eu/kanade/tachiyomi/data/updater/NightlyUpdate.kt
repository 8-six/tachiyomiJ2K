package eu.kanade.tachiyomi.data.updater

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Nightly builds all live in one rolling GitHub release called [TAG] that is replaced by every build. Its files are
 * named `tachiyomij2k-<abi>-nightly-<yyyymmdd>-<commit>.apk`, so the commit a nightly was built from is in the name.
 */
object NightlyUpdate {
    const val TAG = "nightly"

    private val commitInAssetName = Regex("""nightly-\d{8}-([0-9a-f]{7,40})""")
    private val buildTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

    /** The commit a nightly release was built from, read from the names of its files. */
    fun commitOf(assetNames: List<String>): String? = assetNames.firstNotNullOfOrNull { commitInAssetName.find(it)?.groupValues?.get(1) }

    /** Whether [url] is the download of a nightly build. */
    fun isNightlyUrl(url: String): Boolean = commitInAssetName.containsMatchIn(url.substringAfterLast('/'))

    /**
     * Whether a published nightly should replace the installed build.
     *
     * It does if it was built from another commit and published after the installed build was made. The second part
     * keeps a build someone made themselves, which can be newer than the latest nightly, from being "updated" back.
     *
     * @param releaseCommit the commit of the nightly, see [commitOf].
     * @param releasePublishedAt when it was published, like `2026-10-23T12:31:18Z`.
     * @param installedCommit `BuildConfig.COMMIT_SHA`.
     * @param installedBuildTime `BuildConfig.BUILD_TIME`, like `2026-10-23T12:20Z`.
     */
    fun isNewer(
        releaseCommit: String?,
        releasePublishedAt: String?,
        installedCommit: String,
        installedBuildTime: String,
    ): Boolean {
        // Short commits can be shown at different lengths
        if (releaseCommit != null &&
            installedCommit.isNotBlank() &&
            (releaseCommit.startsWith(installedCommit) || installedCommit.startsWith(releaseCommit))
        ) {
            return false
        }

        val published = releasePublishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val built = runCatching { LocalDateTime.parse(installedBuildTime, buildTimeFormat).toInstant(ZoneOffset.UTC) }.getOrNull()
        return if (published != null && built != null) {
            published.isAfter(built)
        } else {
            // Without both times the commit is all there is to go by
            releaseCommit != null
        }
    }
}
