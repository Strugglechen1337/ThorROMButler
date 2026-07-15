package dev.thor.rombutler.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.data.patch.PatchPair
import dev.thor.rombutler.data.patch.PatchScanner
import dev.thor.rombutler.data.update.UpdateAvailability
import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.model.RomArchive
import dev.thor.rombutler.domain.repository.ArchiveAnalyzer
import dev.thor.rombutler.domain.repository.ArchiveRepository
import dev.thor.rombutler.domain.repository.LooseRomRepository
import dev.thor.rombutler.ui.review.ReviewSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One archive in the list plus its analysis progress.
 *
 * @property analysis `null` while the analysis is still running.
 */
data class ArchiveListItem(
    val archive: RomArchive,
    val analysis: ArchiveAnalysis? = null,
)

/**
 * UI state of the scan screen.
 */
sealed interface ScanUiState {
    /** Directory scan in progress. */
    data object Scanning : ScanUiState

    /** All-files permission was revoked after setup — scanning is impossible. */
    data object PermissionMissing : ScanUiState

    /** Scan finished without finding any archives or loose ROMs. */
    data object Empty : ScanUiState

    /** Archives and/or loose ROM files found; analyses stream in. */
    data class Found(
        val items: List<ArchiveListItem>,
        val looseRoms: List<DetectedRom> = emptyList(),
        val patches: List<PatchPair> = emptyList(),
        /** Path of the patch currently being applied, null when idle. */
        val applyingPatch: String? = null,
        /** User-facing message of the last failed patch attempt. */
        val patchError: String? = null,
        /** Loose BIOS/firmware files found in the scan folders. */
        val biosFiles: List<String> = emptyList(),
        /** True when a BIOS target folder is configured in settings. */
        val biosFolderSet: Boolean = false,
        /** True while BIOS files are being moved. */
        val movingBios: Boolean = false,
    ) : ScanUiState {
        /** All analyses finished? */
        val analysisComplete: Boolean get() = items.all { it.analysis != null }

        /**
         * Anything to review? Unknown non-BIOS archive members can be assigned
         * manually and are extracted unless the user selects a packed system.
         */
        val hasReviewableRoms: Boolean
            get() = looseRoms.isNotEmpty() ||
                items.any { item ->
                    val success = item.analysis as? ArchiveAnalysis.Success
                    success != null &&
                        (success.roms.isNotEmpty() || success.fallbackMembers.isNotEmpty())
                }
    }
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    private val archiveAnalyzer: ArchiveAnalyzer,
    private val looseRomRepository: LooseRomRepository,
    private val patchScanner: PatchScanner,
    private val biosSorter: dev.thor.rombutler.data.files.BiosSorter,
    private val reviewSession: ReviewSession,
    updateAvailability: UpdateAvailability,
) : ViewModel() {

    /** Non-null when the (opt-in) start-up check found a newer release. */
    val updateAvailable = updateAvailability.available

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        rescan()
    }

    /**
     * Hands the successful analyses over to the review flow.
     *
     * @return true when there is something to review.
     */
    fun prepareReview(): Boolean {
        val state = _uiState.value as? ScanUiState.Found ?: return false
        val successes = state.items.mapNotNull { item ->
            (item.analysis as? ArchiveAnalysis.Success)?.takeIf {
                it.roms.isNotEmpty() || it.fallbackMembers.isNotEmpty()
            }
        }
        if (successes.isEmpty() && state.looseRoms.isEmpty()) return false
        reviewSession.analyses = successes
        reviewSession.looseRoms = state.looseRoms
        return true
    }

    /**
     * Rescan unless a directory scan is already running — used when the
     * screen resumes (e.g. returning from review after a move run) so
     * moved archives disappear from the list.
     */
    fun rescanIfIdle() {
        if (_uiState.value != ScanUiState.Scanning) rescan()
    }

    /** Scans the download folder, then analyzes each archive sequentially. */
    fun rescan() {
        scanJob?.cancel()
        // Permission can be revoked in the system settings at any time —
        // without it the scan would just look empty, which is misleading.
        if (!android.os.Environment.isExternalStorageManager()) {
            _uiState.value = ScanUiState.PermissionMissing
            return
        }
        _uiState.value = ScanUiState.Scanning
        scanJob = viewModelScope.launch {
            val archives = archiveRepository.scanForArchives()
            val looseRoms = looseRomRepository.scanAndDetect()
            val patches = patchScanner.findPairs()
            val biosScan = biosSorter.scan()
            if (archives.isEmpty() && looseRoms.isEmpty() && patches.isEmpty() &&
                biosScan.files.isEmpty()
            ) {
                _uiState.value = ScanUiState.Empty
                return@launch
            }
            _uiState.value = ScanUiState.Found(
                items = archives.map { ArchiveListItem(it) },
                looseRoms = looseRoms,
                patches = patches,
                biosFiles = biosScan.files,
                biosFolderSet = biosScan.folderSet,
            )

            // Two analyses in parallel: disk-bound, but one slow/broken
            // archive must not stall all the others. Each card fills in
            // as soon as its analysis finishes.
            val semaphore = Semaphore(2)
            coroutineScope {
                for (archive in archives) {
                    launch {
                        semaphore.withPermit {
                            val analysis = analyzeWithTimeout(archive)
                            _uiState.update { state ->
                                if (state !is ScanUiState.Found) return@update state
                                state.copy(
                                    items = state.items.map { item ->
                                        if (item.archive.path == archive.path) {
                                            item.copy(analysis = analysis)
                                        } else {
                                            item
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies one patch/ROM pair; the patched game is written next to the
     * ROM and appears with the automatic rescan afterwards. The source ROM
     * and the patch file stay untouched.
     */
    fun applyPatch(pair: PatchPair) {
        val state = _uiState.value as? ScanUiState.Found ?: return
        if (state.applyingPatch != null) return
        _uiState.value = state.copy(applyingPatch = pair.patchPath, patchError = null)

        viewModelScope.launch {
            runCatching { patchScanner.apply(pair) }
                .onSuccess { rescan() }
                .onFailure { error ->
                    _uiState.update { current ->
                        if (current !is ScanUiState.Found) return@update current
                        current.copy(
                            applyingPatch = null,
                            patchError = error.message ?: "Patch fehlgeschlagen",
                        )
                    }
                }
        }
    }

    /**
     * Moves all found BIOS files into the configured BIOS folder and
     * rescans; the log records one undoable entry for the whole run.
     */
    fun moveBiosFiles() {
        val state = _uiState.value as? ScanUiState.Found ?: return
        if (state.movingBios || state.biosFiles.isEmpty()) return
        _uiState.value = state.copy(movingBios = true)

        viewModelScope.launch {
            runCatching { biosSorter.moveAll(state.biosFiles) }
            rescan()
        }
    }

    /**
     * Guards against broken/pathological archives: after [ANALYSIS_TIMEOUT_MS]
     * the archive is reported as failed and the scan moves on. The blocked
     * read may finish in the background (blocking I/O is not interruptible),
     * but UI and remaining archives are no longer held hostage.
     */
    private suspend fun analyzeWithTimeout(archive: RomArchive): ArchiveAnalysis {
        val deferred = viewModelScope.async { archiveAnalyzer.analyze(archive) }
        // Safety net: the analyzer already maps errors to Failed, but a
        // stray Throwable (e.g. OOM surfacing at await) must never take
        // down the whole scan — one bad archive stays one failed card.
        return try {
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) { deferred.await() }
                ?: run {
                    deferred.cancel()
                    ArchiveAnalysis.Failed(
                        archive,
                        "Zeitlimit überschritten – Archiv beschädigt oder extrem komprimiert?",
                    )
                }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            deferred.cancel()
            ArchiveAnalysis.Failed(
                archive,
                error.message ?: "Analyse fehlgeschlagen",
            )
        }
    }

    private companion object {
        const val ANALYSIS_TIMEOUT_MS = 90_000L
    }
}
