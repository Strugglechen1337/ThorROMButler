package dev.thor.rombutler.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.model.RomArchive
import dev.thor.rombutler.domain.repository.ArchiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state of the scan screen.
 */
sealed interface ScanUiState {
    /** Scan in progress. */
    data object Scanning : ScanUiState

    /** Scan finished without finding any archives. */
    data object Empty : ScanUiState

    /** Scan finished; archives are listed newest first. */
    data class Found(val archives: List<RomArchive>) : ScanUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        rescan()
    }

    /** Starts a fresh scan of the download folder. */
    fun rescan() {
        _uiState.value = ScanUiState.Scanning
        viewModelScope.launch {
            val archives = archiveRepository.scanForArchives()
            _uiState.value = if (archives.isEmpty()) {
                ScanUiState.Empty
            } else {
                ScanUiState.Found(archives)
            }
        }
    }
}
