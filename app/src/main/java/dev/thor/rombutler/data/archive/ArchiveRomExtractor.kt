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
 * [RomExtractor] built on the [ArchiveEntrySource] implementations plus
 * plain-file moves for loose ROMs.
 *
 * Error handling: never overwrite silently (replace is an explicit user
 * decision), check free space before writing gigabytes, and roll back
 * everything written in a failed run.
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
        replaceExisting: Boolean,
        expectedBytes: Long,
        onBytesWritten: (Long) -> Unit,
    ): Result<List<String>> = withContext(ioDispatcher) {
        runCatching {
            val archiveFile = File(archivePath)
            if (!archiveFile.isFile) {
                throw IOException("Quellarchiv nicht gefunden: $archivePath")
            }
            val dir = prepareTargetDir(targetDir, expectedBytes)

            // Flatten archive subfolders: the ROM lands directly in the target
            val targets = entryPaths.associateWith { entryPath ->
                File(dir, entryPath.replace('\\', '/').substringAfterLast('/'))
            }
            handleCollisions(targets.values, replaceExisting)

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

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        targetDir: String,
        replaceExisting: Boolean,
        onBytesWritten: (Long) -> Unit,
    ): Result<List<String>> = withContext(ioDispatcher) {
        runCatching {
            val sources = sourcePaths.map { path ->
                File(path).also {
                    if (!it.isFile) throw IOException("Quelldatei nicht gefunden: $path")
                }
            }
            val dir = prepareTargetDir(targetDir, expectedBytes = sources.sumOf { it.length() })
            val targets = sources.associateWith { File(dir, it.name) }
            handleCollisions(targets.values, replaceExisting)

            val movedTargets = mutableListOf<File>()
            try {
                for ((source, target) in targets) {
                    if (source.renameTo(target)) {
                        // Rename is instant — report the full size at once
                        onBytesWritten(target.length())
                    } else {
                        copyVerified(source, target, onBytesWritten)
                        if (!source.delete()) {
                            throw IOException("Quelle konnte nicht gelöscht werden: ${source.absolutePath}")
                        }
                    }
                    movedTargets += target
                }
            } catch (e: Exception) {
                // Roll back only files copied in this run; renamed originals
                // are moved back to keep the download folder intact.
                for (target in movedTargets) {
                    val source = targets.entries.first { it.value == target }.key
                    if (!source.exists()) target.renameTo(source) else target.delete()
                }
                targets.values.filterNot { it in movedTargets }.forEach { it.delete() }
                throw e
            }
            targets.values.map { it.absolutePath }
        }
    }

    override suspend fun deleteArchive(archivePath: String): Boolean =
        withContext(ioDispatcher) {
            File(archivePath).delete()
        }

    /** Creates the target dir and verifies free space when [expectedBytes] > 0. */
    private fun prepareTargetDir(targetDir: String, expectedBytes: Long): File {
        val dir = File(targetDir)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Zielordner konnte nicht angelegt werden: $targetDir")
        }
        if (expectedBytes > 0) {
            val usable = dir.usableSpace
            if (usable in 1 until expectedBytes + SPACE_MARGIN_BYTES) {
                throw IOException(
                    "Zu wenig Speicherplatz: benötigt ${expectedBytes / MB} MB, frei ${usable / MB} MB",
                )
            }
        }
        return dir
    }

    /** Fails on collisions, or deletes them when the user chose to replace. */
    private fun handleCollisions(targets: Collection<File>, replaceExisting: Boolean) {
        for (target in targets) {
            if (!target.exists()) continue
            if (!replaceExisting) {
                throw IOException("Ziel existiert bereits: ${target.absolutePath}")
            }
            if (!target.delete()) {
                throw IOException("Vorhandene Datei konnte nicht ersetzt werden: ${target.absolutePath}")
            }
        }
    }

    private fun copyVerified(source: File, target: File, onBytes: (Long) -> Unit) {
        try {
            source.inputStream().use { input ->
                ProgressOutputStream(target.outputStream(), onBytes).use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }
            if (target.length() != source.length()) {
                throw IOException("Kopie unvollständig (Größe weicht ab)")
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    companion object {
        private const val MB = 1024L * 1024
        /** Safety margin so the volume is not filled to the last byte. */
        private const val SPACE_MARGIN_BYTES = 64L * MB
    }
}
