package dev.thor.rombutler

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.data.files.IncomingFile
import dev.thor.rombutler.data.update.GitHubUpdateChecker
import dev.thor.rombutler.data.update.UpdateAvailability
import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Inject

/**
 * Decides the start destination once the persisted settings are loaded:
 * completed setup (incl. granted permission) goes straight to the scan
 * screen, everything else starts at setup.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val updateChecker: GitHubUpdateChecker,
    private val updateAvailability: UpdateAvailability,
    private val systemRegistry: SystemRegistry,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** `null` while settings are still loading (splash keeps showing). */
    val startDestination: StateFlow<String?> = settingsRepository.settings
        .map { settings ->
            // Activate persisted custom systems before the first scan screen is shown.
            systemRegistry.applyCustomPackJson(settings.customSystemPackJson)
            if (settings.isSetupComplete && Environment.isExternalStorageManager()) {
                Routes.SCAN
            } else {
                Routes.SETUP
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Active color theme id (thor/odin/crt). */
    val themeId: StateFlow<String> = settingsRepository.settings
        .map { it.themeId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "thor")

    /** Version name to show a one-time what's-new dialog for, or null. */
    val whatsNewVersion = MutableStateFlow<String?>(null)

    /** Number of files taken over from a share intent (one-shot, for a toast). */
    val shareResult = MutableStateFlow<Int?>(null)

    fun consumeShareResult() {
        shareResult.value = null
    }

    /**
     * Copies files shared via ACTION_SEND(_MULTIPLE) into the download
     * folder so the normal scan flow picks them up.
     */
    fun handleSharedUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            val downloadPath = settingsRepository.settings.first().downloadPath ?: return@launch
            val downloadDir = File(downloadPath).takeIf { it.isDirectory } ?: return@launch
            var copied = 0
            for (uri in uris) {
                runCatching {
                    val rawName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "shared.bin"
                    val name = IncomingFile.sanitizeName(rawName) ?: return@runCatching
                    val target = IncomingFile.resolveTarget(downloadDir, name) ?: return@runCatching
                    if (target.exists()) return@runCatching // never overwrite silently
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        IncomingFile.copyAtomically(input, target)
                        copied++
                    }
                }
            }
            shareResult.value = copied
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    /** Saves that the dialog was seen; called on dismiss. */
    fun dismissWhatsNew() {
        whatsNewVersion.value = null
        viewModelScope.launch {
            settingsRepository.setLastSeenVersionCode(currentVersionCode())
        }
    }

    private fun currentVersionCode(): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
            .longVersionCode.toInt()
    }.getOrDefault(0)

    init {
        // One-time what's-new dialog after an update (not on fresh installs)
        viewModelScope.launch {
            val seen = settingsRepository.lastSeenVersionCode()
            val current = currentVersionCode()
            if (seen == 0) {
                settingsRepository.setLastSeenVersionCode(current)
            } else if (current > seen) {
                whatsNewVersion.value = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull()
            }
        }

        // Opt-in automatic update check (default OFF, see settings). Errors
        // are swallowed on purpose: a failed background check must never
        // bother the user.
        viewModelScope.launch {
            if (settingsRepository.settings.first().autoUpdateCheck) {
                val version = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: return@launch
                updateChecker.check(version)
                    .onSuccess { updateAvailability.publish(it) }
            }
        }
    }
}
