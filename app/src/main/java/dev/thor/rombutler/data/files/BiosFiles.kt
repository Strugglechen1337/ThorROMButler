package dev.thor.rombutler.data.files

import java.io.File
import java.io.IOException

/** Result of moving BIOS files: (from → to) pairs plus failures. */
data class BiosMoveResult(
    val moved: List<Pair<String, String>>,
    val failed: List<String>,
)

/**
 * Finds loose BIOS/firmware files in the scan folders and moves them into
 * the user's BIOS folder. The counterpart to the ROM flow: BIOS files are
 * deliberately excluded from the review list, but instead of only ignoring
 * them, the butler can now put them where the emulators expect them.
 *
 * Moves are rename-first with a verified copy+delete fallback (works
 * across storage volumes) and never overwrite an existing file — name
 * collisions get the usual `(1)` suffix.
 */
object BiosFiles {

    /** Loose BIOS files in [roots] (recursive, hidden folders skipped). */
    fun findLoose(roots: List<File>, isBios: (String) -> Boolean): List<File> {
        val found = mutableListOf<File>()
        for (root in roots.filter { it.isDirectory }) {
            collect(root, depth = 0, isBios = isBios, into = found)
        }
        return found.sortedBy { it.name.lowercase() }
    }

    /** Moves [files] into [targetDir]; the caller logs the outcome. */
    fun moveAll(files: List<File>, targetDir: File): BiosMoveResult {
        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            return BiosMoveResult(emptyList(), files.map { it.absolutePath })
        }
        val moved = mutableListOf<Pair<String, String>>()
        val failed = mutableListOf<String>()
        for (file in files) {
            val result = runCatching { moveOne(file, targetDir) }
            val target = result.getOrNull()
            if (target != null) {
                moved += file.absolutePath to target.absolutePath
            } else {
                failed += file.absolutePath
            }
        }
        return BiosMoveResult(moved, failed)
    }

    private fun moveOne(file: File, targetDir: File): File {
        val safeName = IncomingFile.sanitizeName(file.name)
            ?: throw IOException("Ungültiger Dateiname: ${file.name}")
        val target = IncomingFile.uniqueTarget(targetDir, safeName)
            ?: throw IOException("Kein freier Zieldateiname: $safeName")

        // Fast path on the same volume, verified copy+delete across volumes
        if (file.renameTo(target)) return target
        file.inputStream().use { input -> IncomingFile.copyAtomically(input, target) }
        if (target.length() != file.length()) {
            target.delete()
            throw IOException("Kopie unvollständig: $safeName")
        }
        if (!file.delete()) {
            // Copy succeeded but the source is stuck — keep both, report failure
            throw IOException("Quelldatei nicht löschbar: $safeName")
        }
        return target
    }

    private fun collect(dir: File, depth: Int, isBios: (String) -> Boolean, into: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            when {
                child.isDirectory -> {
                    if (depth < MAX_DEPTH && !child.name.startsWith(".")) {
                        collect(child, depth + 1, isBios, into)
                    }
                }

                child.isFile && isBios(child.name) -> into += child
            }
        }
    }

    private const val MAX_DEPTH = 3
}
