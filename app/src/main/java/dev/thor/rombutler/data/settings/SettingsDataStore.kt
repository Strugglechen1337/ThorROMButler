package dev.thor.rombutler.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.thor.rombutler.domain.model.AppSettings
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] backed by Preferences DataStore.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val ROM_BASE_PATH = stringPreferencesKey("rom_base_path")
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val DELETE_ARCHIVES = booleanPreferencesKey("delete_archives_after_extract")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            romBasePath = prefs[Keys.ROM_BASE_PATH],
            downloadPath = prefs[Keys.DOWNLOAD_PATH],
            deleteArchivesAfterExtract = prefs[Keys.DELETE_ARCHIVES] ?: true,
            autoUpdateCheck = prefs[Keys.AUTO_UPDATE_CHECK] ?: false,
        )
    }

    override suspend fun setRomBasePath(path: String) {
        dataStore.edit { it[Keys.ROM_BASE_PATH] = path }
    }

    override suspend fun setDownloadPath(path: String) {
        dataStore.edit { it[Keys.DOWNLOAD_PATH] = path }
    }

    override suspend fun setDeleteArchivesAfterExtract(enabled: Boolean) {
        dataStore.edit { it[Keys.DELETE_ARCHIVES] = enabled }
    }

    override suspend fun setAutoUpdateCheck(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_UPDATE_CHECK] = enabled }
    }
}
