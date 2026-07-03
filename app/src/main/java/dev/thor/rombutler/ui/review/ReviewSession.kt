package dev.thor.rombutler.ui.review

import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.DetectedRom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory hand-over of scan results to the review screen.
 *
 * The analyses are too large for navigation arguments and there is no reason
 * to persist them — a fresh scan recreates them in seconds.
 */
@Singleton
class ReviewSession @Inject constructor() {

    /** Successful analyses selected for review (set by the scan screen). */
    var analyses: List<ArchiveAnalysis.Success> = emptyList()

    /** Loose ROM files found next to the archives (absolute member paths). */
    var looseRoms: List<DetectedRom> = emptyList()
}
