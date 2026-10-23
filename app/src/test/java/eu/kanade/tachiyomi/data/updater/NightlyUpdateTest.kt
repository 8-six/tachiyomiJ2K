package eu.kanade.tachiyomi.data.updater

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the real nightly release the CI published to github.com/8-six/tachiyomiJ2K. */
class NightlyUpdateTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val release by lazy {
        json.decodeFromString<GithubRelease>(javaClass.getResource("/updater/nightly_release.json")!!.readText())
    }

    // the nightly was published at 2026-10-23T12:31:18Z
    private fun isNewer(
        installedCommit: String,
        installedBuildTime: String,
        releaseCommit: String? = release.nightlyCommit,
        publishedAt: String? = release.publishedAt,
    ) = NightlyUpdate.isNewer(releaseCommit, publishedAt, installedCommit, installedBuildTime)

    @Test
    fun `the real release is read as a nightly`() {
        assertTrue(release.isNightly)
        assertEquals("nightly", release.version)
        assertEquals("afac8b0b2", release.nightlyCommit)
        assertEquals("2026-10-23T12:31:18Z", release.publishedAt)
        assertTrue(release.preRelease == true)
    }

    @Test
    fun `the right file is picked for each cpu`() {
        assertTrue(release.downloadLinkFor("arm64-v8a").endsWith("tachiyomij2k-arm64-v8a-nightly-20261023-afac8b0b2.apk"))
        assertTrue(release.downloadLinkFor("armeabi-v7a").endsWith("tachiyomij2k-armeabi-v7a-nightly-20261023-afac8b0b2.apk"))
        assertTrue(release.downloadLinkFor("x86_64").endsWith("tachiyomij2k-x86_64-nightly-20261023-afac8b0b2.apk"))
        // x86 must not take the x86_64 file
        assertTrue(release.downloadLinkFor("x86").endsWith("tachiyomij2k-x86-nightly-20261023-afac8b0b2.apk"))
        // a cpu there's no file for gets the one that runs on all of them, not whichever is first
        assertTrue(release.downloadLinkFor("riscv64").endsWith("tachiyomij2k-universal-nightly-20261023-afac8b0b2.apk"))
        assertTrue(release.downloadLinkFor(null).endsWith("tachiyomij2k-universal-nightly-20261023-afac8b0b2.apk"))
    }

    @Test
    fun `the installed nightly is up to date`() {
        assertFalse(isNewer(installedCommit = "afac8b0b2", installedBuildTime = "2026-10-23T12:20Z"))
        // the commit can be shown shorter or longer
        assertFalse(isNewer(installedCommit = "afac8b0", installedBuildTime = "2026-10-23T12:20Z"))
        assertFalse(isNewer(installedCommit = "afac8b0b278cb862ebcd28162336db0512df38dd", installedBuildTime = "2026-10-23T12:20Z"))
    }

    @Test
    fun `an older nightly is updated`() {
        assertTrue(isNewer(installedCommit = "9a068d44e", installedBuildTime = "2026-10-23T08:20Z"))
        assertTrue(isNewer(installedCommit = "9a068d44e", installedBuildTime = "2026-10-01T00:00Z"))
    }

    @Test
    fun `a build made after the nightly isn't replaced by it`() {
        // someone built their own version later, from a commit the nightly doesn't have
        assertFalse(isNewer(installedCommit = "1234567ab", installedBuildTime = "2026-10-24T09:00Z"))
    }

    @Test
    fun `without times the commit decides`() {
        assertTrue(isNewer(installedCommit = "9a068d44e", installedBuildTime = "unknown", publishedAt = null))
        assertFalse(isNewer(installedCommit = "afac8b0b2", installedBuildTime = "unknown", publishedAt = null))
        // nothing known about the release at all
        assertFalse(isNewer(installedCommit = "9a068d44e", installedBuildTime = "unknown", releaseCommit = null, publishedAt = null))
    }

    @Test
    fun `commits and download links are told apart from other names`() {
        assertEquals("abc1234", NightlyUpdate.commitOf(listOf("notes.txt", "tachiyomij2k-x86-nightly-20261023-abc1234.apk")))
        assertNull(NightlyUpdate.commitOf(listOf("tachiyomij2k-1.8.1.apk")))
        assertTrue(
            NightlyUpdate.isNightlyUrl("https://github.com/x/y/releases/download/nightly/tachiyomij2k-x86-nightly-20261023-abc1234.apk"),
        )
        assertFalse(NightlyUpdate.isNightlyUrl("https://github.com/x/y/releases/download/v1.8.1/tachiyomij2k-1.8.1.apk"))
        // the release tag alone is no proof, the file name is
        assertFalse(NightlyUpdate.isNightlyUrl("https://github.com/x/nightly/releases/download/v1/tachiyomij2k-1.apk"))
    }
}
