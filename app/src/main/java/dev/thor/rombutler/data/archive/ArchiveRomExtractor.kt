package dev.thor.rombutler.data.archive

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.repository.RomExtractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RomExtractor] built on the [ArchiveEntrySource] implementations.
 *
 * Error handling mirrors the mover it replaced: never overwrite, and an
 * aborted extraction removes everything it wrote in this run.
 */
@Singleton
class ArchiveRomExtractor @Inject constructor(
    private val sourceFactory: ArchiveEntrySourceFactory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RomExtractor {

    override suspend fun extractGroup(
        archivePath: String,
        archiveType: ArchiveType,
        entryPaths: List<String>,
        targetDir: String,
        onBytesWritten: (Long) -> Unit,
    ): Result<List<String>> = withContext(ioDispatcher) {
        runCatching {
            val archiveFile = File(archivePath)
            if (!archiveFile.isFile) {
                throw IOException("Quellarchiv nicht gefunden: $archivePath")
            }
            val dir = File(targetDir)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IOException("Zielordner konnte nicht angelegt werden: $targetDir")
            }

            // Flatten archive subfolders: the ROM lands directly in roms/<system>/
            val targets = entryPaths.associateWith { entryPath ->
                File(dir, entryPath.replace('\\', '/').substringAfterLast('/'))
            }
            for (target in targets.values) {
                if (target.exists()) {
                    throw IOException("Ziel existiert bereits: ${target.absolutePath}")
                }
            }

            try {
                sourceFactory.forType(archiveType).extractEntries(archiveFile, targets, onBytesWritten)
            } catch (e: Exception) {
                // No half-extracted groups: remove everything from this run
                targets.values.forEach { it.delete() }
                throw e
            }
            targets.values.map { it.absolutePath }
        }
    }

    override suspend fun deleteArchive(archivePath: String): Boolean =
        withContext(ioDispatcher) {
            File(archivePath).delete()
        }
}
