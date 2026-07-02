package dev.thor.rombutler.domain.model

/**
 * Archive container formats the scanner recognizes.
 *
 * The type is determined by magic bytes, not by file extension — renamed or
 * corrupted downloads must not slip through as the wrong type.
 *
 * @property displayName short label shown on the archive card.
 * @property supported whether the app can read entries from this format.
 *   RAR5 is deliberately unsupported (junrar only handles RAR4) and must be
 *   reported honestly instead of failing halfway through.
 */
enum class ArchiveType(val displayName: String, val supported: Boolean) {
    ZIP("ZIP", supported = true),
    SEVEN_ZIP("7z", supported = true),
    RAR4("RAR", supported = true),
    RAR5("RAR5", supported = false);

    companion object {
        /** File extensions that make a file an archive candidate. */
        val CANDIDATE_EXTENSIONS = setOf("zip", "7z", "rar")
    }
}
