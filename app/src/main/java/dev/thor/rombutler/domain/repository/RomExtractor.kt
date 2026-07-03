package dev.thor.rombutler.domain.repository

import dev.thor.rombutler.domain.model.ArchiveType

/**
 * Extracts ROM groups from archives into their target system folder and
 * cleans up fully processed archives.
 */
interface RomExtractor {

    /**
     * Extracts the entries at [entryPaths] from the archive into
     * [targetDir] (created when missing). Existing target files are NEVER
     * overwritten. On any failure, files already written by this call are
     * removed again — no half-extracted groups remain.
     *
     * @param onBytesWritten delta of decompressed bytes since the last
     *   call — drives the progress bar in the UI.
     * @return absolute paths of the extracted files on success.
     */
    suspend fun extractGroup(
        archivePath: String,
        archiveType: ArchiveType,
        entryPaths: List<String>,
        targetDir: String,
        onBytesWritten: (Long) -> Unit = {},
    ): Result<List<String>>

    /** Deletes a fully processed archive. @return true when gone. */
    suspend fun deleteArchive(archivePath: String): Boolean
}
