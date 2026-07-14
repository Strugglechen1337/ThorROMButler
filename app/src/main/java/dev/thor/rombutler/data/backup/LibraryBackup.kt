package dev.thor.rombutler.data.backup

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One file that still needs to be copied. */
data class PlannedFile(val relativePath: String, val sizeBytes: Long)

/**
 * Result of diffing source against target before a run.
 *
 * @property filesToCopy files missing at the target (or size mismatch).
 * @property skippedExisting files already present with identical size.
 * @property bytesToCopy sum of [filesToCopy] sizes for progress/preflight.
 */
data class BackupPlan(
    val filesToCopy: List<PlannedFile>,
    val skippedExisting: Int,
    val bytesToCopy: Long,
)

/** Outcome of a copy run. */
data class BackupCopyResult(
    val copied: Int,
    val failed: List<String>,
    val cancelled: Boolean,
)

/** Metadata written to the backup root after a successful backup. */
data class BackupManifest(
    val createdAtMillis: Long,
    val sourcePath: String,
    val fileCount: Int,
    val totalBytes: Long,
)

/**
 * Incremental mirror of the ROM library: the target receives a plain 1:1
 * folder copy (`nes/`, `psx/`, ...) that any frontend could read directly.
 * Re-runs only copy what is missing — ROM files never change in place, so
 * "same relative path + same size" counts as already backed up.
 *
 * The same engine restores by swapping source and target; the caller
 * decides the direction. Files are written via hidden temp + atomic
 * rename, so a torn copy never shows up under its final name.
 */
object LibraryBackup {

    const val MANIFEST_NAME = "thor-backup.json"

    /**
     * Walks [source] and diffs against [target] (both must be directories).
     *
     * @param replaceChanged backup direction: a size mismatch means the
     *   backup copy is stale/torn and gets replaced. Restore passes false —
     *   restoring must NEVER overwrite anything that exists in the library.
     */
    fun plan(source: File, target: File, replaceChanged: Boolean = true): BackupPlan {
        val files = mutableListOf<PlannedFile>()
        var skipped = 0
        var bytes = 0L
        walk(source, prefix = "") { relativePath, file ->
            val existing = File(target, relativePath)
            if (existing.isFile && (existing.length() == file.length() || !replaceChanged)) {
                skipped++
            } else {
                files += PlannedFile(relativePath, file.length())
                bytes += file.length()
            }
        }
        return BackupPlan(files, skipped, bytes)
    }

    /**
     * Copies the planned files from [source] to [target].
     *
     * @param onBytesCopied incremental byte deltas for progress reporting.
     * @param onFileStarted relative path of the file about to be copied.
     * @param isCancelled polled between blocks; a cancelled run keeps all
     *   completed files (the next run continues incrementally).
     */
    fun copy(
        source: File,
        target: File,
        plan: BackupPlan,
        onFileStarted: (String) -> Unit = {},
        onBytesCopied: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): BackupCopyResult {
        var copied = 0
        val failed = mutableListOf<String>()
        for (planned in plan.filesToCopy) {
            if (isCancelled()) return BackupCopyResult(copied, failed, cancelled = true)
            onFileStarted(planned.relativePath)
            val result = runCatching {
                copyOne(
                    from = File(source, planned.relativePath),
                    to = File(target, planned.relativePath),
                    onBytesCopied = onBytesCopied,
                    isCancelled = isCancelled,
                )
            }
            when {
                result.isSuccess && result.getOrThrow() -> copied++
                result.isSuccess -> return BackupCopyResult(copied, failed, cancelled = true)
                else -> failed += planned.relativePath
            }
        }
        return BackupCopyResult(copied, failed, cancelled = false)
    }

    /** Writes the manifest into the backup root (backup direction only). */
    fun writeManifest(target: File, manifest: BackupManifest) {
        File(target, MANIFEST_NAME).writeText(
            JSONObject()
                .put("createdAtMillis", manifest.createdAtMillis)
                .put("sourcePath", manifest.sourcePath)
                .put("fileCount", manifest.fileCount)
                .put("totalBytes", manifest.totalBytes)
                .toString(2),
        )
    }

    /** Manifest of an existing backup, or null when absent/unreadable. */
    fun readManifest(target: File): BackupManifest? = runCatching {
        val json = JSONObject(File(target, MANIFEST_NAME).readText())
        BackupManifest(
            createdAtMillis = json.getLong("createdAtMillis"),
            sourcePath = json.getString("sourcePath"),
            fileCount = json.getInt("fileCount"),
            totalBytes = json.getLong("totalBytes"),
        )
    }.getOrNull()

    /**
     * @return true when copied completely, false when cancelled mid-file
     *   (the partial temp file is removed either way on non-completion).
     */
    private fun copyOne(
        from: File,
        to: File,
        onBytesCopied: (Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val parent = to.parentFile ?: throw IOException("Zielordner nicht ermittelbar")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Zielordner konnte nicht angelegt werden: $parent")
        }
        val temp = File(parent, ".thor-backup-${to.name}.partial")
        try {
            from.inputStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (isCancelled()) return false
                        output.write(buffer, 0, read)
                        onBytesCopied(read.toLong())
                    }
                }
            }
            if (temp.length() != from.length()) {
                throw IOException("Kopie unvollständig: ${to.name}")
            }
            if (to.exists() && !to.delete()) {
                throw IOException("Vorhandene Datei nicht ersetzbar: ${to.name}")
            }
            try {
                Files.move(temp.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), to.toPath())
            }
            return true
        } finally {
            temp.delete()
        }
    }

    /** Depth-first walk skipping hidden files/folders and the manifest. */
    private fun walk(dir: File, prefix: String, onFile: (String, File) -> Unit) {
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name.lowercase() }) {
            if (child.name.startsWith(".")) continue
            val relativePath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            when {
                child.isDirectory -> walk(child, relativePath, onFile)
                child.isFile && child.name != MANIFEST_NAME -> onFile(relativePath, child)
            }
        }
    }

    private const val COPY_BUFFER_SIZE = 1024 * 1024
}
