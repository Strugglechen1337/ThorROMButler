package dev.thor.rombutler.data.archive

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.DetectionEngine
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.RomArchive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * End-to-end test of the ZIP analysis pipeline with a real archive built
 * on the fly: entry listing, cue-based grouping and magic-byte resolution
 * — all without extracting the archive.
 */
class CommonsArchiveAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildAnalyzer(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        CommonsArchiveAnalyzer(
            sourceFactory = ArchiveEntrySourceFactory(
                zip = ZipEntrySource(),
                sevenZ = SevenZEntrySource(),
                rar = RarEntrySource(),
            ),
            grouper = RomFileGrouper(),
            engine = DetectionEngine(SystemRegistry()),
            biosDetector = BiosDetector(),
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

    private fun romArchive(file: File) = RomArchive(
        path = file.absolutePath,
        fileName = file.name,
        sizeBytes = file.length(),
        lastModifiedMillis = file.lastModified(),
        type = ArchiveType.ZIP,
    )

    @Test
    fun `analyzes zip with unique-extension rom`() = runTest {
        val zipFile = tempFolder.newFile("roms.zip")
        writeZip(
            zipFile,
            mapOf(
                "Metroid Fusion.gba" to ByteArray(64),
                "readme.txt" to "junk".toByteArray(),
            ),
        )

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val success = analysis as ArchiveAnalysis.Success
        assertThat(success.roms).hasSize(1) // readme.txt is ignored
        val rom = success.roms.single()
        assertThat(rom.detection.system?.id).isEqualTo("gba")
        assertThat(rom.detection.confidence).isEqualTo(Confidence.CERTAIN)
        assertThat(rom.totalSizeBytes).isEqualTo(64)
    }

    @Test
    fun `groups bin and cue via cue content`() = runTest {
        val cueContent = "FILE \"FF7 Track 1.bin\" BINARY\n  TRACK 01 MODE2/2352"
        val zipFile = tempFolder.newFile("ps1.zip")
        writeZip(
            zipFile,
            mapOf(
                "Final Fantasy VII.cue" to cueContent.toByteArray(),
                "FF7 Track 1.bin" to ByteArray(2352),
            ),
        )

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val success = analysis as ArchiveAnalysis.Success
        assertThat(success.roms).hasSize(1)
        val rom = success.roms.single()
        assertThat(rom.group.primary).isEqualTo("Final Fantasy VII.cue")
        assertThat(rom.group.members)
            .containsExactly("Final Fantasy VII.cue", "FF7 Track 1.bin")
        assertThat(rom.detection.system?.id).isEqualTo("psx")
        assertThat(rom.detection.confidence).isEqualTo(Confidence.PROBABLE)
        assertThat(rom.totalSizeBytes).isEqualTo(2352L + cueContent.length)
    }

    @Test
    fun `resolves ambiguous iso via magic bytes inside the archive`() = runTest {
        val isoContent = ByteArray(0x40).also {
            it[0x1C] = 0xC2.toByte()
            it[0x1D] = 0x33
            it[0x1E] = 0x9F.toByte()
            it[0x1F] = 0x3D
        }
        val zipFile = tempFolder.newFile("gc.zip")
        writeZip(zipFile, mapOf("Wind Waker.iso" to isoContent))

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val rom = (analysis as ArchiveAnalysis.Success).roms.single()
        assertThat(rom.detection.system?.id).isEqualTo("gc")
        assertThat(rom.detection.confidence).isEqualTo(Confidence.CERTAIN)
    }

    @Test
    fun `iso without markers stays unknown`() = runTest {
        val zipFile = tempFolder.newFile("unknown.zip")
        writeZip(zipFile, mapOf("Mystery.iso" to ByteArray(0x100)))

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val rom = (analysis as ArchiveAnalysis.Success).roms.single()
        assertThat(rom.detection.confidence).isEqualTo(Confidence.UNKNOWN)
        assertThat(rom.detection.system).isNull()
    }

    @Test
    fun `rar5 archive reports unsupported`() = runTest {
        val fakeRar5 = tempFolder.newFile("modern.rar")
        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler)).analyze(
            romArchive(fakeRar5).copy(type = ArchiveType.RAR5),
        )
        assertThat(analysis).isInstanceOf(ArchiveAnalysis.Unsupported::class.java)
    }

    @Test
    fun `corrupt zip reports failed`() = runTest {
        val corrupt = tempFolder.newFile("corrupt.zip")
        corrupt.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02))

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(corrupt))

        assertThat(analysis).isInstanceOf(ArchiveAnalysis.Failed::class.java)
    }

    @Test
    fun `analyzes a real 7z archive`() = runTest {
        // Regression guard: 7z support must work end to end (LZMA2 via XZ).
        val sevenZipFile = tempFolder.newFile("roms.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(sevenZipFile).use { out ->
            val entry = out.createArchiveEntry(tempFolder.newFile("ignored"), "Metroid Fusion.gba")
            out.putArchiveEntry(entry)
            out.write(ByteArray(64))
            out.closeArchiveEntry()
        }

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler)).analyze(
            romArchive(sevenZipFile).copy(type = ArchiveType.SEVEN_ZIP),
        )

        val success = analysis as ArchiveAnalysis.Success
        val rom = success.roms.single()
        assertThat(rom.detection.system?.id).isEqualTo("gba")
        assertThat(rom.detection.confidence).isEqualTo(Confidence.CERTAIN)
    }

    @Test
    fun `single pass resolves multiple ambiguous entries in one 7z`() = runTest {
        // Regression for the O(n^2) solid-7z prefix reads: two ambiguous
        // .iso entries must both be magic-resolved from ONE archive pass.
        val gcHeader = ByteArray(0x40).also {
            it[0x1C] = 0xC2.toByte(); it[0x1D] = 0x33
            it[0x1E] = 0x9F.toByte(); it[0x1F] = 0x3D
        }
        val ps2Header = ByteArray(0x9000).also {
            "PLAYSTATION".toByteArray(Charsets.US_ASCII).copyInto(it, 0x8020)
        }
        val sevenZipFile = tempFolder.newFile("multi.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(sevenZipFile).use { out ->
            for ((name, content) in listOf("aaa.iso" to gcHeader, "bbb.iso" to ps2Header)) {
                val entry = out.createArchiveEntry(tempFolder.newFile("x-$name"), name)
                out.putArchiveEntry(entry)
                out.write(content)
                out.closeArchiveEntry()
            }
        }

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler)).analyze(
            romArchive(sevenZipFile).copy(type = ArchiveType.SEVEN_ZIP),
        )

        val roms = (analysis as ArchiveAnalysis.Success).roms
        assertThat(roms.map { it.detection.system?.id }).containsExactly("gc", "ps2")
    }

    @Test
    fun `bios files are ignored and counted`() = runTest {
        val zipFile = tempFolder.newFile("bios-pack.zip")
        writeZip(
            zipFile,
            mapOf(
                "scph1001.bin" to ByteArray(512),
                "gba_bios.bin" to ByteArray(16),
                "[BIOS] Nintendo Game Boy Boot ROM (World).gb" to ByteArray(256),
                "Metroid Fusion.gba" to ByteArray(64), // the only real game
            ),
        )

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val success = analysis as ArchiveAnalysis.Success
        assertThat(success.ignoredBiosCount).isEqualTo(3)
        assertThat(success.roms).hasSize(1)
        assertThat(success.roms.single().detection.system?.id).isEqualTo("gba")
    }

    @Test
    fun `unclaimed extensions are reported when no roms are found`() = runTest {
        // .lha (Amiga WHDLoad) and .wad are not on the system list (yet)
        val zipFile = tempFolder.newFile("unsupported.zip")
        writeZip(
            zipFile,
            mapOf(
                "Turrican II.lha" to ByteArray(128),
                "doom.wad" to ByteArray(128),
            ),
        )

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val success = analysis as ArchiveAnalysis.Success
        assertThat(success.roms).isEmpty()
        assertThat(success.otherExtensions).containsExactly("lha", "wad").inOrder()
    }

    @Test
    fun `same file names in different archive folders stay separate`() = runTest {
        val zipFile = tempFolder.newFile("multi.zip")
        writeZip(
            zipFile,
            mapOf(
                "Disc 1/game.cue" to "FILE \"game.bin\" BINARY".toByteArray(),
                "Disc 1/game.bin" to ByteArray(16),
                "Disc 2/game.cue" to "FILE \"game.bin\" BINARY".toByteArray(),
                "Disc 2/game.bin" to ByteArray(32),
            ),
        )

        val analysis = buildAnalyzer(StandardTestDispatcher(testScheduler))
            .analyze(romArchive(zipFile))

        val success = analysis as ArchiveAnalysis.Success
        assertThat(success.roms).hasSize(2)
        assertThat(success.roms.map { it.totalSizeBytes })
            .containsExactly(
                16L + "FILE \"game.bin\" BINARY".length,
                32L + "FILE \"game.bin\" BINARY".length,
            )
    }
}
