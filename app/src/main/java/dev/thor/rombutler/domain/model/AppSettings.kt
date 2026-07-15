package dev.thor.rombutler.domain.model

/**
 * User configuration persisted via DataStore.
 *
 * @property romBasePath Absolute path of the ROM base folder (the folder that
 *   contains the per-system subfolders like `nes/`, `snes/`, ...). `null` until
 *   the user completed the setup.
 * @property downloadPath Absolute path of the folder that is scanned for
 *   downloaded ROM archives.
 * @property deleteArchivesAfterExtract when true, archives whose
 *   ROMs were all extracted successfully are removed from the download
 *   folder.
 */
data class AppSettings(
    val romBasePath: String? = null,
    val downloadPath: String? = null,
    val deleteArchivesAfterExtract: Boolean = false,
    /** Opt-in: query GitHub for a newer release on app start (default OFF). */
    val autoUpdateCheck: Boolean = false,
    /** Opt-in: periodic background scan of the download folder (default OFF). */
    val watcherEnabled: Boolean = false,
    /** Extra folders scanned in addition to [downloadPath] (Telegram, USB, ...). */
    val additionalSourcePaths: List<String> = emptyList(),
    /**
     * When deleting processed archives, move them to a hidden `.thor_trash`
     * folder (auto-purged after 7 days) instead of deleting immediately.
     */
    val trashInsteadOfDelete: Boolean = false,
    /**
     * Per-system folder overrides (systemId -> folder name) for users whose
     * frontend does not follow the ES-DE convention (e.g. `roms/ps1`).
     * Missing entries fall back to [dev.thor.rombutler.domain.model.SystemDefinition.esdeFolder].
     */
    val folderOverrides: Map<String, String> = emptyMap(),
    /** Folder containing No-Intro/Redump `.dat` files for verification. */
    val datFolderPath: String? = null,
    /** Color theme id: "thor" (default), "odin", "crt". */
    val themeId: String = "thor",
    /** Validated schema-v1 JSON containing optional user-defined systems. */
    val customSystemPackJson: String? = null,
    /**
     * Write a `.m3u` playlist when several discs of the same game land in a
     * system folder (default ON — additive, existing playlists are kept).
     */
    val writeM3uPlaylists: Boolean = true,
    /**
     * Opt-in: rename sorted single-file ROMs to their canonical DAT name
     * after a successful checksum verification (default OFF).
     */
    val renameToDatName: Boolean = false,
    /** Target folder of the incremental ROM library backup (null = unset). */
    val backupTargetPath: String? = null,
    /** Folder BIOS/firmware files get sorted into (null = feature off). */
    val biosFolderPath: String? = null,
    /** Locally learn from successful user-confirmed system assignments. */
    val assignmentLearningEnabled: Boolean = true,
    /** Bounded local history used only for confirmable PROBABLE suggestions. */
    val learnedAssignments: List<LearnedAssignment> = emptyList(),
) {
    /** Setup is complete once both folders are configured. */
    val isSetupComplete: Boolean
        get() = !romBasePath.isNullOrBlank() && !downloadPath.isNullOrBlank()
}
