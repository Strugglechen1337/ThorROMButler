package dev.thor.rombutler.domain.model

/**
 * A magic-byte rule that positively identifies a system from a file's
 * decompressed header bytes. Used to resolve extensions shared by several
 * systems (`.iso`, `.rvz`, ...).
 */
sealed interface MagicRule {

    /** Confidence a match yields — usually [Confidence.CERTAIN]. */
    val confidence: Confidence

    /** True when [header] (first bytes of the file) matches this rule. */
    fun matches(header: ByteArray): Boolean

    /**
     * Exact byte sequence at a fixed offset,
     * e.g. GameCube discs: `C2 33 9F 3D` at offset `0x1C`.
     */
    data class BytesAt(
        val offset: Int,
        val bytes: ByteArray,
        override val confidence: Confidence = Confidence.CERTAIN,
    ) : MagicRule {
        override fun matches(header: ByteArray): Boolean {
            if (header.size < offset + bytes.size) return false
            for (i in bytes.indices) {
                if (header[offset + i] != bytes[i]) return false
            }
            return true
        }

        // ByteArray needs manual equals/hashCode in a data class
        override fun equals(other: Any?): Boolean =
            other is BytesAt && offset == other.offset &&
                bytes.contentEquals(other.bytes) && confidence == other.confidence

        override fun hashCode(): Int =
            31 * (31 * offset + bytes.contentHashCode()) + confidence.hashCode()
    }

    /**
     * ASCII marker anywhere inside a byte range — robust when the exact
     * offset varies, e.g. `PSP GAME` inside the ISO9660 volume descriptor
     * area, or the Wii disc magic inside an RVZ header.
     */
    data class TextInRange(
        val rangeStart: Int,
        val rangeEnd: Int,
        val text: String,
        override val confidence: Confidence = Confidence.CERTAIN,
    ) : MagicRule {
        override fun matches(header: ByteArray): Boolean =
            bytesInRange(header, text.toByteArray(Charsets.US_ASCII))

        private fun bytesInRange(header: ByteArray, needle: ByteArray): Boolean {
            val end = minOf(rangeEnd, header.size) - needle.size
            outer@ for (start in rangeStart..end) {
                for (i in needle.indices) {
                    if (header[start + i] != needle[i]) continue@outer
                }
                return true
            }
            return false
        }
    }

    /**
     * Free-form predicate for container formats whose identification needs
     * real parsing (e.g. CHD headers: compressor list + logical size).
     *
     * @property name stable identifier used for equality/logging.
     */
    data class Predicate(
        val name: String,
        override val confidence: Confidence,
        val test: (ByteArray) -> Boolean,
    ) : MagicRule {
        override fun matches(header: ByteArray): Boolean = test(header)
        override fun equals(other: Any?): Boolean = other is Predicate && other.name == name
        override fun hashCode(): Int = name.hashCode()
    }

    /** [BytesAt] semantics, but the sequence may appear anywhere in a range. */
    data class BytesInRange(
        val rangeStart: Int,
        val rangeEnd: Int,
        val bytes: ByteArray,
        override val confidence: Confidence = Confidence.CERTAIN,
    ) : MagicRule {
        override fun matches(header: ByteArray): Boolean {
            val end = minOf(rangeEnd, header.size) - bytes.size
            outer@ for (start in rangeStart..end) {
                for (i in bytes.indices) {
                    if (header[start + i] != bytes[i]) continue@outer
                }
                return true
            }
            return false
        }

        override fun equals(other: Any?): Boolean =
            other is BytesInRange && rangeStart == other.rangeStart && rangeEnd == other.rangeEnd &&
                bytes.contentEquals(other.bytes) && confidence == other.confidence

        override fun hashCode(): Int =
            ((31 * rangeStart + rangeEnd) * 31 + bytes.contentHashCode()) * 31 + confidence.hashCode()
    }
}

/**
 * One emulated system loaded from the bundled or user-provided registry pack.
 * Adding a built-in system means adding one validated JSON definition; users
 * can add extension-based definitions without an app update.
 *
 * @property id stable identifier used by settings and logs.
 * @property displayName name shown in the UI.
 * @property esdeFolder subfolder below the ROM base folder (ES-DE convention).
 * @property extensions ROM file extensions (lowercase, no dot) mapped to the
 *   confidence a *sole* extension match yields. `CERTAIN` only for extensions
 *   that exist for exactly this system (e.g. `nes`); `PROBABLE`/`UNKNOWN` for
 *   generic ones (`cue`, `bin`).
 * @property magicRules positive identification rules for ambiguous extensions.
 * @property gameSubfolder when true, every ROM group gets its own subfolder
 *   below the system folder (`roms/dreamcast/<Spielname>/...`) — GDI dumps
 *   consist of a `.gdi` plus track files that emulators expect per game.
 */
data class SystemDefinition(
    val id: String,
    val displayName: String,
    val esdeFolder: String,
    val extensions: Map<String, Confidence>,
    val magicRules: List<MagicRule> = emptyList(),
    val gameSubfolder: Boolean = false,
)
