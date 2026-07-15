package dev.thor.rombutler.data.files

import dev.thor.rombutler.data.backup.LibraryBackup
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.repository.LibraryArchiveIssue
import dev.thor.rombutler.domain.repository.LibraryArchiveProblem
import dev.thor.rombutler.domain.repository.LibraryBackupHealth
import dev.thor.rombutler.domain.repository.LibraryBackupState
import dev.thor.rombutler.domain.repository.LibraryBiosHealth
import dev.thor.rombutler.domain.repository.LibraryBiosState
import dev.thor.rombutler.domain.repository.LibraryDatHealth
import dev.thor.rombutler.domain.repository.LibraryDatState
import dev.thor.rombutler.domain.repository.LibraryReferenceIssue
import dev.thor.rombutler.domain.repository.LibraryReferenceProblem
import dev.thor.rombutler.domain.repository.LibraryReferenceType
import dev.thor.rombutler.domain.verification.DatIndex
import java.io.File
import java.util.zip.ZipFile

/** Fast, read-only health probes used by the library doctor. */
object LibraryHealthInspector {

    fun inspectReferences(files: List<File>): List<LibraryReferenceIssue> = files
        .filter { it.extension.equals("m3u", true) || it.extension.equals("cue", true) }
        .mapNotNull(::inspectReference)
        .sortedBy { it.filePath.lowercase() }

    fun inspectPackedArchives(files: List<File>): List<LibraryArchiveIssue> = files
        .filter { it.extension.equals("zip", true) }
        .mapNotNull { file ->
            try {
                ZipFile(file).use { zip ->
                    if (!zip.entries().hasMoreElements()) {
                        LibraryArchiveIssue(file.absolutePath, LibraryArchiveProblem.EMPTY)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                LibraryArchiveIssue(file.absolutePath, LibraryArchiveProblem.UNREADABLE)
            }
        }
        .sortedBy { it.filePath.lowercase() }

    fun biosHealth(path: String?, biosDetector: BiosDetector): LibraryBiosHealth {
        if (path.isNullOrBlank()) return LibraryBiosHealth(LibraryBiosState.NOT_CONFIGURED)
        val folder = File(path)
        if (!folder.isDirectory) return LibraryBiosHealth(LibraryBiosState.FOLDER_MISSING)
        val count = BiosFiles.findLoose(listOf(folder), biosDetector::isBios).size
        return LibraryBiosHealth(
            state = if (count == 0) LibraryBiosState.NONE_DETECTED else LibraryBiosState.READY,
            knownFileCount = count,
        )
    }

    fun datHealth(path: String?): LibraryDatHealth {
        if (path.isNullOrBlank()) return LibraryDatHealth(LibraryDatState.NOT_CONFIGURED)
        val folder = File(path)
        if (!folder.isDirectory) return LibraryDatHealth(LibraryDatState.FOLDER_MISSING)
        val datFiles = folder.listFiles { file ->
            file.isFile && file.extension.equals("dat", ignoreCase = true)
        }.orEmpty()
        if (datFiles.isEmpty()) return LibraryDatHealth(LibraryDatState.NO_DAT_FILES)
        val usableCount = datFiles.count { file ->
            runCatching { DatIndex.parse(file.readText()).entryCount > 0 }.getOrDefault(false)
        }
        return LibraryDatHealth(
            state = if (usableCount == 0) {
                LibraryDatState.NO_USABLE_ENTRIES
            } else {
                LibraryDatState.READY
            },
            datFileCount = usableCount,
        )
    }

    fun backupHealth(
        romBase: File,
        backupPath: String?,
        newestLibraryFileMillis: Long,
    ): LibraryBackupHealth {
        if (backupPath.isNullOrBlank()) {
            return LibraryBackupHealth(LibraryBackupState.NOT_CONFIGURED)
        }
        val folder = File(backupPath)
        if (!folder.isDirectory) return LibraryBackupHealth(LibraryBackupState.FOLDER_MISSING)
        val manifest = LibraryBackup.readManifest(folder)
            ?: return LibraryBackupHealth(LibraryBackupState.NO_MANIFEST)
        val sourceMatches = runCatching {
            File(manifest.sourcePath).canonicalFile == romBase.canonicalFile
        }.getOrDefault(false)
        val state = when {
            !sourceMatches -> LibraryBackupState.DIFFERENT_LIBRARY
            manifest.createdAtMillis < newestLibraryFileMillis -> LibraryBackupState.OUTDATED
            else -> LibraryBackupState.CURRENT
        }
        return LibraryBackupHealth(state, manifest.createdAtMillis)
    }

    private fun inspectReference(file: File): LibraryReferenceIssue? {
        val type = if (file.extension.equals("m3u", true)) {
            LibraryReferenceType.M3U
        } else {
            LibraryReferenceType.CUE
        }
        if (file.length() > MAX_DESCRIPTOR_BYTES) {
            return LibraryReferenceIssue(file.absolutePath, type, LibraryReferenceProblem.UNREADABLE)
        }
        val content = try {
            file.readText()
        } catch (_: Exception) {
            return LibraryReferenceIssue(file.absolutePath, type, LibraryReferenceProblem.UNREADABLE)
        }
        val references = when (type) {
            LibraryReferenceType.M3U -> RomFileGrouper.parsePlaylistReferences(content)
            LibraryReferenceType.CUE -> RomFileGrouper.parseCueReferences(content)
        }
        if (references.isEmpty()) {
            return LibraryReferenceIssue(file.absolutePath, type, LibraryReferenceProblem.EMPTY)
        }
        val missing = references
            .filterNot(::isExternalReference)
            .filterNot { referenceExists(file, it) }
            .distinct()
        return if (missing.isEmpty()) {
            null
        } else {
            LibraryReferenceIssue(
                filePath = file.absolutePath,
                type = type,
                problem = LibraryReferenceProblem.MISSING_FILES,
                missingReferences = missing,
            )
        }
    }

    private fun referenceExists(descriptor: File, reference: String): Boolean {
        val normalized = reference.trim().replace('\\', File.separatorChar)
        val referenced = File(normalized).let { candidate ->
            if (candidate.isAbsolute) candidate else File(descriptor.parentFile, normalized)
        }
        return referenced.isFile
    }

    private fun isExternalReference(reference: String): Boolean =
        URI_SCHEME.containsMatchIn(reference.trim())

    private const val MAX_DESCRIPTOR_BYTES = 1024L * 1024L
    private val URI_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
