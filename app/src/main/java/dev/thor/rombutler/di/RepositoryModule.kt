package dev.thor.rombutler.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.thor.rombutler.data.archive.ArchiveRomExtractor
import dev.thor.rombutler.data.archive.CommonsArchiveAnalyzer
import dev.thor.rombutler.data.files.FileArchiveScanner
import dev.thor.rombutler.data.files.LibraryChecker
import dev.thor.rombutler.data.files.LooseRomScanner
import dev.thor.rombutler.data.files.RomFolderManager
import dev.thor.rombutler.data.log.FileLogRepository
import dev.thor.rombutler.data.settings.SettingsDataStore
import dev.thor.rombutler.domain.repository.ArchiveAnalyzer
import dev.thor.rombutler.domain.repository.ArchiveRepository
import dev.thor.rombutler.domain.repository.LibraryRepository
import dev.thor.rombutler.domain.repository.LogRepository
import dev.thor.rombutler.domain.repository.LooseRomRepository
import dev.thor.rombutler.domain.repository.RomExtractor
import dev.thor.rombutler.domain.repository.RomFolderRepository
import dev.thor.rombutler.domain.repository.SettingsRepository
import javax.inject.Singleton

/**
 * Binds data-layer implementations to their domain interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindArchiveRepository(impl: FileArchiveScanner): ArchiveRepository

    @Binds
    @Singleton
    abstract fun bindArchiveAnalyzer(impl: CommonsArchiveAnalyzer): ArchiveAnalyzer

    @Binds
    @Singleton
    abstract fun bindRomFolderRepository(impl: RomFolderManager): RomFolderRepository

    @Binds
    @Singleton
    abstract fun bindRomExtractor(impl: ArchiveRomExtractor): RomExtractor

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: FileLogRepository): LogRepository

    @Binds
    @Singleton
    abstract fun bindLooseRomRepository(impl: LooseRomScanner): LooseRomRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryChecker): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindVerificationRepository(
        impl: dev.thor.rombutler.data.verification.DatRepository,
    ): dev.thor.rombutler.domain.verification.VerificationRepository
}
