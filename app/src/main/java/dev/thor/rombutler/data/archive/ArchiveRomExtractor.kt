package dev.thor.rombutler.data.archive

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.repository.RomExtractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RomExtractor] built on the [ArchiveEntrySource] implementations plus
 * plain-file moves for loose ROMs.
 *
 * Every group is written as a small transaction: new data first lands in
 * hidden partial files, occupied targets are backed up, and only verified
 * files replace the originals. Failures restore both sources and targets.
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

            // Flatten archive subfolders: the ROM lands directly in the target.
            val targets = entryPaths.associateWith { entryPath ->
                File(dir, entryPath.replace('\\', '/').substringAfterLast('/'))
            }
            validateTargets(targets.values, replaceExisting)
            val transactions = targets.values.associateWith { target ->
                StagedTarget(target = target, staged = createStagingFile(dir))
            }

            try {
                sourceFactory.forType(archiveType).extractEntries(
                    archiveFile,
                    targets.mapValues { (_, target) -> transactions.getValue(target).staged },
                    onBytesWritten,
                )
                commitStaged(transactions.values, replaceExisting)
                finishCommit(transactions.values)
            } catch (e: Throwable) {
                rollbackCommit(transactions.values)
                transactions.values.forEach { it.staged.delete() }
                throw e.toUserFacingExtractionError()
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
            validateTargets(targets.values, replaceExisting)

            val prepared = mutableListOf<PreparedMove>()
            try {
                for ((source, target) in targets) {
                    val item = PreparedMove(
                        source = source,
                        transaction = StagedTarget(
                            target = target,
                            staged = createStagingFile(dir),
                        ),
                    )
                    prepared += item
                    if (source.renameTo(item.transaction.staged)) {
                        item.sourceMovedToStage = true
                        // Same-volume rename is instant; report the full size at once.
                        onBytesWritten(item.transaction.staged.length())
                    } else {
                        copyVerified(source, item.transaction.staged, onBytesWritten)
                    }
                }

                commitStaged(prepared.map { it.transaction }, replaceExisting)

                // Cross-volume copies keep their source until every target is
                // committed. The group can therefore still be fully restored.
                for (item in prepared.filterNot { it.sourceMovedToStage }) {
                    if (!item.source.delete()) {
                        throw IOException("Quelle konnte nicht gelöscht werden: ${item.source.absolutePath}")
                    }
                }

                finishCommit(prepared.map { it.transaction })
            } catch (e: Throwable) {
                rollbackCommit(prepared.map { it.transaction })
                restoreMoveSources(prepared)
                throw e
            }
            targets.values.map { it.absolutePath }
        }
    }

    override suspend fun deleteArchive(archivePath: String): Boolean =
        withContext(ioDispatcher) {
            File(archivePath).delete()
        }

    override suspend fun moveToTrash(archivePath: String): Boolean =
        withContext(ioDispatcher) {
            val source = File(archivePath)
            val trashDir = File(source.parentFile, ".thor_trash")
            if (!trashDir.isDirectory && !trashDir.mkdirs()) return@withContext false
            source.renameTo(File(trashDir, source.name))
        }

    /** Creates the target dir and verifies free space when [expectedBytes] > 0. */
    private fun prepareTargetDir(targetDir: String, expectedBytes: Long): File {
        val dir = File(targetDir)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Zielordner konnte nicht angelegt werden: $targetDir")
        }
        recoverInterruptedTransactions(dir)
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

    /** Validates collisions without modifying any existing target. */
    private fun validateTargets(targets: Collection<File>, replaceExisting: Boolean) {
        val duplicateNames = targets.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }
        if (duplicateNames.isNotEmpty()) {
            throw IOException(
                "Mehrere Archiveinträge haben denselben Dateinamen: ${duplicateNames.keys.first()}",
            )
        }
        for (target in targets) {
            if (target.exists() && !replaceExisting) {
                throw IOException("Ziel existiert bereits: ${target.absolutePath}")
            }
        }
    }

    /**
     * Backs up occupied targets and promotes staged files. Backups remain
     * until [finishCommit] so callers can still roll back later steps.
     */
    private fun commitStaged(items: Collection<StagedTarget>, replaceExisting: Boolean) {
        for (item in items) {
            if (!item.target.exists()) continue
            if (!replaceExisting) {
                throw IOException("Ziel existiert bereits: ${item.target.absolutePath}")
            }
            val backup = backupFileFor(item.target)
            if (!item.target.renameTo(backup)) {
                throw IOException(
                    "Vorhandene Datei konnte nicht gesichert werden: ${item.target.absolutePath}",
                )
            }
            item.backup = backup
        }

        for (item in items) {
            if (!item.staged.renameTo(item.target)) {
                throw IOException(
                    "Temporäre Datei konnte nicht übernommen werden: ${item.target.absolutePath}",
                )
            }
            item.committed = true
        }
    }

    /** Finalizes a successful transaction by discarding the old targets. */
    private fun finishCommit(items: Collection<StagedTarget>) {
        items.forEach { item ->
            item.backup?.delete()
            item.backup = null
        }
    }

    /** Restores newly committed files to staging and puts old targets back. */
    private fun rollbackCommit(items: Collection<StagedTarget>) {
        for (item in items.toList().asReversed()) {
            if (item.committed && item.target.exists()) {
                if (!item.target.renameTo(item.staged)) {
                    runCatching {
                        copyVerified(item.target, item.staged) {}
                        item.target.delete()
                    }
                }
                item.committed = false
            }
        }
        for (item in items.toList().asReversed()) {
            val backup = item.backup ?: continue
            if (backup.exists()) {
                if (item.target.exists()) item.target.delete()
                backup.renameTo(item.target)
            }
            item.backup = null
        }
    }

    /** Restores loose sources that were renamed or copied into staging. */
    private fun restoreMoveSources(items: Collection<PreparedMove>) {
        for (item in items.toList().asReversed()) {
            val staged = item.transaction.staged
            if (!staged.exists()) continue
            if (item.source.exists()) {
                staged.delete()
            } else if (!staged.renameTo(item.source)) {
                runCatching {
                    item.source.parentFile?.mkdirs()
                    copyVerified(staged, item.source) {}
                    staged.delete()
                }
            }
        }
    }

    private fun createStagingFile(dir: File): File =
        File(dir, ".thor-${UUID.randomUUID()}.partial")

    private fun backupFileFor(target: File): File =
        File(target.parentFile, ".${target.name}$BACKUP_SUFFIX")

    /**
     * Repairs the tiny commit window after an OS process kill. A missing
     * target means its backup must be restored; an existing target means the
     * new file was already committed and the stale backup can be discarded.
     */
    private fun recoverInterruptedTransactions(dir: File) {
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith('.') && it.name.endsWith(BACKUP_SUFFIX) }
            .forEach { backup ->
                val targetName = backup.name.removePrefix(".").removeSuffix(BACKUP_SUFFIX)
                val target = File(dir, targetName)
                if (target.exists()) {
                    backup.delete()
                } else {
                    backup.renameTo(target)
                }
            }
        val cutoff = System.currentTimeMillis() - PARTIAL_RETENTION_MILLIS
        dir.listFiles().orEmpty()
            .filter {
                it.isFile &&
                    it.name.startsWith(".thor-") &&
                    it.name.endsWith(".partial") &&
                    it.lastModified() < cutoff
            }
            .forEach { it.delete() }
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

    private fun Throwable.toUserFacingExtractionError(): Throwable =
        when (this) {
            is OutOfMemoryError -> IOException(
                "Zu wenig Arbeitsspeicher beim Entpacken. Das Archiv nutzt vermutlich eine sehr große 7z/LZMA2-Kompression. Bitte mit der neuen Version erneut versuchen oder das Archiv am PC als ZIP/7z mit kleinerem Dictionary neu packen.",
                this,
            )

            is org.apache.commons.compress.MemoryLimitException -> IOException(
                "7z-Archiv benötigt zu viel Arbeitsspeicher (${memoryNeededInKb / 1024} MB). Bitte am PC mit kleinerem Dictionary neu packen.",
                this,
            )

            else -> this
        }

    companion object {
        private const val MB = 1024L * 1024

        /** Safety margin so the volume is not filled to the last byte. */
        private const val SPACE_MARGIN_BYTES = 64L * MB
        private const val BACKUP_SUFFIX = ".thor-backup"
        private const val PARTIAL_RETENTION_MILLIS = 24L * 60 * 60 * 1000
    }

    private data class StagedTarget(
        val target: File,
        val staged: File,
        var backup: File? = null,
        var committed: Boolean = false,
    )

    private data class PreparedMove(
        val source: File,
        val transaction: StagedTarget,
        var sourceMovedToStage: Boolean = false,
    )
}
