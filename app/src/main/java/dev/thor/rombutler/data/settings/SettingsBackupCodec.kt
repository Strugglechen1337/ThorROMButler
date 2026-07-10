package dev.thor.rombutler.data.settings

import dev.thor.rombutler.domain.detection.SystemPackCodec
import dev.thor.rombutler.domain.model.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/** Strict, backwards-compatible codec for local settings backups. */
object SettingsBackupCodec {

    fun encode(settings: AppSettings): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("romBasePath", settings.romBasePath ?: JSONObject.NULL)
        .put("downloadPath", settings.downloadPath ?: JSONObject.NULL)
        .put("additionalSourcePaths", JSONArray(settings.additionalSourcePaths))
        .put("deleteArchivesAfterExtract", settings.deleteArchivesAfterExtract)
        .put("trashInsteadOfDelete", settings.trashInsteadOfDelete)
        .put("autoUpdateCheck", settings.autoUpdateCheck)
        .put("watcherEnabled", settings.watcherEnabled)
        .put("folderOverrides", JSONObject(settings.folderOverrides as Map<*, *>))
        .put("datFolderPath", settings.datFolderPath ?: JSONObject.NULL)
        .put("themeId", settings.themeId)
        .put(
            "customSystemPack",
            settings.customSystemPackJson?.let(::JSONObject) ?: JSONObject.NULL,
        )
        .toString(2)

    /**
     * Parses and validates the complete backup before any setting is written.
     * Missing fields keep their current value so backups from older releases
     * remain importable.
     */
    fun decode(
        json: String,
        current: AppSettings,
        canonicalizeCustomPack: (String) -> String,
    ): AppSettings {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "Settings backup is too large"
        }
        val root = JSONObject(json)
        val schemaVersion = if (root.has("schemaVersion")) {
            require(root.opt("schemaVersion") is Number) { "Invalid schemaVersion" }
            root.getInt("schemaVersion")
        } else {
            SCHEMA_VERSION // v1 backups originally had no explicit version
        }
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported schemaVersion" }

        return current.copy(
            romBasePath = root.pathSetting("romBasePath", current.romBasePath),
            downloadPath = root.pathSetting("downloadPath", current.downloadPath),
            additionalSourcePaths = root.pathListSetting(
                "additionalSourcePaths",
                current.additionalSourcePaths,
            ),
            deleteArchivesAfterExtract = root.booleanSetting(
                "deleteArchivesAfterExtract",
                current.deleteArchivesAfterExtract,
            ),
            trashInsteadOfDelete = root.booleanSetting(
                "trashInsteadOfDelete",
                current.trashInsteadOfDelete,
            ),
            autoUpdateCheck = root.booleanSetting(
                "autoUpdateCheck",
                current.autoUpdateCheck,
            ),
            watcherEnabled = root.booleanSetting("watcherEnabled", current.watcherEnabled),
            folderOverrides = root.folderOverridesSetting(current.folderOverrides),
            datFolderPath = root.pathSetting("datFolderPath", current.datFolderPath),
            themeId = root.themeSetting(current.themeId),
            customSystemPackJson = root.customPackSetting(
                current.customSystemPackJson,
                canonicalizeCustomPack,
            ),
        )
    }

    private fun JSONObject.pathSetting(key: String, fallback: String?): String? {
        if (!has(key)) return fallback
        if (isNull(key)) return null
        val value = getString(key).trim()
        require(isValidAbsolutePath(value)) { "Invalid $key" }
        return value
    }

    private fun JSONObject.pathListSetting(key: String, fallback: List<String>): List<String> {
        if (!has(key)) return fallback
        val values = getJSONArray(key)
        require(values.length() <= MAX_SOURCE_PATHS) { "Too many source paths" }
        return buildList {
            for (index in 0 until values.length()) {
                val value = values.getString(index).trim()
                require(isValidAbsolutePath(value)) { "Invalid $key[$index]" }
                if (value !in this) add(value)
            }
        }
    }

    private fun JSONObject.booleanSetting(key: String, fallback: Boolean): Boolean {
        if (!has(key)) return fallback
        require(opt(key) is Boolean) { "Invalid $key" }
        return getBoolean(key)
    }

    private fun JSONObject.folderOverridesSetting(fallback: Map<String, String>): Map<String, String> {
        if (!has("folderOverrides")) return fallback
        val values = getJSONObject("folderOverrides")
        require(values.length() <= MAX_FOLDER_OVERRIDES) { "Too many folder overrides" }
        return buildMap {
            for (systemId in values.keys()) {
                val folder = values.getString(systemId).trim()
                require(SystemPackCodec.isValidSystemId(systemId)) { "Invalid system id" }
                require(SystemPackCodec.isValidFolder(folder)) { "Invalid target folder" }
                put(systemId, folder)
            }
        }
    }

    private fun JSONObject.themeSetting(fallback: String): String {
        if (!has("themeId")) return fallback
        val value = getString("themeId")
        require(value in SUPPORTED_THEMES) { "Invalid themeId" }
        return value
    }

    private fun JSONObject.customPackSetting(
        fallback: String?,
        canonicalize: (String) -> String,
    ): String? {
        if (!has("customSystemPack")) return fallback
        if (isNull("customSystemPack")) return null
        return canonicalize(getJSONObject("customSystemPack").toString())
    }

    private fun isValidAbsolutePath(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_PATH_LENGTH &&
            value.none(Char::isISOControl) && value.startsWith('/')

    const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
    private const val SCHEMA_VERSION = 1
    private const val MAX_SOURCE_PATHS = 64
    private const val MAX_FOLDER_OVERRIDES = 256
    private const val MAX_PATH_LENGTH = 4096
    private val SUPPORTED_THEMES = setOf("thor", "odin", "crt")
}
