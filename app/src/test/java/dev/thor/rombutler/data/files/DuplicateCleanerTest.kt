package dev.thor.rombutler.data.files

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.repository.ExactDuplicateGroup
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DuplicateCleanerTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun group(vararg files: String, size: Long = 4L) =
        ExactDuplicateGroup(sha256 = "hash", sizeBytes = size, files = files.toList())

    private fun write(base: File, relative: String, bytes: ByteArray = byteArrayOf(1, 2, 3, 4)) =
        File(base, relative).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

    @Test
    fun `suggestKeep avoids copy markers and prefers shallow paths`() {
        assertThat(DuplicateCleaner.suggestKeep(listOf("gba/Game (1).gba", "gba/Game.gba")))
            .isEqualTo("gba/Game.gba")
        assertThat(DuplicateCleaner.suggestKeep(listOf("gba/old/Game.gba", "gba/Game.gba")))
            .isEqualTo("gba/Game.gba")
    }

    @Test
    fun `moves duplicates to hidden folder and keeps the chosen file`() = runTest {
        val base = tempDir.newFolder("roms")
        val keep = write(base, "gba/Game.gba")
        val dupe = write(base, "gba/Game (1).gba")

        val cleaner = DuplicateCleaner(UnconfinedTestDispatcher(testScheduler))
        val result = cleaner.clean(base, listOf(group("gba/Game.gba", "gba/Game (1).gba")), emptyMap())

        assertThat(result.movedFiles).isEqualTo(1)
        assertThat(result.movedBytes).isEqualTo(4L)
        assertThat(keep.isFile).isTrue()
        assertThat(dupe.exists()).isFalse()
        val trashed = File(requireNotNull(result.trashPath), "gba/Game (1).gba")
        assertThat(trashed.isFile).isTrue()
        assertThat(result.trashPath).contains(DuplicateCleaner.TRASH_DIR_NAME)
    }

    @Test
    fun `respects explicit keep choice`() = runTest {
        val base = tempDir.newFolder("roms")
        val a = write(base, "gba/A.gba")
        val b = write(base, "gba/B.gba")

        val cleaner = DuplicateCleaner(UnconfinedTestDispatcher(testScheduler))
        cleaner.clean(base, listOf(group("gba/A.gba", "gba/B.gba")), mapOf("hash" to "gba/B.gba"))

        assertThat(a.exists()).isFalse()
        assertThat(b.isFile).isTrue()
    }

    @Test
    fun `skips files changed since the scan and files referenced by playlists`() = runTest {
        val base = tempDir.newFolder("roms")
        write(base, "psx/Disc 1.chd")
        write(base, "psx/Disc 1 copy.chd")
        File(base, "psx/Game.m3u").writeText("Disc 1 copy.chd\n")
        write(base, "gba/X.gba")
        write(base, "gba/Y.gba", byteArrayOf(9, 9, 9, 9, 9))

        val cleaner = DuplicateCleaner(UnconfinedTestDispatcher(testScheduler))
        val result = cleaner.clean(
            base,
            listOf(
                ExactDuplicateGroup("h1", 4L, listOf("psx/Disc 1.chd", "psx/Disc 1 copy.chd")),
                ExactDuplicateGroup("h2", 4L, listOf("gba/X.gba", "gba/Y.gba")),
            ),
            mapOf("h2" to "gba/X.gba"),
        )

        assertThat(result.movedFiles).isEqualTo(0)
        assertThat(result.skippedReferenced).isEqualTo(1)
        assertThat(result.failed).isEqualTo(1)
        assertThat(File(base, "psx/Disc 1 copy.chd").isFile).isTrue()
        assertThat(File(base, "gba/Y.gba").isFile).isTrue()
    }

    @Test
    fun `never touches paths outside the base folder`() = runTest {
        val base = tempDir.newFolder("roms")
        write(base, "gba/A.gba")
        val outside = write(tempDir.root, "outside.gba")

        val cleaner = DuplicateCleaner(UnconfinedTestDispatcher(testScheduler))
        val result = cleaner.clean(
            base,
            listOf(group("gba/A.gba", "../outside.gba")),
            mapOf("hash" to "gba/A.gba"),
        )

        assertThat(result.movedFiles).isEqualTo(0)
        assertThat(outside.isFile).isTrue()
    }
}
