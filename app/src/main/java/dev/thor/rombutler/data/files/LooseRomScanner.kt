package dev.thor.rombutler.data.files

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.DetectionEngine
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.repository.LooseRomRepository
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LooseRomRepository] on plain [java.io.File] — the unarchived counterpart
 * of the archive analyzer: same grouping, same detection, same BIOS filter.
 */
@Singleton
class LooseRomScanner @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val engine: DetectionEngine,
    private val grouper: RomFileGrouper,
    private val biosDetector: BiosDetector,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LooseRomRepository {

    override suspend fun scanAndDetect(): List<DetectedRom> = withContext(ioDispatcher) {
        val settings = settingsRepository.settings.first()
        val roots = (listOfNotNull(settings.downloadPath) + settings.additionalSourcePaths)
            .distinct()
            .map(::File)
            .filter { it.isDirectory }
        if (roots.isEmpty()) return@withContext emptyList()

        val romFiles = mutableListOf<File>()
        for (root in roots) {
            collectRomFiles(root, depth = 0, into = romFiles)
        }

        // Group per directory, like inside archives
        romFiles.groupBy { it.parentFile?.absolutePath.orEmpty() }
            .flatMap { (_, dirFiles) ->
                val byName = dirFiles.associateBy { it.name }
                val textContents = dirFiles
                    .filter { it.extension.lowercase() in TEXT_EXTENSIONS && it.length() <= MAX_TEXT_BYTES }
                    .associate { it.name to it.readText() }

                grouper.group(byName.keys.toList(), textContents).map { group ->
                    val primary = byName.getValue(group.primary)
                    DetectedRom(
                        group = group,
                        memberEntryPaths = group.members.mapNotNull { byName[it]?.absolutePath },
                        detection = detectWithHeader(primary),
                        totalSizeBytes = group.members.sumOf { byName[it]?.length() ?: 0L },
                    )
                }
            }
            .sortedBy { it.group.primary.lowercase() }
    }

    private fun collectRomFiles(dir: File, depth: Int, into: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            when {
                child.isDirectory -> {
                    if (depth < MAX_DEPTH && !child.name.startsWith(".")) {
                        collectRomFiles(child, depth + 1, into)
                    }
                }

                child.isFile &&
                    engine.isRomFileName(child.name) &&
                    !biosDetector.isBios(child.name) -> into += child
            }
        }
    }

    /** Extension detection first; header read only when it could help. */
    private fun detectWithHeader(file: File): dev.thor.rombutler.domain.model.DetectionResult {
        val folderHint = file.parentFile?.name
        val byName = engine.detect(file.name, folderHint = folderHint)
        if (byName.confidence == Confidence.CERTAIN) return byName

        val maxBytes = minOf(DetectionEngine.MAX_HEADER_BYTES.toLong(), file.length()).toInt()
        val header = try {
            ByteArray(maxBytes).also { buffer ->
                file.inputStream().use { it.read(buffer) }
            }
        } catch (_: java.io.IOException) {
            return byName
        }
        val byMagic = engine.detect(file.name, header, folderHint)
        return if (byMagic.confidence.ordinal < byName.confidence.ordinal) byMagic else byName
    }

    companion object {
        private const val MAX_DEPTH = 3
        private const val MAX_TEXT_BYTES = 64L * 1024
        private val TEXT_EXTENSIONS = setOf("cue", "m3u")
    }
}
