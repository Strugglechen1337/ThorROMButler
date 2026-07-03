package dev.thor.rombutler

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.data.update.GitHubUpdateChecker
import dev.thor.rombutler.data.update.UpdateAvailability
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides the start destination once the persisted settings are loaded:
 * completed setup (incl. granted permission) goes straight to the scan
 * screen, everything else starts at setup.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    private val updateChecker: GitHubUpdateChecker,
    private val updateAvailability: UpdateAvailability,
) : ViewModel() {

    /** `null` while settings are still loading (splash keeps showing). */
    val startDestination: StateFlow<String?> = settingsRepository.settings
        .map { settings ->
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

    init {
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
