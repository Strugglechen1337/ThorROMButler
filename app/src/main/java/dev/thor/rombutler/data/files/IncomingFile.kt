package dev.thor.rombutler.data.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Shared safety boundary for files entering through LAN or Android sharing. */
object IncomingFile {

    /**
     * Returns a plain final path segment, rejecting hidden, control-character,
     * empty and excessively long names supplied by another device/app.
     */
    fun sanitizeName(raw: String): String? {
        val name = raw.replace('\\', '/').substringAfterLast('/').trim()
        return name.takeIf {
            it.isNotEmpty() &&
                !it.startsWith('.') &&
                it.length <= MAX_FILE_NAME_LENGTH &&
                it.none(Char::isISOControl)
        }
    }

    /** Resolves [safeName] and verifies that it stays directly below [dir]. */
    fun resolveTarget(dir: File, safeName: String): File? = runCatching {
        val root = dir.canonicalFile
        File(root, safeName).canonicalFile.takeIf { it.parentFile == root }
    }.getOrNull()

    /** Picks a non-existing target by appending `(1)`, `(2)`, ... when needed. */
    fun uniqueTarget(dir: File, safeName: String): File? {
        val direct = resolveTarget(dir, safeName) ?: return null
        if (!direct.exists()) return direct
        val stem = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").takeIf { '.' in safeName }.orEmpty()
        for (counter in 1..MAX_COLLISION_ATTEMPTS) {
            val candidateName = if (extension.isEmpty()) {
                "$stem ($counter)"
            } else {
                "$stem ($counter).$extension"
            }
            val candidate = resolveTarget(dir, candidateName) ?: return null
            if (!candidate.exists()) return candidate
        }
        return null
    }

    /** Copies into a hidden sibling and exposes the final name only when complete. */
    fun copyAtomically(input: InputStream, target: File) {
        val parent = target.parentFile
            ?: throw IOException("Zielordner fehlt")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Zielordner konnte nicht angelegt werden")
        }
        if (target.exists()) throw IOException("Zieldatei existiert bereits")
        val partial = File(parent, ".thor-${UUID.randomUUID()}.partial")
        try {
            partial.outputStream().use { output ->
                input.copyTo(output, bufferSize = COPY_BUFFER_SIZE)
            }
            moveWithoutReplacing(partial, target)
        } finally {
            partial.delete()
        }
    }

    private fun moveWithoutReplacing(partial: File, target: File) {
        try {
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial.toPath(), target.toPath())
        }
    }

    private const val MAX_FILE_NAME_LENGTH = 240
    private const val MAX_COLLISION_ATTEMPTS = 10_000
    private const val COPY_BUFFER_SIZE = 1024 * 1024
}
