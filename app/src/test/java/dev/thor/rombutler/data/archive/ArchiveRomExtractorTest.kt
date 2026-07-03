package dev.thor.rombutler.data.archive

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.ArchiveType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
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

        val result = buildExtractor(StandardTestDispatcher(testScheduler)).extractGroup(
            archivePath = zipFile.absolutePath,
            archiveType = ArchiveType.ZIP,
            entryPaths = listOf("PS1/game.cue", "PS1/game.bin"),
            targetDir = targetDir.absolutePath,
        )

        val files = result.getOrThrow()
        assertThat(files).hasSize(2)
        // Subfolder "PS1/" is flattened away
        assertThat(File(targetDir, "game.cue").isFile).isTrue()
        assertThat(File(targetDir, "game.bin").length()).isEqualTo(2352)
        // Not part of the group -> not extracted
        assertThat(File(targetDir, "readme.txt").exists()).isFalse()
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
