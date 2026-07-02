package dev.thor.rombutler.domain.model

/**
 * User configuration persisted via DataStore.
 *
 * @property romBasePath Absolute path of the ROM base folder (the folder that
 *   contains the per-system subfolders like `nes/`, `snes/`, ...). `null` until
 *   the user completed the setup.
 * @property downloadPath Absolute path of the folder that is scanned for
 *   downloaded ROM archives.
 */
data class AppSettings(
    val romBasePath: String? = null,
    val downloadPath: String? = null,
) {
    /** Setup is complete once both folders are configured. */
    val isSetupComplete: Boolean
        get() = !romBasePath.isNullOrBlank() && !downloadPath.isNullOrBlank()
}
