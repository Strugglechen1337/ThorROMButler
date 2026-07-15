package dev.thor.rombutler.domain.detection

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A set of files that belong to one game and must be moved together,
 * e.g. `game.cue` + `game.bin` or an `.m3u` playlist with its discs.
 *
 * @property primary the file that drives detection and naming
 *   (`.m3u` > `.cue` > the file itself).
 * @property members all files of the group including [primary].
 */
data class RomFileGroup(
    val primary: String,
    val members: List<String>,
)

/**
 * Groups multi-file ROMs so scanner and mover treat them as one unit.
 *
 * Grouping rules (in priority order):
 * 1. `.m3u` playlists claim every file they reference (content provided)
 *    plus, as fallback, all `.cue`/`.chd` files sharing their base name.
 * 2. `.cue` sheets claim the `.bin` tracks they reference (content
 *    provided) or, as fallback, all `.bin` files sharing their base name.
 * 3. Everything else forms a single-file group.
 */
@Singleton
class RomFileGrouper @Inject constructor() {

    /**
     * @param fileNames plain file names (no paths) of one folder/archive.
     * @param textContents optional contents of small text-based files
     *   (`.cue`, `.m3u`), keyed by file name — enables exact reference
     *   resolution instead of the base-name heuristic.
     */
    fun group(
        fileNames: List<String>,
        textContents: Map<String, String> = emptyMap(),
    ): List<RomFileGroup> {
        val remaining = fileNames.toMutableList()
        val groups = mutableListOf<RomFileGroup>()

        // Pass 1: m3u playlists
        for (m3u in fileNames.filter { it.hasExtension("m3u") }) {
            if (m3u !in remaining) continue
            val referenced = textContents[m3u]
                ?.let(::parsePlaylistReferences)
                ?.mapNotNull { ref -> remaining.findByNameIgnoreCase(ref) }
                .orEmpty()
                .ifEmpty {
                    remaining.filter {
                        it != m3u && it.baseName() == m3u.baseName() &&
                            (it.hasExtension("cue") || it.hasExtension("chd") || it.hasExtension("bin"))
                    }
                }
            // Pull in bin tracks of referenced cue sheets as well
            val withTracks = referenced + referenced
                .filter { it.hasExtension("cue") }
                .flatMap { cue -> cueMembers(cue, remaining, textContents) }
            val members = (listOf(m3u) + withTracks).distinct()
            groups += RomFileGroup(primary = m3u, members = members)
            remaining.removeAll(members)
        }

        // Pass 2: cue sheets
        for (cue in fileNames.filter { it.hasExtension("cue") }) {
            if (cue !in remaining) continue
            val members = (listOf(cue) + cueMembers(cue, remaining, textContents)).distinct()
            groups += RomFileGroup(primary = cue, members = members)
            remaining.removeAll(members)
        }

        // Pass 3: everything else stands alone
        for (file in remaining) {
            groups += RomFileGroup(primary = file, members = listOf(file))
        }
        return groups
    }

    /** Bin tracks belonging to [cue]: referenced in content, else same stem. */
    private fun cueMembers(
        cue: String,
        pool: List<String>,
        textContents: Map<String, String>,
    ): List<String> {
        val referenced = textContents[cue]
            ?.let(::parseCueReferences)
            ?.mapNotNull { ref -> pool.findByNameIgnoreCase(ref) }
            .orEmpty()
        if (referenced.isNotEmpty()) return referenced
        return pool.filter { it != cue && it.hasExtension("bin") && it.baseName() == cue.baseName() }
    }

    companion object {
        private val CUE_FILE_LINE = Regex(
            pattern = """^\s*FILE\s+(?:"([^"]+)"|(\S+))""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )

        /** File names referenced by `FILE "..."` lines of a cue sheet. */
        fun parseCueReferences(cueContent: String): List<String> =
            CUE_FILE_LINE.findAll(cueContent)
                .map { match ->
                    (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
                }
                .toList()

        /** Non-comment, non-blank lines of an m3u playlist. */
        fun parsePlaylistReferences(m3uContent: String): List<String> =
            m3uContent.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()

        private fun String.hasExtension(ext: String): Boolean =
            substringAfterLast('.', "").equals(ext, ignoreCase = true)

        private fun String.baseName(): String = substringBeforeLast('.')

        /** Case-insensitive lookup, tolerating path prefixes in references. */
        private fun List<String>.findByNameIgnoreCase(reference: String): String? {
            val refName = reference.replace('\\', '/').substringAfterLast('/')
            return find { it.equals(refName, ignoreCase = true) }
        }
    }
}
