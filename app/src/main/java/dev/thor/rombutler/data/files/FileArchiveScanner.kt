package dev.thor.rombutler.data.files

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.RomArchive
import dev.thor.rombutler.domain.repository.ArchiveRepository
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ArchiveRepository] backed by direct [java.io.File] access.
 *
 * Scans the configured download folder up to [MAX_DEPTH] levels deep and
 * verifies each candidate's container format by reading its first bytes —
 * a renamed `.zip` that is actually RAR5 must be reported as RAR5.
 */
@Singleton
class FileArchiveScanner @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArchiveRepository {

    override suspend fun scanForArchives(): List<RomArchive> = withContext(ioDispatcher) {
        val settings = settingsRepository.settings.first()
        val roots = (listOfNotNull(settings.downloadPath) + settings.additionalSourcePaths)
            .distinct()
            .map(::File)
            .filter { it.isDirectory }
        if (roots.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<RomArchive>()
        for (root in roots) {
            purgeOldTrash(root)
            scanDirectory(root, depth = 0, results = results)
        }
        results.sortedByDescending { it.lastModifiedMillis }
    }

    /** Removes trash entries older than 7 days (see trash-instead-of-delete). */
    private fun purgeOldTrash(root: File) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        File(root, TRASH_DIR_NAME).listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    private fun scanDirectory(dir: File, depth: Int, results: MutableList<RomArchive>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            when {
                child.isDirectory -> {
                    if (depth < MAX_DEPTH && !child.name.startsWith(".")) {
                        scanDirectory(child, depth + 1, results)
                    }
                }

                child.isFile && child.extension.lowercase() in ArchiveType.CANDIDATE_EXTENSIONS -> {
                    val type = detectArchiveType(child) ?: continue
                    results += RomArchive(
                        path = child.absolutePath,
                        fileName = child.name,
                        sizeBytes = child.length(),
                        lastModifiedMillis = child.lastModified(),
                        type = type,
                    )
                }
            }
        }
    }

    companion object {
        /** Hidden trash folder inside each source root. */
        const val TRASH_DIR_NAME = ".thor_trash"

        /** Recursion limit — download folders are usually flat. */
        private const val MAX_DEPTH = 3

        // Container signatures (magic bytes at offset 0)
        private val MAGIC_ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val MAGIC_7Z = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        private val MAGIC_RAR4 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        private val MAGIC_RAR5 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)

        /**
         * Reads the file header and returns the actual container format,
         * or `null` when the file matches no known signature (e.g. a
         * mislabeled text file or an interrupted download).
         */
        fun detectArchiveType(file: File): ArchiveType? {
            val header = ByteArray(8)
            val read = try {
                file.inputStream().use { it.read(header) }
            } catch (_: java.io.IOException) {
                return null
            }
            if (read < MAGIC_ZIP.size) return null

            return when {
                header.startsWith(MAGIC_RAR5) -> ArchiveType.RAR5
                header.startsWith(MAGIC_RAR4) -> ArchiveType.RAR4
                header.startsWith(MAGIC_7Z) -> ArchiveType.SEVEN_ZIP
                header.startsWith(MAGIC_ZIP) -> ArchiveType.ZIP
                else -> null
            }
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) {
                if (this[i] != prefix[i]) return false
            }
            return true
        }
    }
}
