package eu.kanade.tachiyomi.data.track.comick

import java.util.Base64

// ComicK identifies a title by a HID: the base64url of a 48 bit number. The number is what's kept as the tracker's id,
// which loses nothing and can't collide (it isn't a hash of the title), as ComicK's guidance for trackers asks.
internal fun String.asHidToLong(): Long {
    require(length == 8) { "A HID has 8 characters, got $length: $this" }
    return Base64
        .getUrlDecoder()
        .decode(this)
        .fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
}

internal fun Long.toHid(): String {
    require(this in 0 until (1L shl 48)) { "Not in the 48 bit range of a HID: $this" }
    return Base64.getUrlEncoder().encodeToString(ByteArray(6) { (this shr (8 * (5 - it))).toByte() })
}

internal fun Int.toApiListStatus() =
    when (this) {
        ComicK.READING -> 1
        ComicK.COMPLETED -> 2
        ComicK.ON_HOLD -> 3
        ComicK.DROPPED -> 4
        ComicK.PLAN_TO_READ -> 5
        else -> throw IllegalArgumentException("Unknown status: $this")
    }

internal fun Int.fromApiListStatus() =
    when (this) {
        1 -> ComicK.READING
        2 -> ComicK.COMPLETED
        3 -> ComicK.ON_HOLD
        4 -> ComicK.DROPPED
        5 -> ComicK.PLAN_TO_READ
        // A status added later must not break reading the library
        else -> ComicK.READING
    }

class ComickMissingScopesException(
    message: String,
) : Exception(message)
