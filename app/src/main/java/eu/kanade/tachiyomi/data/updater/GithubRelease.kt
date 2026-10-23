package eu.kanade.tachiyomi.data.updater

import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Release object.
 * Contains information about the latest release from GitHub.
 *
 * @param version version of latest release.
 * @param info log of latest release.
 * @param assets assets of latest release.
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val version: String,
    @SerialName("body") val info: String,
    @SerialName("html_url") val releaseLink: String,
    @SerialName("prerelease") val preRelease: Boolean?,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("assets") private val assets: List<Assets>,
) {
    /** Whether this is the rolling nightly release, see [NightlyUpdate]. */
    val isNightly: Boolean
        get() = version == NightlyUpdate.TAG

    /** The commit a nightly was built from. */
    val nightlyCommit: String?
        get() = NightlyUpdate.commitOf(assets.map { it.downloadLink.substringAfterLast('/') })

    /**
     * Get download link of latest release from the assets.
     * @return download link of latest release.
     */
    val downloadLink: String
        get() = downloadLinkFor(Build.SUPPORTED_ABIS[0])

    /** The file made for [abi], or the universal one if there is none for it. */
    fun downloadLinkFor(abi: String?): String {
        val apkVariant =
            when (abi) {
                "arm64-v8a" -> "-arm64-v8a"
                "armeabi-v7a" -> "-armeabi-v7a"
                "x86" -> "-x86"
                "x86_64" -> "-x86_64"
                else -> "-universal"
            }

        return assets.find { it.downloadLink.contains("tachiyomij2k$apkVariant-") }?.downloadLink
            ?: assets.find { it.downloadLink.contains("tachiyomij2k-universal-") }?.downloadLink
            ?: assets[0].downloadLink
    }

    /**
     * Assets class containing download url.
     * @param downloadLink download url.
     */
    @Serializable
    data class Assets(
        @SerialName("browser_download_url") val downloadLink: String,
    )
}
