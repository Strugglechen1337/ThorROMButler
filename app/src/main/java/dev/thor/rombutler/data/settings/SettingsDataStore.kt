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
        val WATCHER_ENABLED = booleanPreferencesKey("watcher_enabled")
        val FOLDER_OVERRIDES = stringPreferencesKey("folder_overrides") // JSON object
        val EXTRA_SOURCES = stringPreferencesKey("extra_source_paths") // JSON array
        val TRASH_MODE = booleanPreferencesKey("trash_instead_of_delete")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            romBasePath = prefs[Keys.ROM_BASE_PATH],
            downloadPath = prefs[Keys.DOWNLOAD_PATH],
            deleteArchivesAfterExtract = prefs[Keys.DELETE_ARCHIVES] ?: true,
            autoUpdateCheck = prefs[Keys.AUTO_UPDATE_CHECK] ?: false,
            watcherEnabled = prefs[Keys.WATCHER_ENABLED] ?: false,
            folderOverrides = prefs[Keys.FOLDER_OVERRIDES].parseOverrides(),
            additionalSourcePaths = prefs[Keys.EXTRA_SOURCES].parseStringList(),
            trashInsteadOfDelete = prefs[Keys.TRASH_MODE] ?: false,
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

    override suspend fun setWatcherEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WATCHER_ENABLED] = enabled }
    }

    override suspend fun addSourcePath(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.EXTRA_SOURCES].parseStringList()
            if (path !in current) {
                prefs[Keys.EXTRA_SOURCES] = org.json.JSONArray(current + path).toString()
            }
        }
    }

    override suspend fun removeSourcePath(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.EXTRA_SOURCES].parseStringList()
            prefs[Keys.EXTRA_SOURCES] = org.json.JSONArray(current - path).toString()
        }
    }

    override suspend fun setTrashInsteadOfDelete(enabled: Boolean) {
        dataStore.edit { it[Keys.TRASH_MODE] = enabled }
    }

    override suspend fun setFolderOverride(systemId: String, folder: String?) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FOLDER_OVERRIDES].parseOverrides().toMutableMap()
            val trimmed = folder?.trim()?.trim('/')
            if (trimmed.isNullOrEmpty()) current.remove(systemId) else current[systemId] = trimmed
            prefs[Keys.FOLDER_OVERRIDES] = org.json.JSONObject(current as Map<*, *>).toString()
        }
    }

    private companion object {
        fun String?.parseStringList(): List<String> {
            if (this.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(this)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrDefault(emptyList())
        }

        fun String?.parseOverrides(): Map<String, String> {
            if (this.isNullOrBlank()) return emptyMap()
            return runCatching {
                val obj = org.json.JSONObject(this)
                buildMap {
                    for (key in obj.keys()) {
                        put(key, obj.getString(key))
                    }
                }
            }.getOrDefault(emptyMap())
        }
    }
}
