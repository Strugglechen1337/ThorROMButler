package dev.thor.rombutler.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.data.log.CrashLog
import dev.thor.rombutler.data.update.GitHubUpdateChecker
import dev.thor.rombutler.data.update.UpdateInfo
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.repository.LibraryReport
import dev.thor.rombutler.domain.repository.LibraryRepository
import dev.thor.rombutler.ui.review.ReviewSession
import dev.thor.rombutler.watcher.WatcherScheduler
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

/** State of the on-demand library check. */
sealed interface LibraryCheckState {
    data object Idle : LibraryCheckState
    data object Running : LibraryCheckState
    data class Done(val report: LibraryReport) : LibraryCheckState
    data class Failed(val message: String) : LibraryCheckState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val updateChecker: GitHubUpdateChecker,
    private val crashLog: CrashLog,
    private val watcherScheduler: WatcherScheduler,
    private val libraryRepository: LibraryRepository,
    private val reviewSession: ReviewSession,
    private val receiveManager: dev.thor.rombutler.receive.ReceiveManager,
    val registry: SystemRegistry,
) : ViewModel() {

    private val _libraryState = MutableStateFlow<LibraryCheckState>(LibraryCheckState.Idle)
    val libraryState: StateFlow<LibraryCheckState> = _libraryState.asStateFlow()

    /** True when a crash was recorded — shows the share row in settings. */
    val hasCrashReport: Boolean = crashLog.exists()

    /** Crash text for the share sheet (last ~100 KB). */
    fun crashReportText(): String? = crashLog.read()

    fun clearCrashReport() = crashLog.clear()

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

    fun setWatcherEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWatcherEnabled(enabled)
            watcherScheduler.setEnabled(enabled)
        }
    }

    /** LAN receive mode (see [ReceiveManager]). */
    val receiveState = receiveManager.state

    fun startReceive(onFailed: () -> Unit) {
        viewModelScope.launch {
            if (!receiveManager.start()) onFailed()
        }
    }

    fun stopReceive() = receiveManager.stop()

    fun setDatFolderPath(path: String?) {
        viewModelScope.launch { settingsRepository.setDatFolderPath(path) }
    }

    fun setTrashInsteadOfDelete(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTrashInsteadOfDelete(enabled) }
    }

    fun addSourcePath(path: String) {
        viewModelScope.launch { settingsRepository.addSourcePath(path) }
    }

    fun removeSourcePath(path: String) {
        viewModelScope.launch { settingsRepository.removeSourcePath(path) }
    }

    /** Writes all settings as JSON into the download folder. */
    fun exportSettings(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val s = settings.value
                val json = org.json.JSONObject()
                    .put("romBasePath", s.romBasePath)
                    .put("downloadPath", s.downloadPath)
                    .put("additionalSourcePaths", org.json.JSONArray(s.additionalSourcePaths))
                    .put("deleteArchivesAfterExtract", s.deleteArchivesAfterExtract)
                    .put("trashInsteadOfDelete", s.trashInsteadOfDelete)
                    .put("autoUpdateCheck", s.autoUpdateCheck)
                    .put("watcherEnabled", s.watcherEnabled)
                    .put("folderOverrides", org.json.JSONObject(s.folderOverrides as Map<*, *>))
                    .toString(2)
                val dir = s.downloadPath ?: error("Kein Download-Ordner")
                java.io.File(dir, BACKUP_FILE_NAME).writeText(json)
            }
            onResult(result.isSuccess)
        }
    }

    /** Reads the JSON backup from the download folder and applies it. */
    fun importSettings(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val dir = settings.value.downloadPath ?: error("Kein Download-Ordner")
                val file = java.io.File(dir, BACKUP_FILE_NAME)
                check(file.isFile) { "Backup-Datei fehlt" }
                val json = org.json.JSONObject(file.readText())

                json.optString("romBasePath").takeIf { it.isNotBlank() }
                    ?.let { settingsRepository.setRomBasePath(it) }
                json.optString("downloadPath").takeIf { it.isNotBlank() }
                    ?.let { settingsRepository.setDownloadPath(it) }
                json.optJSONArray("additionalSourcePaths")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        settingsRepository.addSourcePath(arr.getString(i))
                    }
                }
                settingsRepository.setDeleteArchivesAfterExtract(
                    json.optBoolean("deleteArchivesAfterExtract", true),
                )
                settingsRepository.setTrashInsteadOfDelete(
                    json.optBoolean("trashInsteadOfDelete", false),
                )
                settingsRepository.setAutoUpdateCheck(json.optBoolean("autoUpdateCheck", false))
                val watcher = json.optBoolean("watcherEnabled", false)
                settingsRepository.setWatcherEnabled(watcher)
                watcherScheduler.setEnabled(watcher)
                json.optJSONObject("folderOverrides")?.let { overrides ->
                    for (key in overrides.keys()) {
                        settingsRepository.setFolderOverride(key, overrides.getString(key))
                    }
                }
            }
            onResult(result.isSuccess)
        }
    }

    private companion object {
        const val BACKUP_FILE_NAME = "ThorRomButler-settings.json"
    }

    /** Scans the ROM library: per-system statistics + misplaced ROMs. */
    fun checkLibrary() {
        if (_libraryState.value == LibraryCheckState.Running) return
        _libraryState.value = LibraryCheckState.Running
        viewModelScope.launch {
            _libraryState.value = runCatching { libraryRepository.check() }
                .fold(
                    onSuccess = { LibraryCheckState.Done(it) },
                    onFailure = { LibraryCheckState.Failed(it.message ?: "?") },
                )
        }
    }

    /**
     * Writes the library report as a Markdown list into the download
     * folder (`ThorRomButler-Sammlung.md`).
     */
    fun exportLibrary(report: LibraryReport, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val dir = settings.value.downloadPath ?: error("Kein Download-Ordner")
                val sb = StringBuilder()
                sb.appendLine("# Thor ROM Butler – Sammlung / Collection")
                sb.appendLine()
                sb.appendLine("**${report.totalRoms} ROMs** · ${formatBytes(report.totalBytes)}")
                sb.appendLine()
                for (stat in report.stats) {
                    sb.appendLine("- **${stat.displayName}**: ${stat.romCount} ROMs · ${formatBytes(stat.totalBytes)}")
                }
                if (report.duplicates.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine("## Duplikate / Duplicates (1G1R)")
                    sb.appendLine()
                    for (dup in report.duplicates) {
                        sb.appendLine("- **${dup.title}** (${dup.systemName})")
                        for (variant in dup.variants) {
                            sb.appendLine("  - $variant")
                        }
                    }
                }
                java.io.File(dir, "ThorRomButler-Sammlung.md").writeText(sb.toString())
            }
            onResult(result.isSuccess)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024 * 1024
        val mb = 1024.0 * 1024
        return if (bytes >= gb) {
            String.format(java.util.Locale.ROOT, "%.1f GB", bytes / gb)
        } else {
            String.format(java.util.Locale.ROOT, "%.0f MB", bytes / mb)
        }
    }

    /** Hands the misplaced ROMs to the review flow. */
    fun prepareMisplacedReview(report: LibraryReport): Boolean {
        if (report.misplaced.isEmpty()) return false
        reviewSession.analyses = emptyList()
        reviewSession.looseRoms = report.misplaced
        return true
    }

    /** Blank/empty restores the ES-DE default for that system. */
    fun setFolderOverride(systemId: String, folder: String?) {
        viewModelScope.launch { settingsRepository.setFolderOverride(systemId, folder) }
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
