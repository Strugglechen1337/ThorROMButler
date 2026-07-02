package dev.thor.rombutler.ui.setup

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state of the setup screen.
 *
 * @property hasAllFilesAccess whether MANAGE_EXTERNAL_STORAGE is granted.
 * @property romBasePath currently persisted ROM base folder, if any.
 * @property downloadPath currently persisted download folder, if any.
 */
data class SetupUiState(
    val hasAllFilesAccess: Boolean = false,
    val romBasePath: String? = null,
    val downloadPath: String? = null,
) {
    val isComplete: Boolean
        get() = hasAllFilesAccess && !romBasePath.isNullOrBlank() && !downloadPath.isNullOrBlank()
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val hasAllFilesAccess = MutableStateFlow(Environment.isExternalStorageManager())

    val uiState: StateFlow<SetupUiState> =
        combine(settingsRepository.settings, hasAllFilesAccess) { settings, permission ->
            SetupUiState(
                hasAllFilesAccess = permission,
                romBasePath = settings.romBasePath,
                downloadPath = settings.downloadPath,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetupUiState(hasAllFilesAccess = Environment.isExternalStorageManager()),
        )

    /** Re-reads the permission state; called when the screen resumes. */
    fun refreshPermission() {
        hasAllFilesAccess.value = Environment.isExternalStorageManager()
    }

    fun setRomBasePath(path: String) {
        viewModelScope.launch { settingsRepository.setRomBasePath(path) }
    }

    fun setDownloadPath(path: String) {
        viewModelScope.launch { settingsRepository.setDownloadPath(path) }
    }

    /** Default suggestion for the download folder picker. */
    fun defaultDownloadDir(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

    /** Default suggestion for the ROM base folder picker (ES-DE convention). */
    fun defaultRomBaseDir(): String =
        Environment.getExternalStorageDirectory().resolve("ROMs").absolutePath
}
