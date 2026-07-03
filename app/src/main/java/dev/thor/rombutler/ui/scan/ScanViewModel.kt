package dev.thor.rombutler.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.model.RomArchive
import dev.thor.rombutler.domain.repository.ArchiveAnalyzer
import dev.thor.rombutler.domain.repository.ArchiveRepository
import dev.thor.rombutler.domain.repository.LooseRomRepository
import dev.thor.rombutler.ui.review.ReviewSession
import kotlinx.coroutines.Job
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

    /** Scan finished without finding any archives or loose ROMs. */
    data object Empty : ScanUiState

    /** Archives and/or loose ROM files found; analyses stream in. */
    data class Found(
        val items: List<ArchiveListItem>,
        val looseRoms: List<DetectedRom> = emptyList(),
    ) : ScanUiState {
        /** All analyses finished? */
        val analysisComplete: Boolean get() = items.all { it.analysis != null }

        /** Anything to review (at least one readable ROM)? */
        val hasReviewableRoms: Boolean
            get() = looseRoms.isNotEmpty() ||
                items.any { (it.analysis as? ArchiveAnalysis.Success)?.roms?.isNotEmpty() == true }
    }
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    private val archiveAnalyzer: ArchiveAnalyzer,
    private val looseRomRepository: LooseRomRepository,
    private val reviewSession: ReviewSession,
) : ViewModel() {

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
        val successes = state.items
            .mapNotNull { it.analysis as? ArchiveAnalysis.Success }
            .filter { it.roms.isNotEmpty() }
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
        _uiState.value = ScanUiState.Scanning
        scanJob = viewModelScope.launch {
            val archives = archiveRepository.scanForArchives()
            val looseRoms = looseRomRepository.scanAndDetect()
            if (archives.isEmpty() && looseRoms.isEmpty()) {
                _uiState.value = ScanUiState.Empty
                return@launch
            }
            _uiState.value = ScanUiState.Found(
                items = archives.map { ArchiveListItem(it) },
                looseRoms = looseRoms,
            )

            // Sequential on purpose: archive I/O is disk-bound, and cards
            // filling in one after another reads as pleasant progress.
            for (archive in archives) {
                val analysis = archiveAnalyzer.analyze(archive)
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
