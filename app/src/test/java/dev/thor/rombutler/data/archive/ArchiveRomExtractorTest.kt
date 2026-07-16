package dev.thor.rombutler.data.archive

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.ArchiveType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveRomExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildExtractor(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        ArchiveRomExtractor(
            sourceFactory = ArchiveEntrySourceFactory(
                zip = ZipEntrySource(),
                sevenZ = SevenZEntrySource(),
                rar = RarEntrySource(),
            ),
            ioDispatcher = dispatcher,
        )

    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }

    private fun writeRecoveryJournal(
        targetDir: File,
        id: String,
        kind: String,
        staged: File,
        target: File,
        source: File? = null,
    ): File = File(targetDir, ".thor-$id.txn").apply {
        outputStream().use { output ->
            Properties().apply {
                setProperty("kind", kind)
                setProperty("stagedName", staged.name)
                setProperty("targetPath", target.absolutePath)
                source?.let { setProperty("sourcePath", it.absolutePath) }
            }.store(output, null)
        }
    }

    @Test
    fun `extracts a multi-file group and flattens subfolders`() = runTest {
        val zipFile = tempFolder.newFile("game.zip")
        writeZip(
            zipFile,
            mapOf(
                "PS1/game.cue" to "FILE \"game.bin\" BINARY".toByteArray(),
                "PS1/game.bin" to ByteArray(2352),
                "PS1/readme.txt" to "junk".toByteArray(),
            ),
        )
        val targetDir = tempFolder.newFolder("roms", "psx")

        var reportedBytes = 0L
        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("PS1/game.cue", "PS1/game.bin"),
            targetDir = targetDir.absolutePath,
            onBytesWritten = { reportedBytes += it },
        )

        val files = result.getOrThrow()
        assertThat(files).hasSize(2)
        // Progress callback must account for every decompressed byte
        assertThat(reportedBytes)
            .isEqualTo(2352L + "FILE \"game.bin\" BINARY".length)
        // Subfolder "PS1/" is flattened away
        assertThat(File(targetDir, "game.cue").isFile).isTrue()
        assertThat(File(targetDir, "game.bin").length()).isEqualTo(2352)
        // Not part of the group -> not extracted
        assertThat(File(targetDir, "readme.txt").exists()).isFalse()
        assertThat(targetDir.listFiles().orEmpty().filter { it.name.startsWith(".thor-") })
            .isEmpty()
    }

    @Test
    fun `existing target file fails and rolls back everything`() = runTest {
        val zipFile = tempFolder.newFile("game.zip")
        writeZip(
            zipFile,
            mapOf(
                "game.cue" to "FILE \"game.bin\" BINARY".toByteArray(),
                "game.bin" to ByteArray(64),
            ),
        )
        val targetDir = tempFolder.newFolder("roms", "psx")
        File(targetDir, "game.bin").writeBytes(ByteArray(1)) // collision

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("game.cue", "game.bin"),
            targetDir = targetDir.absolutePath,
        )

        assertThat(result.isFailure).isTrue()
        // Nothing half-extracted, collision file untouched
        assertThat(File(targetDir, "game.cue").exists()).isFalse()
        assertThat(File(targetDir, "game.bin").length()).isEqualTo(1)
    }

    @Test
    fun `failed replacement preserves the existing target`() = runTest {
        val zipFile = tempFolder.newFile("broken-replacement.zip")
        writeZip(zipFile, mapOf("game.bin" to byteArrayOf(9, 9, 9)))
        val targetDir = tempFolder.newFolder("replacement", "psx")
        val existing = File(targetDir, "game.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("game.bin", "missing.cue"),
            targetDir = targetDir.absolutePath,
            replaceExisting = true,
        )

        assertThat(result.isFailure).isTrue()
        assertThat(existing.readBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(targetDir.listFiles().orEmpty().map { it.name })
            .containsExactly("game.bin")
    }

    @Test
    fun `successful replacement swaps the target after verification`() = runTest {
        val zipFile = tempFolder.newFile("replacement.zip")
        writeZip(zipFile, mapOf("game.gba" to byteArrayOf(7, 8, 9)))
        val targetDir = tempFolder.newFolder("replacement-success", "gba")
        val existing = File(targetDir, "game.gba").apply { writeBytes(byteArrayOf(1)) }

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("game.gba"),
            targetDir = targetDir.absolutePath,
            replaceExisting = true,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(existing.readBytes()).isEqualTo(byteArrayOf(7, 8, 9))
        assertThat(targetDir.listFiles().orEmpty().map { it.name })
            .containsExactly("game.gba")
    }

    @Test
    fun `duplicate flattened names fail without overwriting each other`() = runTest {
        val zipFile = tempFolder.newFile("duplicate-names.zip")
        writeZip(
            zipFile,
            mapOf(
                "disc-one/game.bin" to byteArrayOf(1),
                "disc-two/game.bin" to byteArrayOf(2),
            ),
        )
        val targetDir = tempFolder.newFolder("duplicate-target", "psx")

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("disc-one/game.bin", "disc-two/game.bin"),
            targetDir = targetDir.absolutePath,
        )

        assertThat(result.isFailure).isTrue()
        assertThat(targetDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `next transaction restores a backup left by process death`() = runTest {
        val zipFile = tempFolder.newFile("recovery.zip")
        writeZip(zipFile, mapOf("new.gba" to byteArrayOf(5)))
        val targetDir = tempFolder.newFolder("recovery-target", "gba")
        File(targetDir, ".old.gba.thor-backup").writeBytes(byteArrayOf(1, 2))

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("new.gba"),
            targetDir = targetDir.absolutePath,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(File(targetDir, "old.gba").readBytes()).isEqualTo(byteArrayOf(1, 2))
        assertThat(File(targetDir, ".old.gba.thor-backup").exists()).isFalse()
    }

    @Test
    fun `next transaction removes archive bytes left by process death`() = runTest {
        val zipFile = tempFolder.newFile("after-interruption.zip")
        writeZip(zipFile, mapOf("new.gba" to byteArrayOf(5)))
        val targetDir = tempFolder.newFolder("interrupted-extract", "gba")
        val staged = File(targetDir, ".thor-dead.partial").apply {
            writeBytes(ByteArray(1024))
        }
        val journal = writeRecoveryJournal(
            targetDir = targetDir,
            id = "dead",
            kind = "extract",
            staged = staged,
            target = File(targetDir, "old.gba"),
        )

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("new.gba"),
            targetDir = targetDir.absolutePath,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(staged.exists()).isFalse()
        assertThat(journal.exists()).isFalse()
        assertThat(File(targetDir, "new.gba").readBytes()).isEqualTo(byteArrayOf(5))
    }

    @Test
    fun `next transaction restores a moved source left by process death`() = runTest {
        val zipFile = tempFolder.newFile("after-move-interruption.zip")
        writeZip(zipFile, mapOf("new.gba" to byteArrayOf(7)))
        val sourceDir = tempFolder.newFolder("interrupted-move-source")
        val source = File(sourceDir, "original.gba")
        val targetDir = tempFolder.newFolder("interrupted-move-target", "gba")
        val staged = File(targetDir, ".thor-move-dead.partial").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val journal = writeRecoveryJournal(
            targetDir = targetDir,
            id = "move-dead",
            kind = "move",
            staged = staged,
            target = File(targetDir, source.name),
            source = source,
        )

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("new.gba"),
            targetDir = targetDir.absolutePath,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(source.readBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(staged.exists()).isFalse()
        assertThat(journal.exists()).isFalse()
    }

    @Test
    fun `successful loose move leaves no transaction artifacts`() = runTest {
        val sourceDir = tempFolder.newFolder("loose-source")
        val source = File(sourceDir, "game.gba").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val targetDir = tempFolder.newFolder("loose-target", "gba")

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).moveFiles(
            sourcePaths = listOf(source.absolutePath),
            targetDir = targetDir.absolutePath,
            replaceExisting = false,
            onBytesWritten = {},
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(source.exists()).isFalse()
        assertThat(File(targetDir, source.name).readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(targetDir.listFiles().orEmpty().filter { it.name.startsWith(".thor-") })
            .isEmpty()
    }

    @Test
    fun `extracts from a real 7z archive`() = runTest {
        val sevenZipFile = tempFolder.newFile("game.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(sevenZipFile).use { out ->
            val entry = out.createArchiveEntry(tempFolder.newFile("x"), "Metroid Fusion.gba")
            out.putArchiveEntry(entry)
            out.write(ByteArray(128) { it.toByte() })
            out.closeArchiveEntry()
        }
        val targetDir = tempFolder.newFolder("roms", "gba")

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = sevenZipFile.absolutePath,
            archiveType = ArchiveType.SEVEN_ZIP,
            entryPaths = listOf("Metroid Fusion.gba"),
            targetDir = targetDir.absolutePath,
        )

        assertThat(result.isSuccess).isTrue()
        val extracted = File(targetDir, "Metroid Fusion.gba")
        assertThat(extracted.length()).isEqualTo(128)
        assertThat(extracted.readBytes()[5]).isEqualTo(5.toByte())
    }

    @Test
    fun `deleteArchive removes the file`() = runTest {
        val zipFile = tempFolder.newFile("done.zip")
        val extractor = buildExtractor(StandardTestDispatcher(testScheduler))

        assertThat(extractor.deleteArchive(zipFile.absolutePath)).isTrue()
        assertThat(zipFile.exists()).isFalse()
    }
}
