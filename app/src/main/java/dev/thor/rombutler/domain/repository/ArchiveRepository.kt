package dev.thor.rombutler.domain.repository

import dev.thor.rombutler.domain.model.RomArchive

/**
 * Finds ROM archive candidates on disk.
 */
interface ArchiveRepository {

    /**
     * Scans the configured download folder (recursively, limited depth) for
     * archives and identifies their container format via magic bytes.
     *
     * @return found archives, newest first. Empty when no download folder is
     *   configured or it does not exist.
     */
    suspend fun scanForArchives(): List<RomArchive>
}
