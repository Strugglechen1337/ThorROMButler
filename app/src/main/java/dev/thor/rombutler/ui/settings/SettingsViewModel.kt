package dev.thor.rombutler.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.data.update.GitHubUpdateChecker
import dev.thor.rombutler.data.update.UpdateInfo
import dev.thor.rombutler.domain.model.AppSettings
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the manual update check. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Done(val info: UpdateInfo) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val updateChecker: GitHubUpdateChecker,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    /** Installed versionName, e.g. "0.1.0". */
    val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    fun setDownloadPath(path: String) {
        viewModelScope.launch { settingsRepository.setDownloadPath(path) }
    }

    fun setRomBasePath(path: String) {
        viewModelScope.launch { settingsRepository.setRomBasePath(path) }
    }

    fun setDeleteArchives(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDeleteArchivesAfterExtract(enabled) }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUpdateCheck(enabled) }
    }

    /** Manual update check — the app's only network access. */
    fun checkForUpdates() {
        if (_updateState.value == UpdateCheckState.Checking) return
        _updateState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            updateChecker.check(appVersion)
                .onSuccess { _updateState.value = UpdateCheckState.Done(it) }
                .onFailure {
                    _updateState.value =
                        UpdateCheckState.Failed(it.message ?: "?")
                }
        }
    }

    /**
     * Downloads the release APK via [DownloadManager]. The system shows a
     * download notification; tapping it when finished opens the installer.
     */
    fun downloadUpdate(info: UpdateInfo) {
        val url = info.apkDownloadUrl ?: return
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Thor ROM Butler ${info.latestVersion}")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "ThorRomButler-v${info.latestVersion}.apk",
            )
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
            .enqueue(request)
    }
}
