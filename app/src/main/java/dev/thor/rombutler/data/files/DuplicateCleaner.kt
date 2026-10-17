package dev.thor.rombutler.data.files

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.repository.ExactDuplicateGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Outcome of a duplicate cleanup run. */
data class DuplicateCleanupResult(
    val movedFiles: Int,
    val movedBytes: Long,
    /** Files left untouched because a playlist / cue sheet still references them. */
    val skippedReferenced: Int,
    /** Files left untouched because they changed or could not be moved. */
    val failed: Int,
    /** Folder holding the moved files, null when nothing was moved. */
    val trashPath: String?,
)

/**
 * Removes byte-identical duplicates by MOVING them into a hidden
 * `.thor_duplicates/<timestamp>/` folder below the ROM base folder — nothing
 * is deleted, so the user can restore files by hand.
 *
 * Safety rules: at least one file per group always stays, a file is only
 * moved while its size still matches the scan, and files referenced by an
 * `.m3u` / `.cue` in the same folder are skipped to avoid dangling playlists.
 */
class DuplicateCleaner @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param keep relative path to keep per [ExactDuplicateGroup.sha256];
     *   groups without an entry fall back to [suggestKeep].
     */
    suspend fun clean(
        base: File,
        groups: List<ExactDuplicateGroup>,
        keep: Map<String, String>,
    ): DuplicateCleanupResult = withContext(ioDispatcher) {
        val baseCanonical = base.canonicalFile
        val trashRoot = File(File(baseCanonical, TRASH_DIR_NAME), timestamp())
        var moved = 0
        var movedBytes = 0L
        var skipped = 0
        var failed = 0

        for (group in groups) {
            val kept = keep[group.sha256]?.takeIf { it in group.files } ?: suggestKeep(group.files)
            for (relative in group.files) {
                if (relative == kept) continue
                val file = File(baseCanonical, relative)
                val inside = runCatching {
                    file.canonicalFile.toPath().startsWith(baseCanonical.toPath())
                }.getOrDefault(false)
                if (!inside || !file.isFile || file.length() != group.sizeBytes) {
                    failed++
                    continue
                }
                if (isReferenced(file)) {
                    skipped++
                    continue
                }
                val target = File(trashRoot, relative)
                if (moveFile(file, target)) {
                    moved++
                    movedBytes += group.sizeBytes
                } else {
                    failed++
                }
            }
        }
        DuplicateCleanupResult(
            movedFiles = moved,
            movedBytes = movedBytes,
            skippedReferenced = skipped,
            failed = failed,
            trashPath = if (moved > 0) trashRoot.absolutePath else null,
        )
    }

    private fun moveFile(source: File, target: File): Boolean = try {
        target.parentFile?.mkdirs()
        if (target.exists()) {
            false
        } else if (source.renameTo(target)) {
            true
        } else {
            source.copyTo(target)
            if (target.length() == source.length()) {
                source.delete()
            } else {
                target.delete()
                false
            }
        }
    } catch (_: IOException) {
        target.delete()
        false
    }

    /** True when an `.m3u` / `.cue` next to [file] mentions its file name. */
    private fun isReferenced(file: File): Boolean {
        val name = file.name.lowercase()
        val siblings = file.parentFile?.listFiles().orEmpty().filter {
            it.isFile && (it.extension.equals("m3u", true) || it.extension.equals("cue", true))
        }
        return siblings.any { descriptor ->
            if (descriptor.length() > MAX_DESCRIPTOR_BYTES) return@any false
            val content = runCatching { descriptor.readText() }.getOrNull() ?: return@any false
            val references = if (descriptor.extension.equals("m3u", true)) {
                RomFileGrouper.parsePlaylistReferences(content)
            } else {
                RomFileGrouper.parseCueReferences(content)
            }
            references.any { reference ->
                reference.trim().replace('\\', '/').substringAfterLast('/').lowercase() == name
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    companion object {
        const val TRASH_DIR_NAME = ".thor_duplicates"
        private const val MAX_DESCRIPTOR_BYTES = 1024L * 1024L
        private val COPY_MARKER = Regex(
            """(\(\d+\)|\bcopy\b|\bkopie\b|\bduplicate\b|\bbackup\b)""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Default file to keep: prefers names without copy markers such as
         * "(1)" or "Kopie", then the shallowest path, then the shortest name.
         */
        fun suggestKeep(files: List<String>): String = files.minWith(
            compareBy<String> { COPY_MARKER.containsMatchIn(it) }
                .thenBy { it.count { c -> c == '/' } }
                .thenBy { it.length }
                .thenBy { it },
        )
    }
}
