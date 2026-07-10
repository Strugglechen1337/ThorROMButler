package dev.thor.rombutler.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.thor.rombutler.domain.detection.SystemPackCodec
import dev.thor.rombutler.domain.model.AppSettings
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version_code")
        val DAT_FOLDER = stringPreferencesKey("dat_folder_path")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_SYSTEM_PACK = stringPreferencesKey("custom_system_pack_json")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            romBasePath = prefs[Keys.ROM_BASE_PATH],
            downloadPath = prefs[Keys.DOWNLOAD_PATH],
            deleteArchivesAfterExtract = prefs[Keys.DELETE_ARCHIVES] ?: false,
            autoUpdateCheck = prefs[Keys.AUTO_UPDATE_CHECK] ?: false,
            watcherEnabled = prefs[Keys.WATCHER_ENABLED] ?: false,
            folderOverrides = prefs[Keys.FOLDER_OVERRIDES].parseOverrides(),
            additionalSourcePaths = prefs[Keys.EXTRA_SOURCES].parseStringList(),
            trashInsteadOfDelete = prefs[Keys.TRASH_MODE] ?: false,
            datFolderPath = prefs[Keys.DAT_FOLDER],
            themeId = prefs[Keys.THEME_ID] ?: "thor",
            customSystemPackJson = prefs[Keys.CUSTOM_SYSTEM_PACK],
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

    override suspend fun setDatFolderPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(Keys.DAT_FOLDER) else prefs[Keys.DAT_FOLDER] = path
        }
    }

    override suspend fun setThemeId(themeId: String) {
        dataStore.edit { it[Keys.THEME_ID] = themeId }
    }

    override suspend fun lastSeenVersionCode(): Int =
        dataStore.data.map { it[Keys.LAST_SEEN_VERSION] ?: 0 }.first()

    override suspend fun setLastSeenVersionCode(versionCode: Int) {
        dataStore.edit { it[Keys.LAST_SEEN_VERSION] = versionCode }
    }

    override suspend fun setFolderOverride(systemId: String, folder: String?) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FOLDER_OVERRIDES].parseOverrides().toMutableMap()
            val trimmed = folder?.trim()?.trim('/')
            if (trimmed.isNullOrEmpty()) {
                current.remove(systemId)
            } else {
                require(SystemPackCodec.isValidSystemId(systemId)) { "Invalid system id" }
                require(SystemPackCodec.isValidFolder(trimmed)) { "Invalid target folder" }
                current[systemId] = trimmed
            }
            prefs[Keys.FOLDER_OVERRIDES] = org.json.JSONObject(current as Map<*, *>).toString()
        }
    }

    override suspend fun setCustomSystemPack(json: String?) {
        dataStore.edit { prefs ->
            if (json.isNullOrBlank()) {
                prefs.remove(Keys.CUSTOM_SYSTEM_PACK)
            } else {
                prefs[Keys.CUSTOM_SYSTEM_PACK] = json
            }
        }
    }

    override suspend fun replaceSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs.setOrRemove(Keys.ROM_BASE_PATH, settings.romBasePath)
            prefs.setOrRemove(Keys.DOWNLOAD_PATH, settings.downloadPath)
            prefs[Keys.DELETE_ARCHIVES] = settings.deleteArchivesAfterExtract
            prefs[Keys.AUTO_UPDATE_CHECK] = settings.autoUpdateCheck
            prefs[Keys.WATCHER_ENABLED] = settings.watcherEnabled
            prefs[Keys.EXTRA_SOURCES] = org.json.JSONArray(settings.additionalSourcePaths).toString()
            prefs[Keys.TRASH_MODE] = settings.trashInsteadOfDelete
            prefs[Keys.FOLDER_OVERRIDES] =
                org.json.JSONObject(settings.folderOverrides as Map<*, *>).toString()
            prefs.setOrRemove(Keys.DAT_FOLDER, settings.datFolderPath)
            prefs[Keys.THEME_ID] = settings.themeId
            prefs.setOrRemove(Keys.CUSTOM_SYSTEM_PACK, settings.customSystemPackJson)
        }
    }

    private companion object {
        fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
            key: Preferences.Key<String>,
            value: String?,
        ) {
            if (value.isNullOrBlank()) remove(key) else this[key] = value
        }

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
                        val folder = obj.getString(key)
                        if (SystemPackCodec.isValidSystemId(key) &&
                            SystemPackCodec.isValidFolder(folder)
                        ) {
                            put(key, folder)
                        }
                    }
                }
            }.getOrDefault(emptyMap())
        }
    }
}
