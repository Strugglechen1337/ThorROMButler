package dev.thor.rombutler.domain.repository

import dev.thor.rombutler.domain.model.ArchiveType

/**
 * Puts ROMs into their target system folder: extracts archive entries or
 * moves loose files, and cleans up fully processed archives.
 */
interface RomExtractor {

    /**
     * Extracts the entries at [entryPaths] from the archive into
     * [targetDir] (created when missing). On any failure, files already
     * written by this call are removed again — no half-extracted groups.
     *
     * @param replaceExisting when false, an existing target file fails the
     *   whole group. When true, existing files are deleted first (the user
     *   explicitly chose to replace them in the review UI).
     * @param expectedBytes decompressed size of the group; when > 0 the
     *   free space on the target volume is checked BEFORE extracting.
     * @param onBytesWritten delta of bytes written (drives the progress bar).
     * @return absolute paths of the extracted files on success.
     */
    suspend fun extractGroup(
        archivePath: String,
        archiveType: ArchiveType,
        entryPaths: List<String>,
        targetDir: String,
        replaceExisting: Boolean = false,
        expectedBytes: Long = -1,
        onBytesWritten: (Long) -> Unit = {},
    ): Result<List<String>>

    /**
     * Moves loose files into [targetDir] — rename fast path, verified
     * copy+delete across volumes. Same overwrite/rollback semantics as
     * [extractGroup].
     *
     * @return absolute paths of the moved files on success.
     */
    suspend fun moveFiles(
        sourcePaths: List<String>,
        targetDir: String,
        replaceExisting: Boolean = false,
        onBytesWritten: (Long) -> Unit = {},
    ): Result<List<String>>

    /** Deletes a fully processed archive. @return true when gone. */
    suspend fun deleteArchive(archivePath: String): Boolean

    /**
     * Moves a fully processed archive into the hidden `.thor_trash` folder
     * next to it (purged automatically after 7 days).
     *
     * @return true when the move succeeded.
     */
    suspend fun moveToTrash(archivePath: String): Boolean
}
