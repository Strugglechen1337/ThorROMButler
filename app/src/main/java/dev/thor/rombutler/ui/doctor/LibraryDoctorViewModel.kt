package dev.thor.rombutler.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.backup.BackupManager
import dev.thor.rombutler.backup.BackupMode
import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.repository.ExactDuplicateReport
import dev.thor.rombutler.domain.repository.LibraryArchiveIssue
import dev.thor.rombutler.domain.repository.LibraryBackupState
import dev.thor.rombutler.domain.repository.LibraryBiosState
import dev.thor.rombutler.domain.repository.LibraryDatState
import dev.thor.rombutler.domain.repository.LibraryReferenceIssue
import dev.thor.rombutler.domain.repository.LibraryReport
import dev.thor.rombutler.domain.repository.LibraryRepository
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.ui.review.ReviewSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface DoctorState {
    data object Idle : DoctorState
    data object Running : DoctorState
    data class Done(val report: LibraryReport) : DoctorState
    data class Failed(val message: String) : DoctorState
}

sealed interface DoctorDuplicateState {
    data object Idle : DoctorDuplicateState
    data object Running : DoctorDuplicateState
    data class Done(val report: ExactDuplicateReport) : DoctorDuplicateState
    data class Failed(val message: String) : DoctorDuplicateState
}

/** State and actions for the read-only library health center. */
@HiltViewModel
class LibraryDoctorViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val reviewSession: ReviewSession,
    private val backupManager: BackupManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<DoctorState>(DoctorState.Idle)
    val state: StateFlow<DoctorState> = _state.asStateFlow()

    private val _duplicateState =
        MutableStateFlow<DoctorDuplicateState>(DoctorDuplicateState.Idle)
    val duplicateState: StateFlow<DoctorDuplicateState> = _duplicateState.asStateFlow()

    private val _ignoredIssueIds = MutableStateFlow<Set<String>>(emptySet())
    val ignoredIssueIds: StateFlow<Set<String>> = _ignoredIssueIds.asStateFlow()

    val backupState = backupManager.state
    private var duplicateJob: Job? = null

    fun checkLibrary() {
        if (_state.value == DoctorState.Running) return
        duplicateJob?.cancel()
        _duplicateState.value = DoctorDuplicateState.Idle
        _ignoredIssueIds.value = emptySet()
        _state.value = DoctorState.Running
        viewModelScope.launch {
            _state.value = runCatching { libraryRepository.check() }
                .fold(
                    onSuccess = { DoctorState.Done(it) },
                    onFailure = { DoctorState.Failed(it.message ?: "?") },
                )
        }
    }

    fun findExactDuplicates() {
        if (_duplicateState.value == DoctorDuplicateState.Running) return
        _duplicateState.value = DoctorDuplicateState.Running
        duplicateJob = viewModelScope.launch {
            _duplicateState.value = runCatching { libraryRepository.findExactDuplicates() }
                .fold(
                    onSuccess = { DoctorDuplicateState.Done(it) },
                    onFailure = { DoctorDuplicateState.Failed(it.message ?: "?") },
                )
        }
    }

    fun ignoreIssue(id: String) {
        _ignoredIssueIds.value += id
    }

    fun showIgnoredIssues() {
        _ignoredIssueIds.value = emptySet()
    }

    fun prepareMisplacedReview(report: LibraryReport): Boolean {
        if (report.misplaced.isEmpty()) return false
        reviewSession.analyses = emptyList()
        reviewSession.looseRoms = report.misplaced
        return true
    }

    fun startBackup() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val source = settings.romBasePath ?: return@launch
            val target = settings.backupTargetPath ?: return@launch
            backupManager.start(BackupMode.BACKUP, source, target)
        }
    }

    fun acknowledgeBackupAndRefresh() {
        backupManager.acknowledgeFinished()
        checkLibrary()
    }

    fun exportReport(
        report: LibraryReport,
        exactDuplicates: ExactDuplicateReport?,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val success = withContext(ioDispatcher) {
                runCatching {
                    val dir = settingsRepository.settings.first().downloadPath
                        ?: error("Kein Download-Ordner")
                    File(dir, REPORT_FILE_NAME).writeText(buildReport(report, exactDuplicates))
                }.isSuccess
            }
            onResult(success)
        }
    }

    private fun buildReport(
        report: LibraryReport,
        exactDuplicates: ExactDuplicateReport?,
    ): String = buildString {
        appendLine("# Thor ROM Butler - Bibliotheks-Doktor / Library Doctor")
        appendLine()
        appendLine("**${report.totalRoms} ROMs** | ${formatBytes(report.totalBytes)}")
        appendLine()
        appendLine("## Systeme / Systems")
        for (stat in report.stats) {
            appendLine("- **${stat.displayName}**: ${stat.romCount} ROMs | ${formatBytes(stat.totalBytes)}")
        }
        appendLine()
        appendLine("## Bereitschaft / Readiness")
        appendLine("- BIOS: ${biosState(report.biosHealth.state)} (${report.biosHealth.knownFileCount})")
        appendLine("- DAT: ${datState(report.datHealth.state)} (${report.datHealth.datFileCount})")
        appendLine("- Backup: ${backupState(report.backupHealth.state)}")

        if (report.misplaced.isNotEmpty()) {
            appendLine()
            appendLine("## Falsch einsortiert / Misplaced")
            report.misplaced.forEach { rom -> appendLine("- `${rom.group.primary}`") }
        }
        if (report.referenceIssues.isNotEmpty()) {
            appendLine()
            appendLine("## Defekte Verweise / Broken references")
            for (issue in report.referenceIssues) {
                appendLine("- `${issue.filePath}`: ${referenceProblem(issue.problem)}")
                issue.missingReferences.forEach { appendLine("  - fehlt / missing: `$it`") }
            }
        }
        if (report.archiveIssues.isNotEmpty()) {
            appendLine()
            appendLine("## Archivprobleme / Archive problems")
            report.archiveIssues.forEach {
                appendLine("- `${it.filePath}`: ${archiveProblem(it.problem)}")
            }
        }
        if (report.duplicates.isNotEmpty()) {
            appendLine()
            appendLine("## Varianten / Variants (1G1R)")
            for (group in report.duplicates) {
                appendLine("- **${group.title}** (${group.systemName})")
                group.recommendation?.let { recommendation ->
                    val reasons = recommendation.reasons.joinToString { variantReason(it) }
                    appendLine(
                        "  - Empfehlung / recommendation: ${recommendation.fileName}" +
                            if (reasons.isEmpty()) "" else " ($reasons)",
                    )
                }
                group.variants.forEach { appendLine("  - $it") }
            }
        }
        if (exactDuplicates != null) {
            appendLine()
            appendLine("## Exakte Duplikate / Exact duplicates (SHA-256)")
            appendLine(
                "${exactDuplicates.groups.size} Gruppen / groups | " +
                    "${exactDuplicates.duplicateFiles} Dateien / files | " +
                    "${formatBytes(exactDuplicates.reclaimableBytes)} freigebbar / reclaimable",
            )
            for (group in exactDuplicates.groups) {
                appendLine("- **${formatBytes(group.sizeBytes)}** | `${group.sha256}`")
                group.files.forEach { appendLine("  - `$it`") }
            }
        }
    }

    private fun biosState(state: LibraryBiosState): String = when (state) {
        LibraryBiosState.NOT_CONFIGURED -> "nicht konfiguriert / not configured"
        LibraryBiosState.FOLDER_MISSING -> "Ordner fehlt / folder missing"
        LibraryBiosState.NONE_DETECTED -> "keine bekannten Dateien / none recognized"
        LibraryBiosState.READY -> "bereit / ready"
    }

    private fun datState(state: LibraryDatState): String = when (state) {
        LibraryDatState.NOT_CONFIGURED -> "nicht konfiguriert / not configured"
        LibraryDatState.FOLDER_MISSING -> "Ordner fehlt / folder missing"
        LibraryDatState.NO_DAT_FILES -> "keine DAT-Dateien / no DAT files"
        LibraryDatState.NO_USABLE_ENTRIES -> "keine nutzbaren CRC-Einträge / no usable CRC entries"
        LibraryDatState.READY -> "bereit / ready"
    }

    private fun referenceProblem(
        problem: dev.thor.rombutler.domain.repository.LibraryReferenceProblem,
    ): String = when (problem) {
        dev.thor.rombutler.domain.repository.LibraryReferenceProblem.EMPTY ->
            "leer / empty"
        dev.thor.rombutler.domain.repository.LibraryReferenceProblem.MISSING_FILES ->
            "Dateien fehlen / missing files"
        dev.thor.rombutler.domain.repository.LibraryReferenceProblem.UNREADABLE ->
            "nicht lesbar / unreadable"
    }

    private fun archiveProblem(
        problem: dev.thor.rombutler.domain.repository.LibraryArchiveProblem,
    ): String = when (problem) {
        dev.thor.rombutler.domain.repository.LibraryArchiveProblem.EMPTY -> "leer / empty"
        dev.thor.rombutler.domain.repository.LibraryArchiveProblem.UNREADABLE ->
            "nicht lesbar / unreadable"
    }

    private fun backupState(state: LibraryBackupState): String = when (state) {
        LibraryBackupState.NOT_CONFIGURED -> "nicht konfiguriert / not configured"
        LibraryBackupState.FOLDER_MISSING -> "Ordner fehlt / folder missing"
        LibraryBackupState.NO_MANIFEST -> "noch keine Sicherung / no backup yet"
        LibraryBackupState.DIFFERENT_LIBRARY -> "andere Bibliothek / different library"
        LibraryBackupState.OUTDATED -> "veraltet / outdated"
        LibraryBackupState.CURRENT -> "aktuell / current"
    }

    private fun variantReason(
        reason: dev.thor.rombutler.domain.library.VariantRecommendationReason,
    ): String = when (reason) {
        dev.thor.rombutler.domain.library.VariantRecommendationReason.PREFERRED_LANGUAGE ->
            "Sprache / language"
        dev.thor.rombutler.domain.library.VariantRecommendationReason.PREFERRED_REGION ->
            "Region / region"
        dev.thor.rombutler.domain.library.VariantRecommendationReason.CLEAN_DUMP ->
            "sauberer Dump / clean dump"
        dev.thor.rombutler.domain.library.VariantRecommendationReason.NEWER_REVISION ->
            "neuere Revision / newer revision"
    }

    companion object {
        const val MISPLACED_ID = "misplaced"
        const val VARIANTS_ID = "variants"

        fun issueId(issue: LibraryReferenceIssue): String = "reference:${issue.filePath}"
        fun issueId(issue: LibraryArchiveIssue): String = "archive:${issue.filePath}"

        private const val REPORT_FILE_NAME = "ThorRomButler-Bibliotheks-Doktor.md"

        private fun formatBytes(bytes: Long): String {
            val gb = 1024.0 * 1024 * 1024
            val mb = 1024.0 * 1024
            return if (bytes >= gb) {
                String.format(java.util.Locale.ROOT, "%.1f GB", bytes / gb)
            } else {
                String.format(java.util.Locale.ROOT, "%.0f MB", bytes / mb)
            }
        }
    }
}
