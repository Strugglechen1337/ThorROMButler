package dev.thor.rombutler.domain.model

/**
 * How a detection result was derived (shown in the review UI / log).
 */
enum class MatchSource {
    /** Unique or characteristic file extension. */
    EXTENSION,

    /** Verified magic bytes in the file header. */
    MAGIC_BYTES,

    /** Folder name hinted at the system (e.g. an "SNES/" dir in the archive). */
    FOLDER_HINT,

    /** Local history from successful user-confirmed assignments. */
    LEARNED_ASSIGNMENT,

    /** Nothing matched. */
    NONE,
}

/**
 * Result of running the detection engine on one ROM file (group).
 *
 * @property system detected target system; `null` when [confidence] is
 *   [Confidence.UNKNOWN].
 * @property confidence how sure the engine is. Only [Confidence.CERTAIN]
 *   may drive an automatic folder suggestion.
 * @property source what the decision is based on.
 */
data class DetectionResult(
    val system: SystemDefinition?,
    val confidence: Confidence,
    val source: MatchSource,
) {
    companion object {
        /** Shared "no idea" result. */
        val UNKNOWN = DetectionResult(
            system = null,
            confidence = Confidence.UNKNOWN,
            source = MatchSource.NONE,
        )
    }
}
