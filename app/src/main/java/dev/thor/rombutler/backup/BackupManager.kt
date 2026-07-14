package dev.thor.rombutler.backup

import dev.thor.rombutler.data.backup.BackupManifest
import dev.thor.rombutler.data.backup.LibraryBackup
import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.LogLevel
import dev.thor.rombutler.domain.repository.LogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Direction of a run: mirror the library out, or bring files back in. */
enum class BackupMode { BACKUP, RESTORE }

/** Live progress of a backup/restore run. */
data class BackupProgress(
    val mode: BackupMode,
    val currentName: String,
    val copiedFiles: Int,
    val totalFiles: Int,
    val fraction: Float,
)

/** Result of a finished run. */
data class BackupSummary(
    val mode: BackupMode,
    val copied: Int,
    val skipped: Int,
    val failed: Int,
    val cancelled: Boolean,
    /** Non-null when the run could not start (preflight/IO error). */
    val errorMessage: String? = null,
)

/** State machine of the backup manager. */
sealed interface BackupRunState {
    data object Idle : BackupRunState
    data class Running(val progress: BackupProgress) : BackupRunState

    /** Consumed by the settings screen via [BackupManager.acknowledgeFinished]. */
    data class Finished(val summary: BackupSummary) : BackupRunState
}

/**
 * Runs library backup/restore in an application-scoped coroutine (survives
 * navigation) and keeps the process alive through [BackupService]. The
 * engine work lives in [LibraryBackup]; this class adds preflight checks,
 * progress, cancellation and log entries.
 */
@Singleton
class BackupManager @Inject constructor(
    private val serviceLauncher: BackupServiceLauncher,
    private val logRepository: LogRepository,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var runJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)

    private val _state = MutableStateFlow<BackupRunState>(BackupRunState.Idle)
    val state: StateFlow<BackupRunState> = _state.asStateFlow()

    /** Manifest of the backup at [targetPath], or null when none exists. */
    fun manifestAt(targetPath: String): BackupManifest? =
        LibraryBackup.readManifest(File(targetPath))

    /** Starts a run. No-op while one is active. */
    fun start(mode: BackupMode, romBasePath: String, backupPath: String) {
        if (_state.value is BackupRunState.Running) return
        cancelRequested.set(false)
        lastShownFraction = -1f
        _state.value = BackupRunState.Running(
            BackupProgress(mode, "", 0, 0, 0f),
        )
        serviceLauncher.launch()

        runJob = scope.launch {
            val summary = runCatching { run(mode, romBasePath, backupPath) }
                .getOrElse { error ->
                    BackupSummary(
                        mode = mode,
                        copied = 0,
                        skipped = 0,
                        failed = 0,
                        cancelled = false,
                        errorMessage = error.message ?: "Unbekannter Fehler",
                    )
                }
            withContext(NonCancellable) {
                logRepository.append(
                    if (summary.errorMessage != null || summary.failed > 0) {
                        LogLevel.ERROR
                    } else {
                        LogLevel.SUCCESS
                    },
                    summary.toLogMessage(),
                )
                _state.value = BackupRunState.Finished(summary)
            }
        }
    }

    private suspend fun run(
        mode: BackupMode,
        romBasePath: String,
        backupPath: String,
    ): BackupSummary {
        val romBase = File(romBasePath)
        val backupDir = File(backupPath)
        val (source, target) = when (mode) {
            BackupMode.BACKUP -> romBase to backupDir
            BackupMode.RESTORE -> backupDir to romBase
        }
        if (!source.isDirectory) error("Quellordner nicht gefunden: $source")
        if (!target.isDirectory && !target.mkdirs()) {
            error("Zielordner konnte nicht angelegt werden: $target")
        }
        // A backup inside the library (or vice versa) would mirror itself
        val romCanonical = romBase.canonicalPath
        val backupCanonical = backupDir.canonicalPath
        if (backupCanonical == romCanonical ||
            backupCanonical.startsWith(romCanonical + File.separator) ||
            romCanonical.startsWith(backupCanonical + File.separator)
        ) {
            error("Sicherungsziel und ROM-Ordner dürfen nicht ineinander liegen")
        }

        // Restore must never overwrite the live library
        val plan = LibraryBackup.plan(source, target, replaceChanged = mode == BackupMode.BACKUP)
        if (plan.filesToCopy.isNotEmpty()) {
            val usable = target.usableSpace
            if (usable in 1 until plan.bytesToCopy + SPACE_MARGIN_BYTES) {
                error("Zu wenig freier Speicher am Ziel (benötigt ${plan.bytesToCopy / MEGABYTE} MB)")
            }
        }

        var doneBytes = 0L
        var copiedCount = 0
        val totalBytes = plan.bytesToCopy.coerceAtLeast(1)
        val result = LibraryBackup.copy(
            source = source,
            target = target,
            plan = plan,
            onFileStarted = { name ->
                copiedCount++
                publishProgress(mode, name, copiedCount, plan.filesToCopy.size, doneBytes, totalBytes)
            },
            onBytesCopied = { delta ->
                doneBytes += delta
                publishProgress(mode, null, copiedCount, plan.filesToCopy.size, doneBytes, totalBytes)
            },
            isCancelled = cancelRequested::get,
        )

        if (mode == BackupMode.BACKUP && !result.cancelled && result.failed.isEmpty()) {
            LibraryBackup.writeManifest(
                target = target,
                manifest = BackupManifest(
                    createdAtMillis = System.currentTimeMillis(),
                    sourcePath = romBasePath,
                    fileCount = result.copied + plan.skippedExisting,
                    totalBytes = doneBytes,
                ),
            )
        }

        return BackupSummary(
            mode = mode,
            copied = result.copied,
            skipped = plan.skippedExisting,
            failed = result.failed.size,
            cancelled = result.cancelled,
        )
    }

    private var lastShownFraction = -1f

    private fun publishProgress(
        mode: BackupMode,
        name: String?,
        copiedFiles: Int,
        totalFiles: Int,
        doneBytes: Long,
        totalBytes: Long,
    ) {
        val fraction = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        val current = _state.value as? BackupRunState.Running
        if (name == null && current != null && fraction - lastShownFraction < 0.005f) return
        lastShownFraction = fraction
        _state.value = BackupRunState.Running(
            BackupProgress(
                mode = mode,
                currentName = name ?: current?.progress?.currentName.orEmpty(),
                copiedFiles = copiedFiles,
                totalFiles = totalFiles,
                fraction = fraction,
            ),
        )
    }

    /** Cooperative cancel; completed files stay (runs resume incrementally). */
    fun cancel() {
        cancelRequested.set(true)
    }

    /** Called by the UI after consuming a [BackupRunState.Finished]. */
    fun acknowledgeFinished() {
        if (_state.value is BackupRunState.Finished) {
            _state.value = BackupRunState.Idle
        }
    }

    private fun BackupSummary.toLogMessage(): String {
        val action = when (mode) {
            BackupMode.BACKUP -> "ROM-Sicherung"
            BackupMode.RESTORE -> "Wiederherstellung"
        }
        return when {
            errorMessage != null -> "$action fehlgeschlagen: $errorMessage"
            cancelled -> "$action abgebrochen: $copied kopiert, $skipped übersprungen"
            failed > 0 -> "$action: $copied kopiert, $skipped übersprungen, $failed fehlgeschlagen"
            else -> "$action abgeschlossen: $copied kopiert, $skipped bereits vorhanden"
        }
    }

    private companion object {
        const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024
        const val MEGABYTE = 1024L * 1024
    }
}
