package dev.thor.rombutler.data.files

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.UndoInfo
import dev.thor.rombutler.domain.model.UndoKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UndoManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `extraction can be undone while source archive is in trash`() = runTest {
        val downloadDir = tempFolder.newFolder("downloads")
        val originalArchive = File(downloadDir, "game.zip")
        val trashDir = File(downloadDir, ".thor_trash").apply { mkdirs() }
        File(trashDir, originalArchive.name).writeBytes(byteArrayOf(1))
        val extracted = tempFolder.newFile("game.gba").apply { writeBytes(byteArrayOf(2)) }
        val manager = UndoManager(StandardTestDispatcher(testScheduler))

        val result = manager.undo(
            UndoInfo(
                kind = UndoKind.EXTRACTED,
                createdFiles = listOf(extracted.absolutePath),
                sourceArchivePath = originalArchive.absolutePath,
            ),
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(extracted.exists()).isFalse()
    }

    @Test
    fun `undo removes a playlist that referenced the deleted discs`() = runTest {
        val downloadDir = tempFolder.newFolder("downloads")
        File(downloadDir, "game.zip").writeBytes(byteArrayOf(1)) // source still present
        val psxDir = tempFolder.newFolder("roms", "psx")
        val disc1 = File(psxDir, "Game (Disc 1).chd").apply { writeBytes(byteArrayOf(2)) }
        val disc2 = File(psxDir, "Game (Disc 2).chd").apply { writeBytes(byteArrayOf(3)) }
        val playlist = File(psxDir, "Game.m3u").apply {
            writeText("Game (Disc 1).chd\nGame (Disc 2).chd\n")
        }
        // An unrelated playlist in the same folder must survive.
        val otherPlaylist = File(psxDir, "Other.m3u").apply { writeText("Other (Disc 1).chd\n") }
        val manager = UndoManager(StandardTestDispatcher(testScheduler))

        val result = manager.undo(
            UndoInfo(
                kind = UndoKind.EXTRACTED,
                createdFiles = listOf(disc1.absolutePath, disc2.absolutePath),
                sourceArchivePath = File(downloadDir, "game.zip").absolutePath,
            ),
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(disc1.exists()).isFalse()
        assertThat(disc2.exists()).isFalse()
        assertThat(playlist.exists()).isFalse() // dangling playlist removed
        assertThat(otherPlaylist.exists()).isTrue() // unrelated one kept
    }
}
