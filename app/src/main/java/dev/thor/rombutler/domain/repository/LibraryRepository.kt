package dev.thor.rombutler.domain.repository

import dev.thor.rombutler.domain.model.DetectedRom

/** Per-system statistics of the ROM library. */
data class SystemStat(
    val systemId: String,
    val displayName: String,
    val romCount: Int,
    val totalBytes: Long,
)

/**
 * Same game present multiple times in one system folder (1G1R view):
 * different regions/revisions of one title, per No-Intro naming.
 *
 * @property title normalized game title (tags stripped).
 * @property systemName display name of the system.
 * @property variants the actual file names, e.g. "(Europe)" and "(USA)".
 */
data class DuplicateGroup(
    val title: String,
    val systemName: String,
    val variants: List<String>,
)

/** Files with identical size and SHA-256 content hash. */
data class ExactDuplicateGroup(
    val sha256: String,
    val sizeBytes: Long,
    val files: List<String>,
)

/** Result of the optional, potentially long-running exact duplicate scan. */
data class ExactDuplicateReport(
    val candidateFiles: Int,
    val duplicateFiles: Int,
    val reclaimableBytes: Long,
    val groups: List<ExactDuplicateGroup>,
)

/** Text descriptor whose referenced game files are checked by the doctor. */
enum class LibraryReferenceType { M3U, CUE }

/** Why a playlist or cue sheet needs attention. */
enum class LibraryReferenceProblem { EMPTY, MISSING_FILES, UNREADABLE }

data class LibraryReferenceIssue(
    val filePath: String,
    val type: LibraryReferenceType,
    val problem: LibraryReferenceProblem,
    val missingReferences: List<String> = emptyList(),
)

/** Packed ROM set whose ZIP directory cannot be used safely. */
enum class LibraryArchiveProblem { EMPTY, UNREADABLE }

data class LibraryArchiveIssue(
    val filePath: String,
    val problem: LibraryArchiveProblem,
)

enum class LibraryBiosState { NOT_CONFIGURED, FOLDER_MISSING, NONE_DETECTED, READY }

data class LibraryBiosHealth(
    val state: LibraryBiosState,
    val knownFileCount: Int = 0,
)

enum class LibraryDatState {
    NOT_CONFIGURED,
    FOLDER_MISSING,
    NO_DAT_FILES,
    NO_USABLE_ENTRIES,
    READY,
}

data class LibraryDatHealth(
    val state: LibraryDatState,
    val datFileCount: Int = 0,
)

enum class LibraryBackupState {
    NOT_CONFIGURED,
    FOLDER_MISSING,
    NO_MANIFEST,
    DIFFERENT_LIBRARY,
    OUTDATED,
    CURRENT,
}

data class LibraryBackupHealth(
    val state: LibraryBackupState,
    val lastBackupMillis: Long? = null,
)

/**
 * Result of a library check.
 *
 * @property stats per-system counts/sizes, biggest first.
 * @property misplaced ROMs whose CERTAIN detection contradicts the folder
 *   they live in (e.g. a `.gba` inside `roms/psx`) — offered for re-sorting.
 * @property duplicates same-title variants within one system (informational).
 */
data class LibraryReport(
    val totalRoms: Int,
    val totalBytes: Long,
    val stats: List<SystemStat>,
    val misplaced: List<DetectedRom>,
    val duplicates: List<DuplicateGroup> = emptyList(),
    val referenceIssues: List<LibraryReferenceIssue> = emptyList(),
    val archiveIssues: List<LibraryArchiveIssue> = emptyList(),
    val biosHealth: LibraryBiosHealth = LibraryBiosHealth(LibraryBiosState.NOT_CONFIGURED),
    val datHealth: LibraryDatHealth = LibraryDatHealth(LibraryDatState.NOT_CONFIGURED),
    val backupHealth: LibraryBackupHealth =
        LibraryBackupHealth(LibraryBackupState.NOT_CONFIGURED),
) {
    /** Findings that point to an actual broken or misplaced library item. */
    val problemCount: Int
        get() = misplaced.size + referenceIssues.size + archiveIssues.size
}

/**
 * Inspects the existing ROM library below the ROM base folder.
 */
interface LibraryRepository {

    /** Walks the library, collects statistics and finds misplaced ROMs. */
    suspend fun check(): LibraryReport

    /** Hashes only same-sized candidates and reports byte-identical files. */
    suspend fun findExactDuplicates(): ExactDuplicateReport
}
