package dev.thor.rombutler.data.files

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.detection.BiosDetector
import dev.thor.rombutler.domain.detection.DetectionEngine
import dev.thor.rombutler.domain.detection.RomFileGrouper
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.model.AppSettings
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

/**
 * Regression guard for very large collections (several releases fixed UI
 * freezes with thousands of ROMs). The time limits are deliberately generous
 * so slow CI machines do not flake; they only catch order-of-magnitude
 * regressions such as accidental quadratic behavior.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LargeLibraryPerformanceTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val folders = listOf(
        "nes" to "nes", "snes" to "sfc", "gba" to "gba", "gb" to "gb", "gbc" to "gbc",
    )

    /** Settings-only repository: every other call is a test bug. */
    private fun repositoryOf(settings: AppSettings): SettingsRepository =
        Proxy.newProxyInstance(
            SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java),
        ) { _, method, _ ->
            if (method.name == "getSettings") flowOf(settings) else error("unexpected ${method.name}")
        } as SettingsRepository

    private fun createLibrary(romsPerSystem: Int): File {
        val base = tempDir.newFolder("roms")
        for ((systemIndex, entry) in folders.withIndex()) {
            val (folder, extension) = entry
            val dir = File(base, folder).apply { mkdirs() }
            repeat(romsPerSystem) { index ->
                // Unique 4-byte content per file, identical size across the library
                val unique = systemIndex * romsPerSystem + index
                File(dir, "Game $index (Europe).$extension").writeBytes(
                    byteArrayOf(
                        (unique shr 24).toByte(), (unique shr 16).toByte(),
                        (unique shr 8).toByte(), unique.toByte(),
                    ),
                )
            }
        }
        return base
    }

    private fun checker(base: File, testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler): LibraryChecker {
        val registry = SystemRegistry()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return LibraryChecker(
            settingsRepository = repositoryOf(AppSettings(romBasePath = base.absolutePath)),
            registry = registry,
            engine = DetectionEngine(registry),
            grouper = RomFileGrouper(),
            biosDetector = BiosDetector(),
            exactDuplicateFinder = ExactDuplicateFinder(dispatcher),
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `library check handles ten thousand ROMs quickly`() = runTest {
        val base = createLibrary(romsPerSystem = 2_000)
        val checker = checker(base, testScheduler)

        lateinit var report: dev.thor.rombutler.domain.repository.LibraryReport
        val millis = measureTimeMillis { report = checker.check() }

        assertThat(report.totalRoms).isEqualTo(10_000)
        assertThat(report.stats).hasSize(5)
        assertThat(millis).isLessThan(60_000L)
    }

    @Test
    fun `exact duplicate scan hashes ten thousand same-sized files quickly`() = runTest {
        val base = createLibrary(romsPerSystem = 2_000)
        // One real duplicate pair among the 10,000 same-sized files
        File(base, "nes/Game 5 (Europe).nes").copyTo(File(base, "nes/Game 5 copy.nes"))
        val checker = checker(base, testScheduler)

        lateinit var report: dev.thor.rombutler.domain.repository.ExactDuplicateReport
        val millis = measureTimeMillis { report = checker.findExactDuplicates() }

        assertThat(report.groups).hasSize(1)
        assertThat(report.groups.single().files)
            .containsExactly("nes/Game 5 (Europe).nes", "nes/Game 5 copy.nes")
        assertThat(millis).isLessThan(90_000L)
    }
}
