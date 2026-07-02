package dev.thor.rombutler.domain.detection

import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectionResult
import dev.thor.rombutler.domain.model.MatchSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure, side-effect-free detection logic. Input: a ROM file name plus
 * (optionally) its first bytes; output: system + confidence.
 *
 * Decision order:
 * 1. Collect all systems claiming the file's extension.
 * 2. If header bytes are available, try every candidate's magic rules —
 *    a match wins with the rule's confidence (usually CERTAIN).
 * 3. Without a magic match: a single candidate falls back to its
 *    per-extension confidence; several candidates stay UNKNOWN.
 *
 * The engine itself never rounds up: ambiguity is reported honestly so the
 * UI can put the decision where it belongs — with the user.
 */
@Singleton
class DetectionEngine @Inject constructor(
    private val registry: SystemRegistry,
) {

    /**
     * Detects the system for one ROM file.
     *
     * @param fileName plain file name (path parts are ignored).
     * @param header optional first bytes of the (decompressed) file for
     *   magic-byte resolution; [MAX_HEADER_BYTES] bytes are enough.
     */
    fun detect(fileName: String, header: ByteArray? = null): DetectionResult {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension.isEmpty()) return DetectionResult.UNKNOWN

        val candidates = registry.systemsForExtension(extension)
        if (candidates.isEmpty()) return DetectionResult.UNKNOWN

        // Magic bytes beat extension conventions. When several rules match
        // (e.g. a PSP disc whose descriptor area also contains "PLAYSTATION"),
        // the match with the strongest confidence wins — not registry order.
        if (header != null) {
            val magicMatches = candidates.mapNotNull { candidate ->
                candidate.magicRules.firstOrNull { it.matches(header) }
                    ?.let { rule -> candidate to rule }
            }
            val best = magicMatches.minByOrNull { (_, rule) -> rule.confidence.ordinal }
            if (best != null) {
                return DetectionResult(
                    system = best.first,
                    confidence = best.second.confidence,
                    source = MatchSource.MAGIC_BYTES,
                )
            }
        }

        // Extension-only fallback: only meaningful with a single claimant.
        val single = candidates.singleOrNull() ?: return DetectionResult.UNKNOWN
        val confidence = single.extensions.getValue(extension)
        return if (confidence == Confidence.UNKNOWN) {
            DetectionResult.UNKNOWN
        } else {
            DetectionResult(
                system = single,
                confidence = confidence,
                source = MatchSource.EXTENSION,
            )
        }
    }

    /** True when [fileName] has an extension any system claims. */
    fun isRomFileName(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in registry.allRomExtensions
    }

    companion object {
        /**
         * Header bytes needed for all registered magic rules. The largest
         * requirement is the ISO9660 volume descriptor area (up to 0x9000).
         */
        const val MAX_HEADER_BYTES = 0x9000
    }
}
