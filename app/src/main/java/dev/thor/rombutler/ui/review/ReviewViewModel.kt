package dev.thor.rombutler.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.detection.RomFileGroup
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.DetectionResult
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.model.AssignmentAdvisor
import dev.thor.rombutler.domain.model.AppSettings
import dev.thor.rombutler.domain.model.MatchSource
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
import kotlinx.coroutines.CancellationException
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

    /** Unknown archive contents awaiting a manual system choice. */
    data class ArchiveFallback(
        val archivePath: String,
        val archiveType: ArchiveType,
        val archiveFileName: String,
        val archiveSizeBytes: Long,
    ) : RomSource
}

internal data class ResolvedReviewTask(
    val source: TaskSource,
    val entryPaths: List<String>,
    val expectedBytes: Long,
    val keepsArchivePacked: Boolean,
)

/** Only ZIP sets explicitly assigned to Arcade/Neo Geo stay packed. */
internal fun ReviewItem.resolveTask(system: SystemDefinition): ResolvedReviewTask =
    when (val source = source) {
        is RomSource.ArchiveEntry -> ResolvedReviewTask(
            source = TaskSource.Archive(source.archivePath, source.archiveType),
            entryPaths = rom.memberEntryPaths,
            expectedBytes = rom.totalSizeBytes,
            keepsArchivePacked = false,
        )

        RomSource.LooseFiles -> ResolvedReviewTask(
            source = TaskSource.Loose,
            entryPaths = rom.memberEntryPaths,
            expectedBytes = rom.totalSizeBytes,
            keepsArchivePacked = false,
        )

        is RomSource.ArchiveFallback -> {
            val keepPacked = source.archiveType == ArchiveType.ZIP &&
                system.id in PACKED_ARCHIVE_SYSTEM_IDS
            ResolvedReviewTask(
                source = if (keepPacked) {
                    TaskSource.Loose
                } else {
                    TaskSource.Archive(source.archivePath, source.archiveType)
                },
                entryPaths = if (keepPacked) listOf(source.archivePath) else rom.memberEntryPaths,
                expectedBytes = if (keepPacked) source.archiveSizeBytes else rom.totalSizeBytes,
                keepsArchivePacked = keepPacked,
            )
        }
    }

private val PACKED_ARCHIVE_SYSTEM_IDS = setOf("arcade", "neogeo")

/** Items per published UI-state update while applying many selections. */
private const val SELECTION_CHUNK = 250

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
    /** True only when a successful run should reinforce local learning. */
    val learnSelection: Boolean = false,
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
    val retryAvailable: Boolean = false,
    val lastFailureMessage: String? = null,
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
        viewModelScope.launch { loadFromSession() }
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
                        rememberSuccessfulChoices(runState.processedIds)
                        _uiState.update { s ->
                            s.copy(
                                moving = false,
                                progress = null,
                                items = s.items.filterNot { it.id in runState.processedIds },
                                moveSummary = runState.summary,
                                retryAvailable = runState.summary.failed > 0,
                                lastFailureMessage = runState.failures.toReviewMessage(),
                            )
                        }
                        extractionManager.acknowledgeFinished()
                    }
                }
            }
        }
    }

    private suspend fun loadFromSession() {
        val settings = settingsRepository.settings.first()
        val archiveItems = session.analyses.flatMap { analysis ->
            if (analysis.roms.isEmpty()) {
                val memberNames = analysis.fallbackMembers.map {
                    it.path.replace('\\', '/').substringAfterLast('/')
                }
                listOf(
                    ReviewItem(
                        id = "whole::${analysis.archive.path}",
                        source = RomSource.ArchiveFallback(
                            archivePath = analysis.archive.path,
                            archiveType = analysis.archive.type,
                            archiveFileName = analysis.archive.fileName,
                            archiveSizeBytes = analysis.archive.sizeBytes,
                        ),
                        rom = DetectedRom(
                            group = RomFileGroup(
                                primary = analysis.archive.fileName,
                                members = memberNames,
                            ),
                            memberEntryPaths = analysis.fallbackMembers.map { it.path },
                            // Archive lies in "FBA/", "NEOGEO/", ... → suggest that system
                            // (still PROBABLE: the user confirms it).
                            detection = registry
                                .systemForFolderName(
                                    java.io.File(analysis.archive.path).parentFile?.name.orEmpty(),
                                )
                                ?.let { DetectionResult(it, Confidence.PROBABLE, MatchSource.FOLDER_HINT) }
                                ?: DetectionResult.UNKNOWN,
                            totalSizeBytes = analysis.fallbackMembers.sumOf { it.sizeBytes },
                        ),
                    ),
                )
            } else {
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
        }
        val looseItems = session.looseRoms.map { rom ->
            ReviewItem(
                id = "loose::${rom.memberEntryPaths.firstOrNull() ?: rom.group.primary}",
                source = RomSource.LooseFiles,
                rom = rom,
            )
        }
        // Items needing a decision come first (UNKNOWN > PROBABLE > CERTAIN)
        val items = (archiveItems + looseItems)
            .map { it.withLearnedSuggestion(settings) }
            .sortedByDescending { it.rom.detection.confidence.ordinal }
        _uiState.value = ReviewUiState(items = items)

        // Core rule: only CERTAIN detections get their target prefilled.
        // One batched pass — launching a coroutine plus a full list copy per
        // item froze the screen on libraries with thousands of ROMs.
        applySelections(
            items.mapNotNull { item ->
                val system = item.rom.detection.system
                if (system != null && item.rom.detection.confidence == Confidence.CERTAIN) {
                    Selection(item.id, system.id, remember = false)
                } else {
                    null
                }
            },
        )
    }

    /** One system choice for one review item. */
    private data class Selection(val itemId: String, val systemId: String, val remember: Boolean)

    /**
     * Applies many system choices at once: folder lookups are cached per
     * system and the UI state is published in chunks instead of per item.
     */
    private suspend fun applySelections(selections: List<Selection>) {
        if (selections.isEmpty()) return
        val existsCache = HashMap<String, Boolean>()
        for (chunk in selections.chunked(SELECTION_CHUNK)) {
            val updates = HashMap<String, ReviewItem>(chunk.size)
            val current = _uiState.value.items.associateBy { it.id }
            for (selection in chunk) {
                val system = registry.byId(selection.systemId) ?: continue
                val item = current[selection.itemId] ?: continue
                val updated = try {
                    val path = targetDirFor(item, system)
                    val exists = existsCache.getOrPut(system.id) { folderRepository.folderExists(system) }
                    val targetNames = if (item.resolveTask(system).keepsArchivePacked) {
                        listOf((item.source as RomSource.ArchiveFallback).archiveFileName)
                    } else {
                        item.rom.group.members
                    }
                    item.copy(
                        selectedSystemId = system.id,
                        targetPath = path,
                        targetExists = exists,
                        targetOccupied = folderRepository.anyFileExists(path, targetNames),
                        overwrite = false,
                        learnSelection = selection.remember,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // e.g. ROM base folder not configured — leave item unassigned
                    null
                }
                if (updated != null) updates[item.id] = updated
            }
            _uiState.update { state ->
                state.copy(items = state.items.map { updates[it.id] ?: it })
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
    fun selectSystem(
        itemId: String,
        systemId: String,
        rememberChoice: Boolean = false,
    ) {
        if (registry.byId(systemId) == null) return
        viewModelScope.launch {
            applySelections(listOf(Selection(itemId, systemId, rememberChoice)))
        }
    }

    /** Confirms every open PROBABLE suggestion in one go. */
    fun acceptAllSuggestions() {
        val selections = _uiState.value.items.mapNotNull { item ->
            val system = item.rom.detection.system ?: return@mapNotNull null
            if (item.selectedSystemId == null &&
                item.rom.detection.confidence == Confidence.PROBABLE
            ) {
                Selection(
                    itemId = item.id,
                    systemId = system.id,
                    remember = item.rom.detection.source == MatchSource.LEARNED_ASSIGNMENT,
                )
            } else {
                null
            }
        }
        viewModelScope.launch { applySelections(selections) }
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
                            learnSelection = false,
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
            applySelections(
                _uiState.value.items.mapNotNull { item ->
                    item.selectedSystemId?.let { Selection(item.id, it, item.learnSelection) }
                },
            )
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

        _uiState.update {
            it.copy(
                moving = true,
                moveSummary = null,
                retryAvailable = false,
                lastFailureMessage = null,
            )
        }
        viewModelScope.launch {
            val currentSettings = settingsRepository.settings.first()
            val deleteArchives = currentSettings.deleteArchivesAfterExtract
            val trashMode = currentSettings.trashInsteadOfDelete

            val tasks = assigned.mapNotNull { item ->
                val system = registry.byId(item.selectedSystemId ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                val resolved = item.resolveTask(system)
                ExtractionTask(
                    id = item.id,
                    primaryName = item.rom.group.primary,
                    source = resolved.source,
                    entryPaths = resolved.entryPaths,
                    targetDir = targetDirFor(item, system),
                    replaceExisting = item.overwrite,
                    expectedBytes = resolved.expectedBytes,
                )
            }

            // An archive may be deleted once ALL its review items succeeded
            val archiveEntryCleanups = state.items
                .filter { it.source is RomSource.ArchiveEntry }
                .groupBy { it.source as RomSource.ArchiveEntry }
                .map { (source, items) ->
                    ArchiveCleanup(
                        archivePath = source.archivePath,
                        archiveFileName = source.archiveFileName,
                        taskIds = items.map { it.id }.toSet(),
                    )
                }
            val fallbackCleanups = assigned.mapNotNull { item ->
                val source = item.source as? RomSource.ArchiveFallback ?: return@mapNotNull null
                val system = registry.byId(item.selectedSystemId ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                if (item.resolveTask(system).keepsArchivePacked) return@mapNotNull null
                ArchiveCleanup(
                    archivePath = source.archivePath,
                    archiveFileName = source.archiveFileName,
                    taskIds = setOf(item.id),
                )
            }

            extractionManager.start(
                tasks = tasks,
                archiveCleanups = archiveEntryCleanups + fallbackCleanups,
                deleteArchives = deleteArchives,
                trashInsteadOfDelete = trashMode,
                writeM3uPlaylists = currentSettings.writeM3uPlaylists,
                renameToDatName = currentSettings.renameToDatName,
            )
        }
    }

    /** Learns only choices whose file operation actually completed successfully. */
    private suspend fun rememberSuccessfulChoices(processedIds: Set<String>) {
        // Aggregate first: thousands of ROMs share a handful of (extension,
        // system) pairs, so one batched write replaces one disk write per ROM.
        val counts = HashMap<Pair<String, String>, Int>()
        for (item in _uiState.value.items) {
            if (item.id !in processedIds || !item.learnSelection) continue
            val extension = item.learningExtension() ?: continue
            val systemId = item.selectedSystemId ?: continue
            counts.merge(extension to systemId, 1, Int::plus)
        }
        if (counts.isEmpty()) return
        try {
            settingsRepository.rememberAssignments(counts)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Learning is optional and must never block a completed sort run.
        }
    }

    private fun ReviewItem.withLearnedSuggestion(settings: AppSettings): ReviewItem {
        if (!settings.assignmentLearningEnabled ||
            rom.detection.confidence != Confidence.UNKNOWN
        ) {
            return this
        }
        val extension = learningExtension() ?: return this
        val learned = AssignmentAdvisor.suggestion(extension, settings.learnedAssignments)
            ?: return this
        val system = registry.byId(learned.systemId) ?: return this
        return copy(
            rom = rom.copy(
                detection = DetectionResult(
                    system = system,
                    confidence = Confidence.PROBABLE,
                    source = MatchSource.LEARNED_ASSIGNMENT,
                ),
            ),
        )
    }

    private fun ReviewItem.learningExtension(): String? {
        if (source !is RomSource.ArchiveFallback) {
            return AssignmentAdvisor.extensionOf(rom.group.primary)
        }
        return rom.group.members
            .mapNotNull(AssignmentAdvisor::extensionOf)
            .distinct()
            .singleOrNull()
    }

    /** Cancels the running extraction (current group is rolled back). */
    fun cancelExtraction() {
        extractionManager.cancel()
    }

    /** Clears the one-shot move feedback after the UI handled it. */
    fun consumeMoveSummary() {
        _uiState.update { it.copy(moveSummary = null, lastFailureMessage = null) }
    }

    private fun List<dev.thor.rombutler.extraction.ExtractionFailure>.toReviewMessage(): String? =
        when (size) {
            0 -> null
            1 -> "${first().taskName}: ${first().message}"
            else -> null
        }
}
