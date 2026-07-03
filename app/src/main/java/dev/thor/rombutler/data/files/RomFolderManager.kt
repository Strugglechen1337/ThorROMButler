package dev.thor.rombutler.data.files

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.repository.RomFolderRepository
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RomFolderRepository] on plain [java.io.File]: target folders are
 * `<romBasePath>/<esdeFolder>` per ES-DE convention.
 */
@Singleton
class RomFolderManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RomFolderRepository {

    private suspend fun baseDir(): File {
        val base = settingsRepository.settings.first().romBasePath
        check(!base.isNullOrBlank()) { "ROM-Basisordner ist nicht konfiguriert" }
        return File(base)
    }

    /** Effective folder name: user override beats the ES-DE default. */
    private suspend fun folderNameFor(system: SystemDefinition): String =
        settingsRepository.settings.first().folderOverrides[system.id] ?: system.esdeFolder

    override suspend fun targetPathFor(system: SystemDefinition): String =
        withContext(ioDispatcher) {
            File(baseDir(), folderNameFor(system)).absolutePath
        }

    override suspend fun folderExists(system: SystemDefinition): Boolean =
        withContext(ioDispatcher) {
            File(baseDir(), folderNameFor(system)).isDirectory
        }

    override suspend fun ensureFolder(system: SystemDefinition): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val folder = File(baseDir(), folderNameFor(system))
                if (!folder.isDirectory && !folder.mkdirs()) {
                    throw IOException("Ordner konnte nicht angelegt werden: ${folder.absolutePath}")
                }
                folder.absolutePath
            }
        }

    override suspend fun anyFileExists(dirPath: String, fileNames: List<String>): Boolean =
        withContext(ioDispatcher) {
            val dir = File(dirPath)
            dir.isDirectory && fileNames.any { File(dir, it).exists() }
        }
}
