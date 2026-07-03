package dev.thor.rombutler.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.LogLevel
import dev.thor.rombutler.domain.repository.LogRepository
import dev.thor.rombutler.domain.repository.RomExtractor
import dev.thor.rombutler.domain.repository.RomFolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One ROM (group) awaiting user review.
 *
 * @property id stable list key (archive path + primary file name).
 * @property archiveFileName archive the ROM lives in.
 * @property archivePath absolute path of that archive.
 * @property rom detection result from the analysis.
 * @property selectedSystemId user-confirmed target system. Prefilled ONLY
 *   for CERTAIN detections — PROBABLE suggestions must be tapped by the
 *   user, UNKNOWN requires a manual pick. The app never decides on doubt.
 * @property targetPath absolute target folder once a system is selected.
 * @property targetExists whether that folder already exists.
 */
data class ReviewItem(
    val id: String,
    val archiveFileName: String,
    val archivePath: String,
    val archiveType: ArchiveType,
    val rom: DetectedRom,
    val selectedSystemId: String? = null,
    val targetPath: String? = null,
    val targetExists: Boolean? = null,
)

/**
 * One-shot feedback after a folder-creation run (formatted by the UI).
 */
data class FolderCreationResult(val created: Int, val failed: Int)

/**
 * One-shot feedback after a move run (formatted by the UI).
 */
data class MoveSummary(val moved: Int, val failed: Int)

/**
 * Live progress of an extraction run.
 *
 * @property currentIndex 1-based index of the ROM being extracted.
 * @property totalCount number of ROMs in this run.
 * @property currentName primary file name currently being extracted.
 * @property fraction overall progress 0..1, byte-based across the whole run.
 */
data class ExtractionProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val currentName: String,
    val fraction: Float,
)

/**
 * UI state of the review screen.
 *
 * @property items all ROMs of the current scan session.
 * @property creatingFolders folder creation in progress.
 * @property folderResult one-shot feedback after creating folders.
 * @property moving extraction run in progress.
 * @property progress live progress while [moving] (null otherwise).
 * @property moveSummary one-shot feedback after extracting (UI opens log).
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
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val session: ReviewSession,
    private val folderRepository: RomFolderRepository,
    private val romExtractor: RomExtractor,
    private val logRepository: LogRepository,
    val registry: SystemRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadFromSession()
    }

    private fun loadFromSession() {
        val items = session.analyses.flatMap { analysis ->
            analysis.roms.map { rom ->
                ReviewItem(
                    id = "${analysis.archive.path}::${rom.group.primary}",
                    archiveFileName = analysis.archive.fileName,
                    archivePath = analysis.archive.path,
                    archiveType = analysis.archive.type,
                    rom = rom,
                )
            }
        }
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
            _uiState.update { state ->
                state.copy(
                    items = state.items.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                selectedSystemId = system.id,
                                targetPath = path,
                                targetExists = exists,
                            )
                        } else {
                            item
                        }
                    },
                )
            }
        }
    }

    /** Removes the assignment again (user changed their mind). */
    fun clearSelection(itemId: String) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(selectedSystemId = null, targetPath = null, targetExists = null)
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
            // Refresh existence flags for all assigned items
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
     * Extracts every assigned ROM group from its archive into the target
     * system folder — per group, so mixed archives are no problem. An
     * archive whose groups were ALL extracted successfully is deleted
     * afterwards (that is the "move" the user expects: the download folder
     * gets cleaned up). Every outcome is written to the persistent log.
     */
    fun extractAssigned() {
        val state = _uiState.value
        val assigned = state.items.filter { it.selectedSystemId != null }
        if (assigned.isEmpty() || state.moving) return

        _uiState.update { it.copy(moving = true, moveSummary = null) }
        viewModelScope.launch {
            var extracted = 0
            var failed = 0
            val extractedIds = mutableSetOf<String>()

            // Byte-based progress across the whole run, throttled so a
            // 4-GB ISO does not trigger thousands of recompositions.
            val totalBytes = assigned.sumOf { it.rom.totalSizeBytes }.coerceAtLeast(1)
            var doneBytes = 0L
            var lastShownFraction = -1f

            for ((index, item) in assigned.withIndex()) {
                val system = registry.byId(item.selectedSystemId ?: continue) ?: continue
                val targetDir = targetDirFor(item, system)

                fun publishProgress() {
                    val fraction = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                    if (fraction - lastShownFraction < 0.005f) return
                    lastShownFraction = fraction
                    _uiState.update {
                        it.copy(
                            progress = ExtractionProgress(
                                currentIndex = index + 1,
                                totalCount = assigned.size,
                                currentName = item.rom.group.primary,
                                fraction = fraction,
                            ),
                        )
                    }
                }
                lastShownFraction = -1f // always show the new ROM's name
                publishProgress()

                romExtractor.extractGroup(
                    archivePath = item.archivePath,
                    archiveType = item.archiveType,
                    entryPaths = item.rom.memberEntryPaths,
                    targetDir = targetDir,
                    onBytesWritten = { delta ->
                        doneBytes += delta
                        publishProgress()
                    },
                ).onSuccess { files ->
                    extracted++
                    extractedIds += item.id
                    logRepository.append(
                        LogLevel.SUCCESS,
                        "${item.rom.group.primary} → $targetDir (${files.size} Datei(en))",
                    )
                }.onFailure { error ->
                    failed++
                    logRepository.append(
                        LogLevel.ERROR,
                        "${item.rom.group.primary}: ${error.message ?: "Unbekannter Fehler"}",
                    )
                }
            }

            // Clean up archives whose ROMs are now all in place
            for ((path, archiveItems) in state.items.groupBy { it.archivePath }) {
                if (archiveItems.all { it.id in extractedIds }) {
                    val name = archiveItems.first().archiveFileName
                    if (romExtractor.deleteArchive(path)) {
                        logRepository.append(LogLevel.INFO, "Quellarchiv gelöscht: $name")
                    } else {
                        logRepository.append(
                            LogLevel.ERROR,
                            "Quellarchiv konnte nicht gelöscht werden: $name",
                        )
                    }
                }
            }

            _uiState.update { s ->
                s.copy(
                    moving = false,
                    progress = null,
                    items = s.items.filterNot { it.id in extractedIds },
                    moveSummary = MoveSummary(moved = extracted, failed = failed),
                )
            }
        }
    }

    /** Clears the one-shot move feedback after the UI handled it. */
    fun consumeMoveSummary() {
        _uiState.update { it.copy(moveSummary = null) }
    }
}
