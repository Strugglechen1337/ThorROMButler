package dev.thor.rombutler.domain.detection

/** Minimal parser for Dolphin's CISO container header used by GameCube and Wii images. */
object CisoHeader {
    private const val HEADER_SIZE = 0x8000
    private const val MAP_OFFSET = 8
    private val MAGIC = byteArrayOf(0x43, 0x49, 0x53, 0x4F) // "CISO"
    private val GAMECUBE_MAGIC = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D)
    private val WII_MAGIC = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())

    fun isGameCube(header: ByteArray): Boolean =
        hasDiscHeader(header) && header.matchesAt(HEADER_SIZE + 0x1C, GAMECUBE_MAGIC)

    fun isWii(header: ByteArray): Boolean =
        hasDiscHeader(header) && header.matchesAt(HEADER_SIZE + 0x18, WII_MAGIC)

    private fun hasDiscHeader(header: ByteArray): Boolean {
        if (!header.matchesAt(0, MAGIC) || header.size <= MAP_OFFSET) return false

        val blockSize =
            (header[4].toLong() and 0xFF) or
                ((header[5].toLong() and 0xFF) shl 8) or
                ((header[6].toLong() and 0xFF) shl 16) or
                ((header[7].toLong() and 0xFF) shl 24)

        // Block zero contains the disc header and is the first stored data block.
        return blockSize > 0 && header[MAP_OFFSET] == 1.toByte()
    }

    private fun ByteArray.matchesAt(offset: Int, expected: ByteArray): Boolean {
        if (offset < 0 || size < offset + expected.size) return false
        return expected.indices.all { this[offset + it] == expected[it] }
    }
}
