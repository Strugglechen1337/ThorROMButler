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
}
