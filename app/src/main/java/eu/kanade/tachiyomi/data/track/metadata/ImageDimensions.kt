package eu.kanade.tachiyomi.data.track.metadata

/**
 * Reads the size of an image from the start of its file, so covers can be compared without downloading them: the first
 * few kilobytes are enough for PNG, GIF, WebP and JPEG.
 */
object ImageDimensions {
    /** Width and height in pixels, or null if the bytes aren't (the start of) a known image. */
    fun parse(bytes: ByteArray): Pair<Int, Int>? =
        when {
            bytes.isPng() -> png(bytes)
            bytes.isJpeg() -> jpeg(bytes)
            bytes.isWebp() -> webp(bytes)
            bytes.isGif() -> gif(bytes)
            else -> null
        }?.takeIf { (w, h) -> w > 0 && h > 0 }

    private fun ByteArray.isPng() = size >= 24 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() && this[2] == 'N'.code.toByte()

    private fun ByteArray.isJpeg() = size >= 4 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()

    private fun ByteArray.isGif() =
        size >= 10 && this[0] == 'G'.code.toByte() && this[1] == 'I'.code.toByte() && this[2] == 'F'.code.toByte()

    private fun ByteArray.isWebp() = size >= 30 && ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP"

    private fun ByteArray.ascii(
        offset: Int,
        length: Int,
    ) = String(this, offset, length, Charsets.ISO_8859_1)

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF

    private fun ByteArray.beU16(i: Int) = (u8(i) shl 8) or u8(i + 1)

    private fun ByteArray.beU32(i: Int) = (u8(i) shl 24) or (u8(i + 1) shl 16) or (u8(i + 2) shl 8) or u8(i + 3)

    private fun ByteArray.leU16(i: Int) = u8(i) or (u8(i + 1) shl 8)

    private fun ByteArray.leU24(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16)

    private fun png(b: ByteArray) = b.beU32(16) to b.beU32(20)

    private fun gif(b: ByteArray) = b.leU16(6) to b.leU16(8)

    private fun webp(b: ByteArray): Pair<Int, Int>? =
        when (b.ascii(12, 4)) {
            // Lossy: after the frame tag comes a start code and then two 14 bit sizes
            "VP8 " ->
                if (b.u8(23) == 0x9D &&
                    b.u8(24) == 0x01 &&
                    b.u8(25) == 0x2A
                ) {
                    (b.leU16(26) and 0x3FFF) to (b.leU16(28) and 0x3FFF)
                } else {
                    null
                }
            // Lossless: a signature byte and then two 14 bit sizes, each one less than the real one
            "VP8L" ->
                if (b.u8(20) == 0x2F) {
                    val bits = b.u8(21) or (b.u8(22) shl 8) or (b.u8(23) shl 16) or (b.u8(24) shl 24)
                    ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
                } else {
                    null
                }
            // Extended: the canvas, each size one less than the real one
            "VP8X" -> (b.leU24(24) + 1) to (b.leU24(27) + 1)
            else -> null
        }

    private fun jpeg(b: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < b.size) {
            if (b.u8(i) != 0xFF) {
                i++
                continue
            }
            val marker = b.u8(i + 1)
            when {
                // Padding
                marker == 0xFF -> i++
                // No length: standalone markers
                marker == 0x01 || marker in 0xD0..0xD8 -> i += 2
                // Start of frame, the sizes come after the precision. The ones in between are not frames (DHT, JPG, DAC)
                marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC ->
                    return b.beU16(i + 7) to b.beU16(i + 5)
                else -> i += 2 + b.beU16(i + 2)
            }
        }
        return null
    }
}
