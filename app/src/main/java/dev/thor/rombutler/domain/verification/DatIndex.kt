package dev.thor.rombutler.domain.verification

/**
 * One ROM entry from a DAT file.
 *
 * @property name canonical file name from the DAT (No-Intro/Redump naming).
 * @property sizeBytes expected size, -1 when the DAT omits it.
 * @property datName name of the DAT collection the entry came from.
 */
data class DatEntry(
    val name: String,
    val sizeBytes: Long,
    val datName: String,
)

/**
 * CRC32 index over one or more DAT files (Logiqx XML as published by
 * No-Intro and Redump). Lookup key is the uppercase hex CRC32.
 *
 * Parsing is deliberately a lenient streaming scan for `<rom .../>` tags
 * instead of a full XML parser: DATs are machine-generated, several MB
 * large, and this keeps the parser dependency-free and JVM-testable.
 */
class DatIndex private constructor(
    private val byCrc: Map<String, DatEntry>,
    val entryCount: Int,
    val datNames: List<String>,
) {

    /** Entry for [crc32] (any casing/width), or null when unknown. */
    fun lookup(crc32: Long): DatEntry? =
        byCrc[crc32.toString(16).uppercase().padStart(8, '0')]

    fun isEmpty(): Boolean = byCrc.isEmpty()

    companion object {
        val EMPTY = DatIndex(emptyMap(), 0, emptyList())

        private val ROM_TAG = Regex("""<rom\s+([^>]*?)/?>""", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("""(\w+)="([^"]*)"""")
        private val HEADER_NAME =
            Regex("""<header>.*?<name>([^<]+)</name>""", RegexOption.DOT_MATCHES_ALL)

        /** Parses one DAT file content; unknown/malformed tags are skipped. */
        fun parse(datContent: String): DatIndex {
            val datName = HEADER_NAME.find(datContent)?.groupValues?.get(1)?.trim() ?: "DAT"
            val entries = mutableMapOf<String, DatEntry>()
            for (match in ROM_TAG.findAll(datContent)) {
                val attributes = ATTRIBUTE.findAll(match.groupValues[1])
                    .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val crc = attributes["crc"]?.uppercase()?.padStart(8, '0') ?: continue
                val name = attributes["name"] ?: continue
                entries.putIfAbsent(
                    crc,
                    DatEntry(
                        name = unescapeXml(name),
                        sizeBytes = attributes["size"]?.toLongOrNull() ?: -1,
                        datName = datName,
                    ),
                )
            }
            return DatIndex(entries, entries.size, listOf(datName))
        }

        /** Merges several parsed DATs into one index. */
        fun merge(indexes: List<DatIndex>): DatIndex {
            if (indexes.isEmpty()) return EMPTY
            val merged = mutableMapOf<String, DatEntry>()
            for (index in indexes) {
                for ((crc, entry) in index.byCrc) {
                    merged.putIfAbsent(crc, entry)
                }
            }
            return DatIndex(merged, merged.size, indexes.flatMap { it.datNames })
        }

        private fun unescapeXml(text: String): String = text
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
    }
}
