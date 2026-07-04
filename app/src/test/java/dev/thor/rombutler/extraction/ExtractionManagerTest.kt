package dev.thor.rombutler.extraction

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.LogEntry
import dev.thor.rombutler.domain.model.LogLevel
import dev.thor.rombutler.domain.model.UndoInfo
import dev.thor.rombutler.domain.model.UndoKind
import dev.thor.rombutler.domain.repository.LogRepository
import dev.thor.rombutler.domain.repository.RomExtractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * JVM tests for the extraction run: success, failure, trash mode and
 * cancellation — including the archive-cleanup and undo-metadata rules.
 */
class ExtractionManagerTest {

    // --- fakes -----------------------------------------------------------

    private class FakeExtractor : RomExtractor {
        val deletedArchives = mutableListOf<String>()
        val trashedArchives = mutableListOf<String>()
        var failFor: Set<String> = emptySet()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun extractGroup(
            archivePath: String,
            archiveType: ArchiveType,
            entryPaths: List<String>,
            targetDir: String,
            replaceExisting: Boolean,
            expectedBytes: Long,
            onBytesWritten: (Long) -> Unit,
        ): Result<List<String>> {
            gate?.await()
            if (archivePath in failFor) {
                return Result.failure(java.io.IOException("kaputt"))
            }
            onBytesWritten(expectedBytes.coerceAtLeast(1))
            return Result.success(entryPaths.map { "$targetDir/${it.substringAfterLast('/')}" })
        }

        override suspend fun moveFiles(
            sourcePaths: List<String>,
            targetDir: String,
            replaceExisting: Boolean,
            onBytesWritten: (Long) -> Unit,
        ): Result<List<String>> {
            gate?.await()
            if (sourcePaths.any { it in failFor }) {
                return Result.failure(java.io.IOException("kaputt"))
            }
            return Result.success(sourcePaths.map { "$targetDir/${it.substringAfterLast('/')}" })
        }

        override suspend fun deleteArchive(archivePath: String): Boolean {
            deletedArchives += archivePath
            return true
        }

        override suspend fun moveToTrash(archivePath: String): Boolean {
            trashedArchives += archivePath
            return true
        }
    }

    private class FakeLog : LogRepository {
        val appended = mutableListOf<LogEntry>()
        override val entries = MutableStateFlow<List<LogEntry>>(emptyList())
        override suspend fun append(level: LogLevel, message: String, undo: UndoInfo?) {
            appended += LogEntry(0L, level, message, undo)
        }

        override suspend fun markUndone(entry: LogEntry) = Unit
        override suspend fun clear() = Unit
    }

    private fun task(id: String, archive: String? = null) = ExtractionTask(
        id = id,
        primaryName = "$id.gba",
        source = archive?.let { TaskSource.Archive(it, ArchiveType.ZIP) } ?: TaskSource.Loose,
        entryPaths = listOf("$id.gba"),
        targetDir = "/roms/gba",
        replaceExisting = false,
        expectedBytes = 100,
    )

    // --- tests -----------------------------------------------------------

    @Test
    fun `successful run processes all tasks and cleans up the archive`() = runTest {
        val extractor = FakeExtractor()
        val log = FakeLog()
        val manager = ExtractionManager({}, extractor, log, StandardTestDispatcher(testScheduler))

        manager.start(
            tasks = listOf(task("a", archive = "/dl/a.zip"), task("b")),
            archiveCleanups = listOf(ArchiveCleanup("/dl/a.zip", "a.zip", setOf("a"))),
            deleteArchives = true,
        )
        advanceUntilIdle()

        val finished = manager.state.value as ExtractionRunState.Finished
        assertThat(finished.summary.moved).isEqualTo(2)
        assertThat(finished.summary.failed).isEqualTo(0)
        assertThat(finished.processedIds).containsExactly("a", "b")
        assertThat(finished.cancelled).isFalse()
        assertThat(extractor.deletedArchives).containsExactly("/dl/a.zip")

        // Undo metadata: archive task -> EXTRACTED, loose task -> MOVED
        val undos = log.appended.filter { it.level == LogLevel.SUCCESS }.mapNotNull { it.undo }
        assertThat(undos.map { it.kind }).containsExactly(UndoKind.EXTRACTED, UndoKind.MOVED)
        assertThat(undos.first { it.kind == UndoKind.EXTRACTED }.sourceArchivePath)
            .isEqualTo("/dl/a.zip")
    }

    @Test
    fun `failed task keeps the archive and is counted`() = runTest {
        val extractor = FakeExtractor().apply { failFor = setOf("/dl/a.zip") }
        val log = FakeLog()
        val manager = ExtractionManager({}, extractor, log, StandardTestDispatcher(testScheduler))

        manager.start(
            tasks = listOf(task("a", archive = "/dl/a.zip"), task("b")),
            archiveCleanups = listOf(ArchiveCleanup("/dl/a.zip", "a.zip", setOf("a"))),
            deleteArchives = true,
        )
        advanceUntilIdle()

        val finished = manager.state.value as ExtractionRunState.Finished
        assertThat(finished.summary.moved).isEqualTo(1)
        assertThat(finished.summary.failed).isEqualTo(1)
        assertThat(finished.processedIds).containsExactly("b")
        // The archive's task failed -> it must NOT be deleted
        assertThat(extractor.deletedArchives).isEmpty()
        assertThat(log.appended.any { it.level == LogLevel.ERROR }).isTrue()
    }

    @Test
    fun `trash mode moves archives to trash instead of deleting`() = runTest {
        val extractor = FakeExtractor()
        val manager =
            ExtractionManager({}, extractor, FakeLog(), StandardTestDispatcher(testScheduler))

        manager.start(
            tasks = listOf(task("a", archive = "/dl/a.zip")),
            archiveCleanups = listOf(ArchiveCleanup("/dl/a.zip", "a.zip", setOf("a"))),
            deleteArchives = true,
            trashInsteadOfDelete = true,
        )
        advanceUntilIdle()

        assertThat(extractor.trashedArchives).containsExactly("/dl/a.zip")
        assertThat(extractor.deletedArchives).isEmpty()
    }

    @Test
    fun `cancel mid-run finishes as cancelled without archive cleanup`() = runTest {
        val extractor = FakeExtractor().apply { gate = CompletableDeferred() }
        val log = FakeLog()
        val manager = ExtractionManager({}, extractor, log, StandardTestDispatcher(testScheduler))

        manager.start(
            tasks = listOf(task("a", archive = "/dl/a.zip")),
            archiveCleanups = listOf(ArchiveCleanup("/dl/a.zip", "a.zip", setOf("a"))),
            deleteArchives = true,
        )
        testScheduler.runCurrent() // run until the extractor blocks on the gate
        assertThat(manager.state.value).isInstanceOf(ExtractionRunState.Running::class.java)

        manager.cancel()
        advanceUntilIdle()

        val finished = manager.state.value as ExtractionRunState.Finished
        assertThat(finished.cancelled).isTrue()
        assertThat(finished.summary.moved).isEqualTo(0)
        assertThat(extractor.deletedArchives).isEmpty()
        assertThat(log.appended.any { it.level == LogLevel.INFO && "Abgebrochen" in it.message })
            .isTrue()
    }

    @Test
    fun `acknowledge resets to idle and allows the next run`() = runTest {
        val extractor = FakeExtractor()
        val manager =
            ExtractionManager({}, extractor, FakeLog(), StandardTestDispatcher(testScheduler))

        manager.start(listOf(task("a")), emptyList(), deleteArchives = false)
        advanceUntilIdle()
        assertThat(manager.state.value).isInstanceOf(ExtractionRunState.Finished::class.java)

        manager.acknowledgeFinished()
        assertThat(manager.state.value).isEqualTo(ExtractionRunState.Idle)

        manager.start(listOf(task("b")), emptyList(), deleteArchives = false)
        advanceUntilIdle()
        val finished = manager.state.value as ExtractionRunState.Finished
        assertThat(finished.processedIds).containsExactly("b")
    }
}
