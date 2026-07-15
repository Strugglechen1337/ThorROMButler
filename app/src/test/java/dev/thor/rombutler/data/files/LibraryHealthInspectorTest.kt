package dev.thor.rombutler.data.files

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.data.backup.BackupManifest
import dev.thor.rombutler.data.backup.LibraryBackup
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.repository.LibraryArchiveProblem
import dev.thor.rombutler.domain.repository.LibraryBackupState
import dev.thor.rombutler.domain.repository.LibraryBiosState
import dev.thor.rombutler.domain.repository.LibraryDatState
import dev.thor.rombutler.domain.repository.LibraryReferenceProblem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryHealthInspectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `valid relative m3u and cue references stay healthy`() {
        val dir = temp.newFolder("psx")
        File(dir, "track.bin").writeBytes(byteArrayOf(1))
        File(dir, "Disc 1.cue").writeText("FILE \"track.bin\" BINARY")
        File(dir, "Disc 2.chd").writeBytes(byteArrayOf(2))
        File(dir, "Game.m3u").writeText("Disc 1.cue\nDisc 2.chd\n")

        val issues = LibraryHealthInspector.inspectReferences(dir.listFiles().orEmpty().toList())

        assertThat(issues).isEmpty()
    }

    @Test
    fun `missing playlist entries are reported but URLs are accepted`() {
        val dir = temp.newFolder("mixed")
        val playlist = File(dir, "Game.m3u").apply {
            writeText("# comment\nmissing.cue\nhttps://example.test/disc.chd\n")
        }

        val issue = LibraryHealthInspector.inspectReferences(listOf(playlist)).single()

        assertThat(issue.problem).isEqualTo(LibraryReferenceProblem.MISSING_FILES)
        assertThat(issue.missingReferences).containsExactly("missing.cue")
    }

    @Test
    fun `empty cue is reported`() {
        val cue = temp.newFile("empty.cue").apply { writeText("REM no tracks") }

        val issue = LibraryHealthInspector.inspectReferences(listOf(cue)).single()

        assertThat(issue.problem).isEqualTo(LibraryReferenceProblem.EMPTY)
    }

    @Test
    fun `packed zip directory is checked without extracting`() {
        val valid = temp.newFile("valid.zip")
        ZipOutputStream(valid.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("rom.bin"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val broken = temp.newFile("broken.zip").apply { writeText("not a zip") }
        val empty = temp.newFile("empty.zip")
        ZipOutputStream(empty.outputStream()).use { }

        val issues = LibraryHealthInspector.inspectPackedArchives(listOf(valid, broken, empty))

        assertThat(issues).hasSize(2)
        assertThat(issues.associate { File(it.filePath).name to it.problem }).containsExactly(
            "broken.zip", LibraryArchiveProblem.UNREADABLE,
            "empty.zip", LibraryArchiveProblem.EMPTY,
        )
    }

    @Test
    fun `bios and dat readiness distinguish configuration states`() {
        val bios = temp.newFolder("bios")
        val dat = temp.newFolder("dat")

        assertThat(LibraryHealthInspector.biosHealth(null, BiosDetector()).state)
            .isEqualTo(LibraryBiosState.NOT_CONFIGURED)
        assertThat(LibraryHealthInspector.biosHealth(bios.absolutePath, BiosDetector()).state)
            .isEqualTo(LibraryBiosState.NONE_DETECTED)
        File(bios, "gba_bios.bin").writeBytes(byteArrayOf(1))
        assertThat(LibraryHealthInspector.biosHealth(bios.absolutePath, BiosDetector()).state)
            .isEqualTo(LibraryBiosState.READY)

        assertThat(LibraryHealthInspector.datHealth(dat.absolutePath).state)
            .isEqualTo(LibraryDatState.NO_DAT_FILES)
        File(dat, "broken.dat").writeText("not a DAT")
        assertThat(LibraryHealthInspector.datHealth(dat.absolutePath).state)
            .isEqualTo(LibraryDatState.NO_USABLE_ENTRIES)
        File(dat, "Nintendo.dat").writeText(
            """<datafile><rom name="Game.gba" crc="12345678"/></datafile>""",
        )
        val datHealth = LibraryHealthInspector.datHealth(dat.absolutePath)
        assertThat(datHealth.state).isEqualTo(LibraryDatState.READY)
        assertThat(datHealth.datFileCount).isEqualTo(1)
    }

    @Test
    fun `backup status detects current and outdated manifests`() {
        val romBase = temp.newFolder("roms")
        val backup = temp.newFolder("backup")
        LibraryBackup.writeManifest(
            backup,
            BackupManifest(
                createdAtMillis = 2_000,
                sourcePath = romBase.absolutePath,
                fileCount = 1,
                totalBytes = 1,
            ),
        )

        assertThat(
            LibraryHealthInspector.backupHealth(romBase, backup.absolutePath, 1_000).state,
        ).isEqualTo(LibraryBackupState.CURRENT)
        assertThat(
            LibraryHealthInspector.backupHealth(romBase, backup.absolutePath, 3_000).state,
        ).isEqualTo(LibraryBackupState.OUTDATED)
    }
}
