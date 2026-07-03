package dev.thor.rombutler.domain.repository

import dev.thor.rombutler.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the persisted user settings.
 * Implemented with Preferences DataStore in the data layer.
 */
interface SettingsRepository {

    /** Emits the current settings and every subsequent change. */
    val settings: Flow<AppSettings>

    /** Persists the ROM base folder (parent of the per-system folders). */
    suspend fun setRomBasePath(path: String)

    /** Persists the folder that is scanned for downloaded archives. */
    suspend fun setDownloadPath(path: String)

    /** Persists whether fully extracted archives get deleted. */
    suspend fun setDeleteArchivesAfterExtract(enabled: Boolean)

    /** Persists the opt-in automatic update check on app start. */
    suspend fun setAutoUpdateCheck(enabled: Boolean)
}
