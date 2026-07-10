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

    /** Persists the opt-in background watcher mode. */
    suspend fun setWatcherEnabled(enabled: Boolean)

    /** Adds an extra source folder to scan. */
    suspend fun addSourcePath(path: String)

    /** Removes an extra source folder. */
    suspend fun removeSourcePath(path: String)

    /** Persists the trash-instead-of-delete behavior for archives. */
    suspend fun setTrashInsteadOfDelete(enabled: Boolean)

    /** Persists the folder containing `.dat` files (null/blank = disable). */
    suspend fun setDatFolderPath(path: String?)

    /** Persists the color theme id ("thor", "odin", "crt"). */
    suspend fun setThemeId(themeId: String)

    /** Version code whose what's-new dialog was already shown (0 = never). */
    suspend fun lastSeenVersionCode(): Int

    /** Marks the what's-new dialog of [versionCode] as shown. */
    suspend fun setLastSeenVersionCode(versionCode: Int)

    /**
     * Overrides the target folder name for one system; `null` or blank
     * restores the ES-DE default.
     */
    suspend fun setFolderOverride(systemId: String, folder: String?)

    /** Persists a validated custom system pack; `null` removes all custom systems. */
    suspend fun setCustomSystemPack(json: String?)

    /** Replaces all user-configurable settings in one atomic DataStore transaction. */
    suspend fun replaceSettings(settings: AppSettings)
}
