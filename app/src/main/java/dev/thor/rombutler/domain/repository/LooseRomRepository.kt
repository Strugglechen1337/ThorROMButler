package dev.thor.rombutler.domain.repository

import dev.thor.rombutler.domain.model.DetectedRom

/**
 * Finds ROM files lying loose (unarchived) in the download folder and runs
 * the detection engine on them.
 */
interface LooseRomRepository {

    /**
     * Scans the download folder for loose ROM files, groups multi-file
     * ROMs (bin+cue, m3u) and detects their systems. BIOS files are
     * skipped. `memberEntryPaths` of the results contain ABSOLUTE paths.
     */
    suspend fun scanAndDetect(): List<DetectedRom>
}
