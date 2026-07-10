package dev.thor.rombutler.data.files

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IncomingFileTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanitizer strips path segments and rejects unsafe names`() {
        assertThat(IncomingFile.sanitizeName("../folder/game.gba")).isEqualTo("game.gba")
        assertThat(IncomingFile.sanitizeName("C:\\folder\\game.gba")).isEqualTo("game.gba")
        assertThat(IncomingFile.sanitizeName(".hidden.zip")).isNull()
        assertThat(IncomingFile.sanitizeName("bad\u0000name.zip")).isNull()
    }

    @Test
    fun `atomic copy never replaces an existing target`() {
        val dir = tempFolder.newFolder("incoming")
        val target = dir.resolve("game.gba").apply { writeBytes(byteArrayOf(1)) }

        val result = runCatching {
            IncomingFile.copyAtomically(byteArrayOf(2, 3).inputStream(), target)
        }

        assertThat(result.isFailure).isTrue()
        assertThat(target.readBytes()).isEqualTo(byteArrayOf(1))
        assertThat(dir.listFiles().orEmpty().map { it.name }).containsExactly("game.gba")
    }

    @Test
    fun `unique target keeps extension and avoids overwrites`() {
        val dir = tempFolder.newFolder("duplicates")
        dir.resolve("game.zip").writeBytes(byteArrayOf())

        val target = IncomingFile.uniqueTarget(dir, "game.zip")

        assertThat(target?.name).isEqualTo("game (1).zip")
    }
}
