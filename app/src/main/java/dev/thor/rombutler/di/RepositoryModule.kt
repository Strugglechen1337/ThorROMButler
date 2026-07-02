package dev.thor.rombutler.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.thor.rombutler.data.files.FileArchiveScanner
import dev.thor.rombutler.data.settings.SettingsDataStore
import dev.thor.rombutler.domain.repository.ArchiveRepository
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
}
