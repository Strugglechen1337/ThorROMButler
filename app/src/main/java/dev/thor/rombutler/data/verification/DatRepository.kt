package dev.thor.rombutler.data.verification

import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.domain.verification.DatIndex
import dev.thor.rombutler.domain.verification.VerificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches the merged [DatIndex] from all `*.dat` files in the
 * user-configured DAT folder. Reloads when the folder path or the file
 * set (names + timestamps) changed.
 */
@Singleton
class DatRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VerificationRepository {
    private val mutex = Mutex()
    private var cachedIndex: DatIndex = DatIndex.EMPTY
    private var cacheKey: String = ""

    /** Current merged index; [DatIndex.EMPTY] when no folder/DATs exist. */
    override suspend fun index(): DatIndex = withContext(ioDispatcher) {
        mutex.withLock {
            val folder = settingsRepository.settings.first().datFolderPath
                ?.let(::File)?.takeIf { it.isDirectory }
                ?: return@withLock DatIndex.EMPTY.also { cachedIndex = it; cacheKey = "" }

            val datFiles = folder.listFiles { f: File ->
                f.isFile && f.extension.equals("dat", ignoreCase = true)
            }.orEmpty().sortedBy { it.name }

            val key = datFiles.joinToString("|") { "${it.name}:${it.lastModified()}" }
            if (key != cacheKey) {
                cachedIndex = DatIndex.merge(
                    datFiles.mapNotNull { file ->
                        runCatching { DatIndex.parse(file.readText()) }.getOrNull()
                    },
                )
                cacheKey = key
            }
            cachedIndex
        }
    }
}
