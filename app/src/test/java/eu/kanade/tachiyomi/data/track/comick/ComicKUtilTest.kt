package eu.kanade.tachiyomi.data.track.comick

import eu.kanade.tachiyomi.data.track.comick.dto.ComickLibraryEntry
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ComicKUtilTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a hid is a number that can be turned back into the same hid`() {
        // The HID of the series the other tests use
        val id = "cjee7NwJ".asHidToLong()
        assertEquals("cjee7NwJ", id.toHid())
        listOf("kLm_k2Ri", "2mh3cADX", "AAAAAAAA", "________").forEach { assertEquals(it, it.asHidToLong().toHid()) }
    }

    @Test
    fun `different hids are different ids`() {
        assertTrue("cjee7NwJ".asHidToLong() != "kLm_k2Ri".asHidToLong())
    }

    @Test
    fun `things that aren't a hid are refused`() {
        try {
            "short".asHidToLong()
            fail("expected an exception")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            (-1L).toHid()
            fail("expected an exception")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `statuses go there and back`() {
        listOf(ComicK.READING, ComicK.COMPLETED, ComicK.ON_HOLD, ComicK.DROPPED, ComicK.PLAN_TO_READ).forEach {
            assertEquals(it, it.toApiListStatus().fromApiListStatus())
        }
        // ComicK numbers them as reading, completed, on hold, dropped, plan to read
        assertEquals(5, ComicK.PLAN_TO_READ.toApiListStatus())
        assertEquals(ComicK.DROPPED, 4.fromApiListStatus())
        // a status ComicK adds later doesn't break reading a library
        assertEquals(ComicK.READING, 99.fromApiListStatus())
    }

    @Test
    fun `a library entry becomes a track`() {
        val entry =
            json.decodeFromString<ComickLibraryEntry>(
                """{"data":{"hid":"cjee7NwJ","title":"The Extra's Academy Survival Guide","status":3,"progress":{"number":"41.5"},"rating":7.0,"extra":1}}""",
            )
        val track = entry.toTrack(12)
        assertEquals(12, track.sync_id)
        assertEquals("cjee7NwJ".asHidToLong(), track.media_id)
        assertEquals(ComicK.ON_HOLD, track.status)
        assertEquals(41.5f, track.last_chapter_read)
        assertEquals(7f, track.score)
    }

    @Test
    fun `a library entry without progress or rating`() {
        val track =
            json.decodeFromString<ComickLibraryEntry>("""{"data":{"hid":"cjee7NwJ","title":"T","status":1}}""").toTrack(12)
        assertEquals(0f, track.last_chapter_read)
        assertEquals(0f, track.score)
    }

    @Test
    fun `a token expires a minute early`() {
        val now = 1_000_000_000_000L
        val fresh = ComickOAuth(accessToken = "a", expiresIn = 900, receivedAt = now)
        assertFalse(fresh.isExpired(now))
        assertFalse(fresh.isExpired(now + 839_000))
        assertTrue(fresh.isExpired(now + 841_000))
        // an absolute time wins over how long it lasts
        assertTrue(ComickOAuth(accessToken = "a", expiresIn = 900, expiresAt = now / 1000 + 10, receivedAt = now).isExpired(now))
        // without either it's 15 minutes
        assertFalse(ComickOAuth(accessToken = "a", receivedAt = now).isExpired(now + 800_000))
    }

    @Test
    fun `a token keeps when it was received after being saved`() {
        // else a token that is restored would count its lifetime from the restore
        val token = ComickOAuth(accessToken = "a", refreshToken = "r", expiresIn = 900, receivedAt = 1234L)
        val restored = json.decodeFromString<ComickOAuth>(json.encodeToString(token))
        assertEquals(1234L, restored.receivedAt)
    }
}
