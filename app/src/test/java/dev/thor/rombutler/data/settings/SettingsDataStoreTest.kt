package dev.thor.rombutler.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsDataStoreTest {

    @Test
    fun `replace settings writes one complete snapshot and preserves release state`() = runTest {
        val dataStore = TrackingPreferencesDataStore()
        val repository = SettingsDataStore(dataStore)
        repository.setLastSeenVersionCode(42)
        val imported = AppSettings(
            romBasePath = "/storage/emulated/0/ROMs",
            downloadPath = "/storage/emulated/0/Download",
            deleteArchivesAfterExtract = true,
            autoUpdateCheck = true,
            watcherEnabled = true,
            additionalSourcePaths = listOf("/storage/emulated/0/Telegram"),
            trashInsteadOfDelete = true,
            folderOverrides = mapOf("psx" to "PlayStation"),
            datFolderPath = "/storage/emulated/0/DATs",
            themeId = "crt",
            customSystemPackJson = "{\"schemaVersion\":1}",
        )

        repository.replaceSettings(imported)

        assertThat(repository.settings.first()).isEqualTo(imported)
        assertThat(repository.lastSeenVersionCode()).isEqualTo(42)
        assertThat(dataStore.updateCount).isEqualTo(2)
    }

    @Test
    fun `unsafe folder override is rejected without a write`() = runTest {
        val dataStore = TrackingPreferencesDataStore()
        val repository = SettingsDataStore(dataStore)

        val result = runCatching { repository.setFolderOverride("psx", "../outside") }

        assertThat(result.isFailure).isTrue()
        assertThat(repository.settings.first().folderOverrides).isEmpty()
        assertThat(dataStore.updateCount).isEqualTo(0)
    }

    private class TrackingPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        var updateCount = 0
            private set

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            updateCount++
            return updated
        }
    }
}
