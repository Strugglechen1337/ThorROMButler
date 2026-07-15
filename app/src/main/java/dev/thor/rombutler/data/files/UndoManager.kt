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
        val removed = info.createdFiles.map(::File)
        removed.forEach { it.delete() }
        // A generated .m3u that now references a deleted disc would be a
        // dangling playlist — remove it as part of the undo.
        removeStalePlaylists(removed)
        // Remove a now-empty per-game subfolder (Dreamcast etc.)
        removed.firstOrNull()?.parentFile
            ?.takeIf { it.listFiles()?.isEmpty() == true }
            ?.delete()
    }

    private fun undoMove(info: UndoInfo) {
        val restored = mutableListOf<File>()
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
            restored += target
        }
        // The files left their targets — drop playlists that referenced them.
        removeStalePlaylists(restored)
    }

    /**
     * Deletes `.m3u` playlists next to [removedFiles] that reference any of
     * those (now-missing) file names, so undo does not leave dangling
     * multi-disc playlists behind.
     */
    private fun removeStalePlaylists(removedFiles: List<File>) {
        removedFiles.groupBy { it.parentFile }.forEach { (dir, files) ->
            if (dir == null) return@forEach
            val removedNames = files.map { it.name.lowercase() }.toSet()
            dir.listFiles { f: File -> f.isFile && f.extension.equals("m3u", ignoreCase = true) }
                .orEmpty()
                .forEach { playlist ->
                    val references = runCatching { playlist.readLines() }
                        .getOrDefault(emptyList())
                        .map {
                            it.trim().replace('\\', '/').substringAfterLast('/').lowercase()
                        }
                    if (references.any { it in removedNames }) {
                        playlist.delete()
                    }
                }
        }
    }
}
