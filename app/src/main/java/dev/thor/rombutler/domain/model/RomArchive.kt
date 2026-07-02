package dev.thor.rombutler.domain.model

/**
 * A ROM archive candidate found in the download folder.
 *
 * @property path absolute file path.
 * @property fileName file name including extension.
 * @property sizeBytes file size in bytes.
 * @property lastModifiedMillis file modification timestamp (epoch millis).
 * @property type container format determined via magic bytes.
 */
data class RomArchive(
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val type: ArchiveType,
)
