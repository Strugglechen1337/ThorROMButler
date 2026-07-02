package dev.thor.rombutler

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides the start destination once the persisted settings are loaded:
 * completed setup (incl. granted permission) goes straight to the scan
 * screen, everything else starts at setup.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
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
}
