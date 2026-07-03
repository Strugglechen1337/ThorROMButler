package dev.thor.rombutler.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.repository.RomFolderRepository
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.extraction.ArchiveCleanup
import dev.thor.rombutler.extraction.ExtractionManager
import dev.thor.rombutler.extraction.ExtractionProgress
import dev.thor.rombutler.extraction.ExtractionRunState
import dev.thor.rombutler.extraction.ExtractionTask
import dev.thor.rombutler.extraction.MoveSummary
import dev.thor.rombutler.extraction.TaskSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where a review item's files come from.
 */
sealed interface RomSource {
    /** ROM group inside an archive; `memberEntryPaths` are archive-internal. */
    data class ArchiveEntry(
        val archivePath: String,
        val archiveType: ArchiveType,
        val archiveFileName: String,
    ) : RomSource

    /** Loose files in the download folder; `memberEntryPaths` are absolute. */
    data object LooseFiles : RomSource
}

/**
 * One ROM (group) awaiting user review.
 *
 * @property selectedSystemId user-confirmed target system. Prefilled ONLY
 *   for CERTAIN detections — PROBABLE suggestions must be tapped by the
 *   user, UNKNOWN requires a manual pick. The app never decides on doubt.
 * @property targetPath full target directory once a system is selected.
 * @property targetExists whether the SYSTEM folder already exists.
 * @property targetOccupied whether any group file already exists at the
 *   target (duplicate) — the user must explicitly opt into replacing.
 * @property overwrite user chose to replace the existing duplicate files.
 */
data class ReviewItem(
    val id: String,
    val source: RomSource,
    val rom: DetectedRom,
    val selectedSystemId: String? = null,
    val targetPath: String? = null,
    val targetExists: Boolean? = null,
    val targetOccupied: Boolean = false,
    val overwrite: Boolean = false,
)

/** One-shot feedback after a folder-creation run (formatted by the UI). */
data class FolderCreationResult(val created: Int, val failed: Int)

/**
 * UI state of the review screen.
 */
data class ReviewUiState(
    val items: List<ReviewItem> = emptyList(),
    val creatingFolders: Boolean = false,
    val folderResult: FolderCreationResult? = null,
    val moving: Boolean = false,
    val progress: ExtractionProgress? = null,
    val moveSummary: MoveSummary? = null,
) {
    val assignedCount: Int get() = items.count { it.selectedSystemId != null }
    val missingFolderCount: Int get() = items.count { it.selectedSystemId != null && it.targetExists == false }

    /** Ready to process: assigned, and duplicates resolved via overwrite. */
    val processableCount: Int
        get() = items.count { it.selectedSystemId != null && (!it.targetOccupied || it.overwrite) }

    /** Open PROBABLE suggestions the accept-all button would confirm. */
    val openSuggestionCount: Int
        get() = items.count {
            it.selectedSystemId == null &&
                it.rom.detection.confidence == Confidence.PROBABLE &&
                it.rom.detection.system != null
        }
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val session: ReviewSession,
    private val folderRepository: RomFolderRepository,
    private val settingsRepository: SettingsRepository,
    private val extractionManager: ExtractionManager,
    val registry: SystemRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadFromSession()
        // Mirror the app-scoped extraction run into the screen state. The
        // run itself lives in ExtractionManager and survives navigation.
        viewModelScope.launch {
            extractionManager.state.collect { runState ->
                when (runState) {
                    ExtractionRunState.Idle ->
                        _uiState.update { it.copy(moving = false, progress = null) }

                    is ExtractionRunState.Running ->
                        _uiState.update { it.copy(moving = true, progress = runState.progress) }

                    is ExtractionRunState.Finished -> {
                        _uiState.update { s ->
                            s.copy(
                                moving = false,
                                progress = null,
                                items = s.items.filterNot { it.id in runState.processedIds },
                                moveSummary = runState.summary,
                            )
                        }
                        extractionManager.acknowledgeFinished()
                    }
                }
            }
        }
    }

    private fun loadFromSession() {
        val archiveItems = session.analyses.flatMap { analysis ->
            analysis.roms.map { rom ->
                ReviewItem(
                    id = "${analysis.archive.path}::${rom.group.primary}",
                    source = RomSource.ArchiveEntry(
                        archivePath = analysis.archive.path,
                        archiveType = analysis.archive.type,
                        archiveFileName = analysis.archive.fileName,
                    ),
                    rom = rom,
                )
            }
        }
        val looseItems = session.looseRoms.map { rom ->
            ReviewItem(
                id = "loose::${rom.memberEntryPaths.firstOrNull() ?: rom.group.primary}",
                source = RomSource.LooseFiles,
                rom = rom,
            )
        }
        val items = archiveItems + looseItems
        _uiState.value = ReviewUiState(items = items)

        // Core rule: only CERTAIN detections get their target prefilled.
        for (item in items) {
            val system = item.rom.detection.system
            if (system != null && item.rom.detection.confidence == Confidence.CERTAIN) {
                selectSystem(item.id, system.id)
            }
        }
    }

    /**
     * Full target directory for one ROM: the system folder, plus a
     * per-game subfolder for systems that need it (Dreamcast GDI dumps).
     */
    private suspend fun targetDirFor(item: ReviewItem, system: SystemDefinition): String {
        val base = folderRepository.targetPathFor(system)
        if (!system.gameSubfolder) return base
        val gameName = item.rom.group.primary.substringBeforeLast('.')
        return "$base/$gameName"
    }

    /** Applies the user's (or the CERTAIN prefill's) system choice. */
    fun selectSystem(itemId: String, systemId: String) {
        val system = registry.byId(systemId) ?: return
        viewModelScope.launch {
            val item = _uiState.value.items.find { it.id == itemId } ?: return@launch
            val path = targetDirFor(item, system)
            val exists = folderRepository.folderExists(system)
            val occupied = folderRepository.anyFileExists(path, item.rom.group.members)
            _uiState.update { state ->
                state.copy(
                    items = state.items.map { current ->
                        if (current.id == itemId) {
                            current.copy(
                                selectedSystemId = system.id,
                                targetPath = path,
                                targetExists = exists,
                                targetOccupied = occupied,
                                overwrite = false,
                            )
                        } else {
                            current
                        }
                    },
                )
            }
        }
    }

    /** Confirms every open PROBABLE suggestion in one go. */
    fun acceptAllSuggestions() {
        for (item in _uiState.value.items) {
            val system = item.rom.detection.system ?: continue
            if (item.selectedSystemId == null &&
                item.rom.detection.confidence == Confidence.PROBABLE
            ) {
                selectSystem(item.id, system.id)
            }
        }
    }

    /** User decision to replace existing duplicate files at the target. */
    fun setOverwrite(itemId: String, overwrite: Boolean) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.id == itemId) it.copy(overwrite = overwrite) else it
                },
            )
        }
    }

    /** Removes the assignment again (user changed their mind). */
    fun clearSelection(itemId: String) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            selectedSystemId = null,
                            targetPath = null,
                            targetExists = null,
                            targetOccupied = false,
                            overwrite = false,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    /** Creates all missing target folders of the assigned items. */
    fun createMissingFolders() {
        val systems: List<SystemDefinition> = _uiState.value.items
            .filter { it.selectedSystemId != null && it.targetExists == false }
            .mapNotNull { registry.byId(it.selectedSystemId!!) }
            .distinctBy { it.id }
        if (systems.isEmpty()) return

        _uiState.update { it.copy(creatingFolders = true, folderResult = null) }
        viewModelScope.launch {
            var created = 0
            var failed = 0
            for (system in systems) {
                folderRepository.ensureFolder(system)
                    .onSuccess { created++ }
                    .onFailure { failed++ }
            }
            val affected = _uiState.value.items.filter { it.selectedSystemId != null }
            for (item in affected) {
                selectSystem(item.id, item.selectedSystemId!!)
            }
            _uiState.update {
                it.copy(
                    creatingFolders = false,
                    folderResult = FolderCreationResult(created = created, failed = failed),
                )
            }
        }
    }

    /** Clears the one-shot folder feedback after the UI showed it. */
    fun consumeFolderResult() {
        _uiState.update { it.copy(folderResult = null) }
    }

    /**
     * Hands every assigned ROM over to the [ExtractionManager]: archive
     * groups are extracted, loose files are moved, duplicates are skipped
     * unless the user opted into replacing them. The manager runs in a
     * foreground service, so the run survives leaving the screen.
     */
    fun extractAssigned() {
        val state = _uiState.value
        val assigned = state.items.filter {
            it.selectedSystemId != null && (!it.targetOccupied || it.overwrite)
        }
        if (assigned.isEmpty() || state.moving) return

        _uiState.update { it.copy(moving = true, moveSummary = null) }
        viewModelScope.launch {
            val deleteArchives = settingsRepository.settings.first().deleteArchivesAfterExtract

            val tasks = assigned.mapNotNull { item ->
                val system = registry.byId(item.selectedSystemId ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                ExtractionTask(
                    id = item.id,
                    primaryName = item.rom.group.primary,
                    source = when (val source = item.source) {
                        is RomSource.ArchiveEntry ->
                            TaskSource.Archive(source.archivePath, source.archiveType)

                        is RomSource.LooseFiles -> TaskSource.Loose
                    },
                    entryPaths = item.rom.memberEntryPaths,
                    targetDir = targetDirFor(item, system),
                    replaceExisting = item.overwrite,
                    expectedBytes = item.rom.totalSizeBytes,
                )
            }

            // An archive may be deleted once ALL its review items succeeded
            val cleanups = state.items
                .filter { it.source is RomSource.ArchiveEntry }
                .groupBy { it.source as RomSource.ArchiveEntry }
                .map { (source, items) ->
                    ArchiveCleanup(
                        archivePath = source.archivePath,
                        archiveFileName = source.archiveFileName,
                        taskIds = items.map { it.id }.toSet(),
                    )
                }

            extractionManager.start(tasks, cleanups, deleteArchives)
        }
    }

    /** Cancels the running extraction (current group is rolled back). */
    fun cancelExtraction() {
        extractionManager.cancel()
    }

    /** Clears the one-shot move feedback after the UI handled it. */
    fun consumeMoveSummary() {
        _uiState.update { it.copy(moveSummary = null) }
    }
}
