package dev.thor.rombutler.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryBackupTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `full backup mirrors the folder structure`() {
        val source = tempDir.newFolder("roms")
        File(source, "nes").mkdirs()
        File(source, "nes/Pixel Kingdom.nes").writeText("nes-data")
        File(source, "psx/games").mkdirs()
        File(source, "psx/Crystal Saga VII (Disc 1).cue").writeText("cue")
        val target = tempDir.newFolder("backup")

        val plan = LibraryBackup.plan(source, target)
        val result = LibraryBackup.copy(source, target, plan)

        assertThat(plan.filesToCopy).hasSize(2)
        assertThat(result.copied).isEqualTo(2)
        assertThat(result.failed).isEmpty()
        assertThat(File(target, "nes/Pixel Kingdom.nes").readText()).isEqualTo("nes-data")
        assertThat(File(target, "psx/Crystal Saga VII (Disc 1).cue").readText()).isEqualTo("cue")
    }

    @Test
    fun `second run skips files that already exist with same size`() {
        val source = tempDir.newFolder("roms")
        File(source, "gba").mkdirs()
        File(source, "gba/Star Courier GX.gba").writeText("abc")
        val target = tempDir.newFolder("backup")
        LibraryBackup.copy(source, target, LibraryBackup.plan(source, target))

        // add one new file; existing one must be skipped
        File(source, "gba/Moonlight Quest.gba").writeText("def")
        val secondPlan = LibraryBackup.plan(source, target)

        assertThat(secondPlan.skippedExisting).isEqualTo(1)
        assertThat(secondPlan.filesToCopy.map { it.relativePath })
            .containsExactly("gba/Moonlight Quest.gba")
    }

    @Test
    fun `backup replaces a stale copy but restore never overwrites`() {
        val source = tempDir.newFolder("roms")
        File(source, "snes").mkdirs()
        File(source, "snes/Game.sfc").writeText("longer-content")
        val target = tempDir.newFolder("backup")
        File(target, "snes").mkdirs()
        File(target, "snes/Game.sfc").writeText("torn") // size mismatch

        // backup direction: stale copy is replaced
        val backupPlan = LibraryBackup.plan(source, target, replaceChanged = true)
        assertThat(backupPlan.filesToCopy).hasSize(1)

        // restore direction: existing library file is untouchable
        val restorePlan = LibraryBackup.plan(target, source, replaceChanged = false)
        assertThat(restorePlan.filesToCopy).isEmpty()
        assertThat(restorePlan.skippedExisting).isEqualTo(1)
    }

    @Test
    fun `hidden folders and the manifest are excluded`() {
        val source = tempDir.newFolder("roms")
        File(source, ".thor_trash").mkdirs()
        File(source, ".thor_trash/old.zip").writeText("trash")
        File(source, ".hidden-file").writeText("x")
        File(source, LibraryBackup.MANIFEST_NAME).writeText("{}")
        File(source, "nes").mkdirs()
        File(source, "nes/Game.nes").writeText("rom")
        val target = tempDir.newFolder("backup")

        val plan = LibraryBackup.plan(source, target)

        assertThat(plan.filesToCopy.map { it.relativePath }).containsExactly("nes/Game.nes")
    }

    @Test
    fun `cancel keeps completed files and reports cancelled`() {
        val source = tempDir.newFolder("roms")
        File(source, "a.bin").writeText("1")
        File(source, "b.bin").writeText("2")
        val target = tempDir.newFolder("backup")
        var copiedFirst = false

        val result = LibraryBackup.copy(
            source = source,
            target = target,
            plan = LibraryBackup.plan(source, target),
            onFileStarted = { },
            onBytesCopied = { copiedFirst = true },
            isCancelled = { copiedFirst }, // cancel after the first file's bytes
        )

        assertThat(result.cancelled).isTrue()
        assertThat(result.copied).isEqualTo(1)
        // no partial temp files left behind
        assertThat(target.listFiles().orEmpty().filter { it.name.contains(".partial") }).isEmpty()
    }

    @Test
    fun `manifest roundtrip`() {
        val target = tempDir.newFolder("backup")
        val manifest = BackupManifest(
            createdAtMillis = 1_234L,
            sourcePath = "/storage/emulated/0/ROMs",
            fileCount = 42,
            totalBytes = 1_000_000L,
        )

        LibraryBackup.writeManifest(target, manifest)

        assertThat(LibraryBackup.readManifest(target)).isEqualTo(manifest)
        assertThat(LibraryBackup.readManifest(tempDir.newFolder("empty"))).isNull()
    }
}
