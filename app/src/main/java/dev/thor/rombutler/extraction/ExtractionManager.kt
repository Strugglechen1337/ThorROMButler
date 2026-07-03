package dev.thor.rombutler.extraction

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.LogLevel
import dev.thor.rombutler.domain.model.UndoInfo
import dev.thor.rombutler.domain.model.UndoKind
import dev.thor.rombutler.domain.repository.LogRepository
import dev.thor.rombutler.domain.repository.RomExtractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where a task's files come from. */
sealed interface TaskSource {
    data class Archive(val archivePath: String, val archiveType: ArchiveType) : TaskSource
    data object Loose : TaskSource
}

/**
 * One fully resolved unit of work: extract or move a ROM group.
 */
data class ExtractionTask(
    val id: String,
    val primaryName: String,
    val source: TaskSource,
    val entryPaths: List<String>,
    val targetDir: String,
    val replaceExisting: Boolean,
    val expectedBytes: Long,
)

/** Archive that may be deleted once all its [taskIds] succeeded. */
data class ArchiveCleanup(
    val archivePath: String,
    val archiveFileName: String,
    val taskIds: Set<String>,
)

/** Live progress of a run. */
data class ExtractionProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val currentName: String,
    val fraction: Float,
)

/** Result of a finished run. */
data class MoveSummary(val moved: Int, val failed: Int)

/** One failed extraction task with the user-facing error message. */
data class ExtractionFailure(
    val taskId: String,
    val taskName: String,
    val message: String,
)

/** State machine of the extraction manager. */
sealed interface ExtractionRunState {
    data object Idle : ExtractionRunState
    data class Running(val progress: ExtractionProgress) : ExtractionRunState

    /** Run ended; the review screen consumes this and calls [ExtractionManager.acknowledgeFinished]. */
    data class Finished(
        val summary: MoveSummary,
        val processedIds: Set<String>,
        val failures: List<ExtractionFailure>,
        val cancelled: Boolean,
    ) : ExtractionRunState
}

/**
 * Runs extraction jobs in an application-scoped coroutine so they survive
 * screen rotations and navigation, and keeps the process alive through
 * [ExtractionService] (foreground service with progress notification and
 * cancel action).
 */
@Singleton
class ExtractionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val romExtractor: RomExtractor,
    private val logRepository: LogRepository,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var runJob: Job? = null

    private val _state = MutableStateFlow<ExtractionRunState>(ExtractionRunState.Idle)
    val state: StateFlow<ExtractionRunState> = _state.asStateFlow()

    /**
     * Starts a run. No-op when one is already active.
     *
     * @param deleteArchives whether fully processed archives get removed.
     */
    fun start(
        tasks: List<ExtractionTask>,
        archiveCleanups: List<ArchiveCleanup>,
        deleteArchives: Boolean,
        trashInsteadOfDelete: Boolean = false,
    ) {
        if (tasks.isEmpty() || _state.value is ExtractionRunState.Running) return
        _state.value = ExtractionRunState.Running(
            ExtractionProgress(1, tasks.size, tasks.first().primaryName, 0f),
        )
        // Keep the process alive + show the progress notification
        ContextCompat.startForegroundService(
            context,
            Intent(context, ExtractionService::class.java),
        )

        runJob = scope.launch {
            var processed = 0
            var failed = 0
            var cancelled = false
            var cancelledTaskName: String? = null
            val processedIds = mutableSetOf<String>()
            val failures = mutableListOf<ExtractionFailure>()

            val totalBytes = tasks.sumOf { it.expectedBytes }.coerceAtLeast(1)
            var doneBytes = 0L
            var lastShownFraction = -1f

            try {
                for ((index, task) in tasks.withIndex()) {
                    cancelledTaskName = task.primaryName

                    fun publishProgress() {
                        val fraction = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        if (fraction - lastShownFraction < 0.005f) return
                        lastShownFraction = fraction
                        _state.value = ExtractionRunState.Running(
                            ExtractionProgress(
                                currentIndex = index + 1,
                                totalCount = tasks.size,
                                currentName = task.primaryName,
                                fraction = fraction,
                            ),
                        )
                    }
                    lastShownFraction = -1f
                    publishProgress()

                    val result = when (val source = task.source) {
                        is TaskSource.Archive -> romExtractor.extractGroup(
                            archivePath = source.archivePath,
                            archiveType = source.archiveType,
                            entryPaths = task.entryPaths,
                            targetDir = task.targetDir,
                            replaceExisting = task.replaceExisting,
                            expectedBytes = task.expectedBytes,
                            onBytesWritten = { delta ->
                                doneBytes += delta
                                publishProgress()
                            },
                        )

                        is TaskSource.Loose -> romExtractor.moveFiles(
                            sourcePaths = task.entryPaths,
                            targetDir = task.targetDir,
                            replaceExisting = task.replaceExisting,
                            onBytesWritten = { delta ->
                                doneBytes += delta
                                publishProgress()
                            },
                        )
                    }

                    result.onSuccess { files ->
                        processed++
                        processedIds += task.id
                        // Structured undo info makes the log entry revertible
                        val undo = when (val source = task.source) {
                            is TaskSource.Archive -> UndoInfo(
                                kind = UndoKind.EXTRACTED,
                                createdFiles = files,
                                sourceArchivePath = source.archivePath,
                            )

                            is TaskSource.Loose -> UndoInfo(
                                kind = UndoKind.MOVED,
                                createdFiles = files,
                                restoreTo = task.entryPaths,
                            )
                        }
                        logRepository.append(
                            LogLevel.SUCCESS,
                            "${task.primaryName} → ${task.targetDir} (${files.size} Datei(en))",
                            undo = undo,
                        )
                    }.onFailure { error ->
                        failed++
                        val message = error.message ?: "Unbekannter Fehler"
                        failures += ExtractionFailure(
                            taskId = task.id,
                            taskName = task.primaryName,
                            message = message,
                        )
                        logRepository.append(
                            LogLevel.ERROR,
                            "${task.primaryName}: $message",
                        )
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Cancel mid-group: the extractor already rolled the group
                // back. Finalization below runs in a NonCancellable context.
                cancelled = true
            }

            // After a cancel every ordinary suspend call would throw again —
            // logging and the final state MUST survive, hence NonCancellable.
            withContext(NonCancellable) {
                if (cancelled) {
                    logRepository.append(
                        LogLevel.INFO,
                        "Abgebrochen: ${cancelledTaskName ?: "Einsortieren"}",
                    )
                }

                if (deleteArchives && !cancelled) {
                    for (cleanup in archiveCleanups) {
                        if (cleanup.taskIds.isNotEmpty() && cleanup.taskIds.all { it in processedIds }) {
                            val removed = if (trashInsteadOfDelete) {
                                romExtractor.moveToTrash(cleanup.archivePath)
                            } else {
                                romExtractor.deleteArchive(cleanup.archivePath)
                            }
                            if (removed) {
                                logRepository.append(
                                    LogLevel.INFO,
                                    if (trashInsteadOfDelete) {
                                        "Quellarchiv in Papierkorb verschoben: ${cleanup.archiveFileName}"
                                    } else {
                                        "Quellarchiv gelöscht: ${cleanup.archiveFileName}"
                                    },
                                )
                            } else {
                                logRepository.append(
                                    LogLevel.ERROR,
                                    "Quellarchiv konnte nicht gelöscht werden: ${cleanup.archiveFileName}",
                                )
                            }
                        }
                    }
                }

                _state.value = ExtractionRunState.Finished(
                    summary = MoveSummary(moved = processed, failed = failed),
                    processedIds = processedIds,
                    failures = failures,
                    cancelled = cancelled,
                )
            }
        }
    }

    /** Requests cancellation; the current group is rolled back. */
    fun cancel() {
        runJob?.cancel()
    }

    /** Called by the UI after consuming a [ExtractionRunState.Finished]. */
    fun acknowledgeFinished() {
        if (_state.value is ExtractionRunState.Finished) {
            _state.value = ExtractionRunState.Idle
        }
    }
}
