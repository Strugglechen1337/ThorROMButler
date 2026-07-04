package dev.thor.rombutler.data.archive

import com.github.junrar.Archive
import dev.thor.rombutler.domain.model.ArchiveType
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A file entry inside an archive.
 *
 * @property path entry path inside the archive (forward slashes).
 * @property sizeBytes uncompressed size (0 when the format doesn't store it).
 */
data class ArchiveEntry(
    val path: String,
    val sizeBytes: Long,
)

/**
 * Uniform read-only access to archive contents, one implementation per
 * container format. All methods work WITHOUT extracting the archive:
 * [listEntries] reads only the archive directory/headers, [readEntryPrefix]
 * decompresses just the first bytes of a single entry.
 */
interface ArchiveEntrySource {

    /** All file entries (directories excluded). */
    fun listEntries(archiveFile: File): List<ArchiveEntry>

    /**
     * First [maxBytes] decompressed bytes of the entry at [entryPath],
     * or `null` when the entry cannot be read.
     */
    fun readEntryPrefix(archiveFile: File, entryPath: String, maxBytes: Int): ByteArray?

    /**
     * Batch variant: prefixes for several entries (path -> maxBytes) read
     * in ONE pass over the archive. Crucial for solid 7z blocks, where
     * every separate open decompresses everything again from the start.
     * Missing/unreadable entries are simply absent from the result.
     */
    fun readEntryPrefixes(archiveFile: File, requests: Map<String, Int>): Map<String, ByteArray>

    /**
     * Extracts the entries in [targets] (entry path -> destination file)
     * in ONE pass over the archive — important for solid 7z blocks.
     * Destination files are created; parent dirs must already exist.
     *
     * @param onBytesWritten called with the DELTA of decompressed bytes
     *   written since the last call (drives the extraction progress bar).
     * @throws java.io.IOException when an entry is missing or writing fails.
     */
    fun extractEntries(
        archiveFile: File,
        targets: Map<String, File>,
        onBytesWritten: (Long) -> Unit = {},
    )
}

/** Output stream wrapper reporting written byte deltas to [onBytes]. */
internal class ProgressOutputStream(
    private val delegate: java.io.OutputStream,
    private val onBytes: (Long) -> Unit,
) : java.io.OutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
        onBytes(1L)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        onBytes(len.toLong())
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}

/** Reads an [InputStream] up to [maxBytes] into a right-sized array. */
internal fun InputStream.readPrefix(maxBytes: Int): ByteArray {
    val buffer = ByteArray(maxBytes)
    var total = 0
    while (total < maxBytes) {
        val read = read(buffer, total, maxBytes - total)
        if (read < 0) break
        total += read
    }
    return if (total == maxBytes) buffer else buffer.copyOf(total)
}

/**
 * ZIP via Commons Compress [ZipFile] — true random access through the
 * central directory, so prefix reads are cheap even in huge archives.
 */
@Singleton
class ZipEntrySource @Inject constructor() : ArchiveEntrySource {

    override fun listEntries(archiveFile: File): List<ArchiveEntry> =
        ZipFile.builder().setFile(archiveFile).get().use { zip ->
            buildList {
                for (entry in zip.entries) {
                    if (!entry.isDirectory) {
                        add(ArchiveEntry(path = entry.name, sizeBytes = entry.size.coerceAtLeast(0)))
                    }
                }
            }
        }

    override fun readEntryPrefix(archiveFile: File, entryPath: String, maxBytes: Int): ByteArray? =
        ZipFile.builder().setFile(archiveFile).get().use { zip ->
            val entry = zip.getEntry(entryPath) ?: return null
            zip.getInputStream(entry).use { it.readPrefix(maxBytes) }
        }

    override fun readEntryPrefixes(
        archiveFile: File,
        requests: Map<String, Int>,
    ): Map<String, ByteArray> =
        ZipFile.builder().setFile(archiveFile).get().use { zip ->
            buildMap {
                for ((entryPath, maxBytes) in requests) {
                    val entry = zip.getEntry(entryPath) ?: continue
                    runCatching {
                        put(entryPath, zip.getInputStream(entry).use { it.readPrefix(maxBytes) })
                    }
                }
            }
        }

    override fun extractEntries(
        archiveFile: File,
        targets: Map<String, File>,
        onBytesWritten: (Long) -> Unit,
    ) {
        ZipFile.builder().setFile(archiveFile).get().use { zip ->
            for ((entryPath, targetFile) in targets) {
                val entry = zip.getEntry(entryPath)
                    ?: throw java.io.IOException("Eintrag nicht gefunden: $entryPath")
                // ZIP stores a CRC32 per entry — verify multi-GB writes
                val crc = java.util.zip.CRC32()
                zip.getInputStream(entry).use { input ->
                    ProgressOutputStream(targetFile.outputStream(), onBytesWritten).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            crc.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (entry.crc >= 0 && entry.crc != crc.value) {
                    throw java.io.IOException("CRC-Prüfung fehlgeschlagen: ${targetFile.name}")
                }
            }
        }
    }
}

/**
 * 7z via Commons Compress [SevenZFile]. Solid blocks may force decompression
 * of preceding entries when seeking — acceptable because only small prefixes
 * are read and only for ambiguous extensions.
 */
@Singleton
class SevenZEntrySource @Inject constructor() : ArchiveEntrySource {

    override fun listEntries(archiveFile: File): List<ArchiveEntry> =
        SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
            sevenZ.entries
                .filter { !it.isDirectory }
                .map { ArchiveEntry(path = it.name, sizeBytes = it.size) }
        }

    override fun readEntryPrefix(archiveFile: File, entryPath: String, maxBytes: Int): ByteArray? =
        SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryPath) {
                    val buffer = ByteArray(minOf(maxBytes.toLong(), entry.size).toInt())
                    var total = 0
                    while (total < buffer.size) {
                        val read = sevenZ.read(buffer, total, buffer.size - total)
                        if (read < 0) break
                        total += read
                    }
                    return if (total == buffer.size) buffer else buffer.copyOf(total)
                }
                entry = sevenZ.nextEntry
            }
            null
        }

    override fun readEntryPrefixes(
        archiveFile: File,
        requests: Map<String, Int>,
    ): Map<String, ByteArray> {
        if (requests.isEmpty()) return emptyMap()
        // Single sequential pass: solid blocks are decompressed exactly once
        return SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
            buildMap {
                var remaining = requests.size
                var entry = sevenZ.nextEntry
                while (entry != null && remaining > 0) {
                    val maxBytes = requests[entry.name]
                    if (maxBytes != null && !entry.isDirectory) {
                        val buffer = ByteArray(minOf(maxBytes.toLong(), entry.size).toInt())
                        var total = 0
                        while (total < buffer.size) {
                            val read = sevenZ.read(buffer, total, buffer.size - total)
                            if (read < 0) break
                            total += read
                        }
                        put(entry.name, if (total == buffer.size) buffer else buffer.copyOf(total))
                        remaining--
                    }
                    entry = sevenZ.nextEntry
                }
            }
        }
    }

    override fun extractEntries(
        archiveFile: File,
        targets: Map<String, File>,
        onBytesWritten: (Long) -> Unit,
    ) {
        var remaining = targets.size
        SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null && remaining > 0) {
                val targetFile = targets[entry.name]
                if (targetFile != null && !entry.isDirectory) {
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = sevenZ.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            onBytesWritten(read.toLong())
                        }
                    }
                    remaining--
                }
                entry = sevenZ.nextEntry
            }
        }
        if (remaining > 0) {
            throw java.io.IOException("$remaining Einträge nicht im Archiv gefunden")
        }
    }
}

/**
 * RAR4 via junrar. RAR5 is rejected earlier by the scanner ([ArchiveType]),
 * junrar would throw an UnsupportedRarV5Exception anyway.
 */
@Singleton
class RarEntrySource @Inject constructor() : ArchiveEntrySource {

    override fun listEntries(archiveFile: File): List<ArchiveEntry> =
        Archive(archiveFile).use { rar ->
            rar.fileHeaders
                .filter { !it.isDirectory }
                .map { header ->
                    ArchiveEntry(
                        path = header.fileName.replace('\\', '/'),
                        sizeBytes = header.fullUnpackSize,
                    )
                }
        }

    override fun readEntryPrefix(archiveFile: File, entryPath: String, maxBytes: Int): ByteArray? =
        Archive(archiveFile).use { rar ->
            val header = rar.fileHeaders.firstOrNull {
                !it.isDirectory && it.fileName.replace('\\', '/') == entryPath
            } ?: return null
            rar.getInputStream(header).use { it.readPrefix(maxBytes) }
        }

    override fun readEntryPrefixes(
        archiveFile: File,
        requests: Map<String, Int>,
    ): Map<String, ByteArray> {
        if (requests.isEmpty()) return emptyMap()
        return Archive(archiveFile).use { rar ->
            buildMap {
                for (header in rar.fileHeaders) {
                    if (header.isDirectory) continue
                    val path = header.fileName.replace('\\', '/')
                    val maxBytes = requests[path] ?: continue
                    runCatching {
                        put(path, rar.getInputStream(header).use { it.readPrefix(maxBytes) })
                    }
                }
            }
        }
    }

    override fun extractEntries(
        archiveFile: File,
        targets: Map<String, File>,
        onBytesWritten: (Long) -> Unit,
    ) {
        var remaining = targets.size
        Archive(archiveFile).use { rar ->
            for (header in rar.fileHeaders) {
                if (header.isDirectory) continue
                val targetFile = targets[header.fileName.replace('\\', '/')] ?: continue
                ProgressOutputStream(targetFile.outputStream(), onBytesWritten).use { output ->
                    rar.extractFile(header, output)
                }
                remaining--
            }
        }
        if (remaining > 0) {
            throw java.io.IOException("$remaining Einträge nicht im Archiv gefunden")
        }
    }
}

/**
 * Picks the matching [ArchiveEntrySource] for a container format.
 */
@Singleton
class ArchiveEntrySourceFactory @Inject constructor(
    private val zip: ZipEntrySource,
    private val sevenZ: SevenZEntrySource,
    private val rar: RarEntrySource,
) {
    /** @throws IllegalArgumentException for unsupported formats (RAR5). */
    fun forType(type: ArchiveType): ArchiveEntrySource = when (type) {
        ArchiveType.ZIP -> zip
        ArchiveType.SEVEN_ZIP -> sevenZ
        ArchiveType.RAR4 -> rar
        ArchiveType.RAR5 -> throw IllegalArgumentException("RAR5 wird nicht unterstützt")
    }
}
