package dev.thor.rombutler.data.files

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.DetectionEngine
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.library.VariantAdvisor
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.domain.repository.LibraryReport
import dev.thor.rombutler.domain.repository.LibraryRepository
import dev.thor.rombutler.domain.repository.ExactDuplicateReport
import dev.thor.rombutler.domain.repository.LibraryArchiveIssue
import dev.thor.rombutler.domain.repository.LibraryReferenceIssue
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.domain.repository.SystemStat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LibraryRepository] on plain [java.io.File]: walks every known system
 * folder below the ROM base, counts ROMs per system and flags files whose
 * CERTAIN detection contradicts the folder they live in.
 *
 * Deliberately conservative: only CERTAIN detections are reported as
 * misplaced (a lone `.bin` in `roms/psx` is fine), and folder hints are
 * NOT used here — the current folder is exactly what is being questioned.
 */
@Singleton
class LibraryChecker @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val registry: SystemRegistry,
    private val engine: DetectionEngine,
    private val grouper: RomFileGrouper,
    private val biosDetector: BiosDetector,
    private val exactDuplicateFinder: ExactDuplicateFinder,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {

    override suspend fun check(): LibraryReport = withContext(ioDispatcher) {
        val settings = settingsRepository.settings.first()
        val basePath = settings.romBasePath
            ?: throw IllegalStateException("ROM-Basisordner ist nicht konfiguriert")
        val base = File(basePath)
        if (!base.isDirectory) throw IllegalStateException("ROM-Basisordner nicht gefunden: $basePath")

        // Effective folder name -> system (overrides win)
        val folderToSystem = registry.systems.associateBy { system ->
            (settings.folderOverrides[system.id] ?: system.esdeFolder).lowercase()
        }

        val stats = mutableListOf<SystemStat>()
        val misplaced = mutableListOf<DetectedRom>()
        val duplicates = mutableListOf<dev.thor.rombutler.domain.repository.DuplicateGroup>()
        val referenceIssues = mutableListOf<LibraryReferenceIssue>()
        val archiveIssues = mutableListOf<LibraryArchiveIssue>()
        var newestLibraryFileMillis = 0L

        for (dir in base.listFiles()?.filter { it.isDirectory }.orEmpty()) {
            val system = folderToSystem[dir.name.lowercase()] ?: continue
            val libraryFiles = mutableListOf<File>()
            collectLibraryFiles(dir, depth = 0, into = libraryFiles)
            newestLibraryFileMillis = maxOf(
                newestLibraryFileMillis,
                libraryFiles.maxOfOrNull { it.lastModified() } ?: 0L,
            )
            val romFiles = libraryFiles.filter {
                (engine.isRomFileName(it.name) ||
                    (system.id in PACKED_ZIP_SYSTEMS && it.extension.equals("zip", true))) &&
                    !biosDetector.isBios(it.name)
            }
            referenceIssues += LibraryHealthInspector.inspectReferences(libraryFiles)
            if (system.id in PACKED_ZIP_SYSTEMS) {
                archiveIssues += LibraryHealthInspector.inspectPackedArchives(romFiles)
            }

            stats += SystemStat(
                systemId = system.id,
                displayName = system.displayName,
                romCount = romFiles.size,
                totalBytes = romFiles.sumOf { it.length() },
            )

            // 1G1R view: same normalized title more than once in one system
            romFiles
                .groupBy { normalizeTitle(it.name) }
                .filterValues { it.size > 1 && it.first().extension.lowercase() != "bin" }
                .forEach { (title, files) ->
                    val variants = files.map { it.name }.sorted()
                    val locale = Locale.getDefault()
                    duplicates += dev.thor.rombutler.domain.repository.DuplicateGroup(
                        title = title,
                        systemName = system.displayName,
                        variants = variants,
                        recommendation = VariantAdvisor.recommend(
                            variants = variants,
                            localeLanguage = locale.language,
                            localeCountry = locale.country,
                        ),
                    )
                }

            // Group per directory (bin+cue stays together), then question
            // only groups with a CERTAIN detection that disagrees.
            romFiles.groupBy { it.parentFile?.absolutePath.orEmpty() }
                .forEach { (_, dirFiles) ->
                    val byName = dirFiles.associateBy { it.name }
                    for (group in grouper.group(byName.keys.toList())) {
                        val primary = byName.getValue(group.primary)
                        val detection = detect(primary)
                        if (detection.confidence == Confidence.CERTAIN &&
                            detection.system != null &&
                            detection.system.id != system.id
                        ) {
                            misplaced += DetectedRom(
                                group = group,
                                memberEntryPaths = group.members.mapNotNull { byName[it]?.absolutePath },
                                detection = detection,
                                totalSizeBytes = group.members.sumOf { byName[it]?.length() ?: 0L },
                            )
                        }
                    }
                }
        }

        LibraryReport(
            totalRoms = stats.sumOf { it.romCount },
            totalBytes = stats.sumOf { it.totalBytes },
            stats = stats.sortedByDescending { it.totalBytes },
            misplaced = misplaced.sortedBy { it.group.primary.lowercase() },
            duplicates = duplicates.sortedBy { it.title },
            referenceIssues = referenceIssues,
            archiveIssues = archiveIssues,
            biosHealth = LibraryHealthInspector.biosHealth(settings.biosFolderPath, biosDetector),
            datHealth = LibraryHealthInspector.datHealth(settings.datFolderPath),
            backupHealth = LibraryHealthInspector.backupHealth(
                romBase = base,
                backupPath = settings.backupTargetPath,
                newestLibraryFileMillis = newestLibraryFileMillis,
            ),
        )
    }

    override suspend fun findExactDuplicates(): ExactDuplicateReport = withContext(ioDispatcher) {
        val settings = settingsRepository.settings.first()
        val basePath = settings.romBasePath
            ?: throw IllegalStateException("ROM-Basisordner ist nicht konfiguriert")
        val base = File(basePath)
        if (!base.isDirectory) throw IllegalStateException("ROM-Basisordner nicht gefunden: $basePath")

        val knownFolders = registry.systems.associateBy { system ->
            (settings.folderOverrides[system.id] ?: system.esdeFolder).lowercase()
        }
        val files = mutableListOf<File>()
        base.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.lowercase() in knownFolders.keys }
            .forEach { dir ->
                val system = knownFolders.getValue(dir.name.lowercase())
                val libraryFiles = mutableListOf<File>()
                collectLibraryFiles(dir, depth = 0, into = libraryFiles)
                files += libraryFiles.filter {
                    (engine.isRomFileName(it.name) ||
                        (system.id in PACKED_ZIP_SYSTEMS && it.extension.equals("zip", true))) &&
                        !biosDetector.isBios(it.name)
                }
            }
        exactDuplicateFinder.find(base, files)
    }

    private fun collectLibraryFiles(dir: File, depth: Int, into: MutableList<File>) {
        for (child in dir.listFiles().orEmpty()) {
            when {
                child.isDirectory && depth < MAX_DEPTH && !child.name.startsWith(".") ->
                    collectLibraryFiles(child, depth + 1, into)

                child.isFile && !child.name.startsWith(".") -> into += child
            }
        }
    }

    /** Extension first, header only when needed — NO folder hint here. */
    private fun detect(file: File): dev.thor.rombutler.domain.model.DetectionResult {
        val byName = engine.detect(file.name)
        if (byName.confidence == Confidence.CERTAIN) return byName
        val maxBytes = minOf(DetectionEngine.MAX_HEADER_BYTES.toLong(), file.length()).toInt()
        val header = try {
            ByteArray(maxBytes).also { buffer -> file.inputStream().use { it.read(buffer) } }
        } catch (_: java.io.IOException) {
            return byName
        }
        val byMagic = engine.detect(file.name, header)
        return if (byMagic.confidence.ordinal < byName.confidence.ordinal) byMagic else byName
    }

    companion object {
        private const val MAX_DEPTH = 2 // system folder + per-game subfolders
        private val PACKED_ZIP_SYSTEMS = setOf("arcade", "neogeo")

        private val TAG_GROUPS = Regex("""[(\[][^)\]]*[)\]]""")
        private val MULTI_SPACE = Regex("""\s+""")

        /**
         * Normalized game title per No-Intro naming: extension and all
         * `(...)`/`[...]` tag groups stripped, whitespace collapsed,
         * lowercase — "Game (Europe) (Rev 1).gba" == "Game (USA).gba".
         */
        fun normalizeTitle(fileName: String): String = fileName
            .substringBeforeLast('.')
            .replace(TAG_GROUPS, " ")
            .replace(MULTI_SPACE, " ")
            .trim()
            .lowercase()
    }
}
