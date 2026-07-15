package dev.thor.rombutler.data.archive

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.DetectionEngine
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.ArchiveMember
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

            // Name-based detection first; collect everything that needs
            // bytes from the archive (cue/m3u texts + magic-byte headers).
            val nameDetections = romEntries.associate { entry ->
                val hint = entry.path.entryDirectory().substringAfterLast('/')
                    .takeIf { it.isNotEmpty() }
                entry.path to engine.detect(entry.path.entryFileName(), folderHint = hint)
            }
            val prefixRequests = buildMap {
                for (entry in romEntries) {
                    when {
                        entry.path.hasAnyExtension("cue", "m3u") &&
                            entry.sizeBytes in 1..MAX_TEXT_BYTES ->
                            put(entry.path, entry.sizeBytes.toInt())

                        nameDetections.getValue(entry.path).confidence != Confidence.CERTAIN ->
                            put(
                                entry.path,
                                minOf(
                                    DetectionEngine.MAX_HEADER_BYTES.toLong(),
                                    entry.sizeBytes.takeIf { it > 0 }
                                        ?: DetectionEngine.MAX_HEADER_BYTES.toLong(),
                                ).toInt(),
                            )
                    }
                }
            }
            // ONE pass over the archive — solid 7z blocks are decompressed
            // exactly once instead of once per ambiguous entry.
            val prefixes = source.readEntryPrefixes(file, prefixRequests)

            // Group per archive-internal directory: two discs in different
            // subfolders must not be merged, and equal file names in
            // different folders must not collide.
            val roms = romEntries
                .groupBy { it.path.entryDirectory() }
                .flatMap { (_, dirEntries) ->
                    val byName = dirEntries.associateBy { it.path.entryFileName() }

                    // Small text files steer grouping (exact cue/m3u references).
                    val textContents = dirEntries
                        .filter { it.path.hasAnyExtension("cue", "m3u") }
                        .mapNotNull { entry ->
                            prefixes[entry.path]
                                ?.let { entry.path.entryFileName() to it.decodeToString() }
                        }
                        .toMap()

                    grouper.group(byName.keys.toList(), textContents).map { group ->
                        val primaryEntry = byName.getValue(group.primary)
                        val detection = resolveDetection(
                            byName = nameDetections.getValue(primaryEntry.path),
                            entry = primaryEntry,
                            prefixes = prefixes,
                        )
                        DetectedRom(
                            group = group,
                            memberEntryPaths = group.members.mapNotNull { byName[it]?.path },
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
                fallbackMembers = if (roms.isEmpty()) {
                    gameCandidates.map { ArchiveMember(it.path, it.sizeBytes) }
                } else {
                    emptyList()
                },
            )
        } catch (e: Throwable) {
            // Throwable, not Exception: a large/solid 7z can exhaust memory
            // while a header prefix is decompressed. That is an *Error*, not
            // an Exception — letting it escape crashed the whole scan on
            // big folders. Report the archive as failed instead.
            ArchiveAnalysis.Failed(archive, e.toUserMessage())
        }
    }

    /** Maps library throwables to actionable German messages. */
    private fun Throwable.toUserMessage(): String {
        val name = javaClass.simpleName
        return when {
            this is OutOfMemoryError -> "Zu wenig Arbeitsspeicher für die Analyse – " +
                "Archiv nutzt vermutlich sehr große 7z/LZMA2-Kompression. Am PC mit " +
                "kleinerem Dictionary (z. B. 32 MB) neu packen."

            this is org.apache.commons.compress.MemoryLimitException ->
                "7z-Archiv braucht zu viel Arbeitsspeicher (${memoryNeededInKb / 1024} MB). " +
                    "Am PC mit kleinerem Dictionary neu packen."

            this is org.apache.commons.compress.PasswordRequiredException ||
                "Encrypted" in name || "Password" in name ->
                "Archiv ist passwortgeschützt"

            else -> message ?: name
        }
    }

    /**
     * Detects by name first; reads the entry's header only when that could
     * actually improve the result (ambiguous extension with magic rules).
     */
    /** Upgrades a name-based detection with a pre-fetched header prefix. */
    private fun resolveDetection(
        byName: dev.thor.rombutler.domain.model.DetectionResult,
        entry: ArchiveEntry,
        prefixes: Map<String, ByteArray>,
    ): dev.thor.rombutler.domain.model.DetectionResult {
        if (byName.confidence == Confidence.CERTAIN) return byName
        val header = prefixes[entry.path] ?: return byName
        val folderHint = entry.path.entryDirectory().substringAfterLast('/')
            .takeIf { it.isNotEmpty() }
        val byMagic = engine.detect(entry.path.entryFileName(), header, folderHint)

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
