package dev.thor.rombutler.data.files

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.detection.BiosDetector
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BiosFilesTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val detector = BiosDetector()

    @Test
    fun `finds known bios files but never games`() {
        val root = tempDir.newFolder("downloads")
        File(root, "scph1001.bin").writeText("ps1")
        File(root, "gba_bios.bin").writeText("gba")
        File(root, "Kickle Cubicle (USA).nes").writeText("game")
        File(root, "Moonlight Quest (Europe).sfc").writeText("game")
        File(root, ".hidden").mkdirs()
        File(root, ".hidden/scph7502.bin").writeText("hidden")

        val found = BiosFiles.findLoose(listOf(root), detector::isBios)

        assertThat(found.map { it.name })
            .containsExactly("gba_bios.bin", "scph1001.bin")
            .inOrder()
    }

    @Test
    fun `moves files and keeps content`() {
        val root = tempDir.newFolder("downloads")
        val bios = File(root, "scph1001.bin").apply { writeText("ps1-bios-content") }
        val target = tempDir.newFolder("bios")

        val result = BiosFiles.moveAll(listOf(bios), target)

        assertThat(result.failed).isEmpty()
        assertThat(result.moved).hasSize(1)
        assertThat(bios.exists()).isFalse()
        assertThat(File(target, "scph1001.bin").readText()).isEqualTo("ps1-bios-content")
        assertThat(result.moved.single().second)
            .isEqualTo(File(target, "scph1001.bin").absolutePath)
    }

    @Test
    fun `never overwrites an existing file in the bios folder`() {
        val root = tempDir.newFolder("downloads")
        File(root, "gba_bios.bin").writeText("new")
        val target = tempDir.newFolder("bios")
        File(target, "gba_bios.bin").writeText("existing")

        val result = BiosFiles.moveAll(listOf(File(root, "gba_bios.bin")), target)

        assertThat(result.failed).isEmpty()
        assertThat(File(target, "gba_bios.bin").readText()).isEqualTo("existing")
        assertThat(File(target, "gba_bios (1).bin").readText()).isEqualTo("new")
    }
}
