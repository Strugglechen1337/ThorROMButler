package dev.thor.rombutler.domain.model

import dev.thor.rombutler.domain.detection.RomFileGroup

/**
 * One detected ROM (single file or multi-file group) inside an archive.
 *
 * @property group the files that belong together (bin+cue, m3u, ...).
 * @property detection system assignment with confidence.
 * @property totalSizeBytes uncompressed size of all group members.
 */
data class DetectedRom(
    val group: RomFileGroup,
    val detection: DetectionResult,
    val totalSizeBytes: Long,
)

/**
 * Outcome of analyzing one archive WITHOUT extracting it.
 */
sealed interface ArchiveAnalysis {

    val archive: RomArchive

    /**
     * Archive was readable; [roms] lists all detected ROM groups.
     *
     * @property ignoredBiosCount BIOS/firmware files that were deliberately
     *   skipped (they are not games and must not reach the review list).
     * @property otherExtensions distinct extensions of remaining entries no
     *   system claims — shown when [roms] is empty so users see WHY nothing
     *   was detected (e.g. an Amiga archive on a v0.1 system list).
     */
    data class Success(
        override val archive: RomArchive,
        val roms: List<DetectedRom>,
        val ignoredBiosCount: Int = 0,
        val otherExtensions: List<String> = emptyList(),
    ) : ArchiveAnalysis

    /** Container format is recognized but not readable (RAR5). */
    data class Unsupported(
        override val archive: RomArchive,
    ) : ArchiveAnalysis

    /** Reading failed (corrupt, encrypted, I/O error). */
    data class Failed(
        override val archive: RomArchive,
        val message: String,
    ) : ArchiveAnalysis
}
