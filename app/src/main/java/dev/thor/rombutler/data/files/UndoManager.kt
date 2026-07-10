package dev.thor.rombutler.data.files

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.UndoInfo
import dev.thor.rombutler.domain.model.UndoKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverts a logged sort action.
 *
 * - [UndoKind.EXTRACTED]: deletes the extracted files — but ONLY while the
 *   source archive still exists, otherwise the extracted copy is the only
 *   one and deleting it would lose the ROM.
 * - [UndoKind.MOVED]: moves the files back to their original paths
 *   (rename fast path, verified copy+delete across volumes).
 */
@Singleton
class UndoManager @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun undo(info: UndoInfo): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            when (info.kind) {
                UndoKind.EXTRACTED -> undoExtraction(info)
                UndoKind.MOVED -> undoMove(info)
            }
        }
    }

    private fun undoExtraction(info: UndoInfo) {
        val originalArchive = info.sourceArchivePath?.let(::File)
        val archive = originalArchive?.takeIf { it.isFile }
            ?: originalArchive?.let { original ->
                File(File(original.parentFile, ".thor_trash"), original.name)
                    .takeIf { it.isFile }
            }
        if (archive == null) {
            throw IOException(
                "Quellarchiv existiert nicht mehr – Rückgängig würde die einzige Kopie löschen",
            )
        }
        info.createdFiles.forEach { File(it).delete() }
        // Remove a now-empty per-game subfolder (Dreamcast etc.)
        info.createdFiles.firstOrNull()?.let { first ->
            File(first).parentFile
                ?.takeIf { it.listFiles()?.isEmpty() == true }
                ?.delete()
        }
    }

    private fun undoMove(info: UndoInfo) {
        for ((index, targetPath) in info.createdFiles.withIndex()) {
            val target = File(targetPath)
            val original = File(info.restoreTo.getOrNull(index) ?: continue)
            if (!target.isFile) {
                throw IOException("Datei nicht mehr vorhanden: ${target.absolutePath}")
            }
            if (original.exists()) {
                throw IOException("Ursprungspfad ist bereits belegt: ${original.absolutePath}")
            }
            original.parentFile?.mkdirs()
            if (!target.renameTo(original)) {
                target.copyTo(original)
                if (original.length() != target.length()) {
                    original.delete()
                    throw IOException("Kopie unvollständig (Größe weicht ab)")
                }
                target.delete()
            }
        }
    }
}
