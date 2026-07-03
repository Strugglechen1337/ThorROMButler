package dev.thor.rombutler.data.archive

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.DetectionEngine
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.model.RomArchive
import dev.thor.rombutler.domain.repository.ArchiveAnalyzer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ArchiveAnalyzer] built on Commons Compress / junrar.
 *
 * Reads as little as possible:
 * 1. entry list (archive directory only),
 * 2. text of small `.cue`/`.m3u` entries for exact grouping,
 * 3. a magic-byte header prefix — only for entries whose extension is
 *    ambiguous and only up to [DetectionEngine.MAX_HEADER_BYTES].
 */
@Singleton
class CommonsArchiveAnalyzer @Inject constructor(
    private val sourceFactory: ArchiveEntrySourceFactory,
    private val grouper: RomFileGrouper,
    private val engine: DetectionEngine,
    private val biosDetector: BiosDetector,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArchiveAnalyzer {

    override suspend fun analyze(archive: RomArchive): ArchiveAnalysis = withContext(ioDispatcher) {
        if (!archive.type.supported) {
            return@withContext ArchiveAnalysis.Unsupported(archive)
        }
        val file = File(archive.path)
        val source = sourceFactory.forType(archive.type)

        try {
            val entries = source.listEntries(file)

            // BIOS/firmware files are not games — keep them out of review.
            val (biosEntries, gameCandidates) = entries.partition { biosDetector.isBios(it.path) }
            val romEntries = gameCandidates.filter { engine.isRomFileName(it.path) }

            // Extensions nobody claims — surfaced when nothing is detected.
            val otherExtensions = gameCandidates
                .filterNot { engine.isRomFileName(it.path) }
                .map { it.path.substringAfterLast('.', "").lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()

            // Group per archive-internal directory: two discs in different
            // subfolders must not be merged, and equal file names in
            // different folders must not collide.
            val roms = romEntries
                .groupBy { it.path.entryDirectory() }
                .flatMap { (_, dirEntries) ->
                    val byName = dirEntries.associateBy { it.path.entryFileName() }

                    // Small text files steer grouping (exact cue/m3u references).
                    val textContents = dirEntries
                        .filter { it.path.hasAnyExtension("cue", "m3u") && it.sizeBytes <= MAX_TEXT_BYTES }
                        .mapNotNull { entry ->
                            source.readEntryPrefix(file, entry.path, entry.sizeBytes.toInt())
                                ?.let { entry.path.entryFileName() to it.decodeToString() }
                        }
                        .toMap()

                    grouper.group(byName.keys.toList(), textContents).map { group ->
                        val primaryEntry = byName.getValue(group.primary)
                        val detection = detectWithHeaderFallback(file, source, primaryEntry)
                        DetectedRom(
                            group = group,
                            detection = detection,
                            totalSizeBytes = group.members.sumOf {
                                byName[it]?.sizeBytes ?: 0L
                            },
                        )
                    }
                }
                .sortedBy { it.group.primary.lowercase() }

            ArchiveAnalysis.Success(
                archive = archive,
                roms = roms,
                ignoredBiosCount = biosEntries.size,
                otherExtensions = otherExtensions,
            )
        } catch (e: Exception) {
            ArchiveAnalysis.Failed(archive, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Detects by name first; reads the entry's header only when that could
     * actually improve the result (ambiguous extension with magic rules).
     */
    private fun detectWithHeaderFallback(
        file: File,
        source: ArchiveEntrySource,
        entry: ArchiveEntry,
    ): dev.thor.rombutler.domain.model.DetectionResult {
        val byName = engine.detect(entry.path.entryFileName())
        if (byName.confidence == Confidence.CERTAIN) return byName

        val maxBytes = minOf(
            DetectionEngine.MAX_HEADER_BYTES.toLong(),
            entry.sizeBytes.takeIf { it > 0 } ?: DetectionEngine.MAX_HEADER_BYTES.toLong(),
        ).toInt()
        val header = source.readEntryPrefix(file, entry.path, maxBytes) ?: return byName
        val byMagic = engine.detect(entry.path.entryFileName(), header)

        // Keep the better of the two (magic can only upgrade, not downgrade).
        return if (byMagic.confidence.ordinal < byName.confidence.ordinal) byMagic else byName
    }

    companion object {
        /** Upper bound for cue/m3u text entries — larger files are no sheets. */
        private const val MAX_TEXT_BYTES = 64L * 1024

        private fun String.entryFileName(): String =
            replace('\\', '/').substringAfterLast('/')

        private fun String.entryDirectory(): String =
            replace('\\', '/').substringBeforeLast('/', missingDelimiterValue = "")

        private fun String.hasAnyExtension(vararg exts: String): Boolean {
            val ext = substringAfterLast('.', "").lowercase()
            return ext in exts
        }
    }
}
