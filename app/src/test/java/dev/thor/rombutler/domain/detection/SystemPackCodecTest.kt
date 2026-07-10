package dev.thor.rombutler.domain.detection

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.AppSettings
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.model.SystemPack
import dev.thor.rombutler.domain.model.SystemPackDecodeResult
import dev.thor.rombutler.domain.model.SystemPackError
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

class SystemPackCodecTest {

    @Test
    fun `oversized packs are rejected before parsing`() {
        val decoded = SystemPackCodec.decode(" ".repeat(SystemPackCodec.MAX_PACK_BYTES + 1))

        assertThat(decoded).isEqualTo(
            SystemPackDecodeResult.Failure(SystemPackError.PACK_TOO_LARGE),
        )
    }

    @Test
    fun `bundled JSON matches the complete fallback registry`() {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream(BUILTIN_RESOURCE))
            .bufferedReader().use { it.readText() }

        val decoded = SystemPackCodec.decode(json)

        assertThat(decoded).isInstanceOf(SystemPackDecodeResult.Success::class.java)
        val systems = (decoded as SystemPackDecodeResult.Success).pack.systems
        assertThat(systems).isEqualTo(LegacySystemDefinitions.systems)
    }

    @Test
    fun `encode decode round trip preserves definitions and magic rules`() {
        val original = SystemPack(
            schemaVersion = 1,
            packId = "example.pack",
            displayName = "Example pack",
            systems = LegacySystemDefinitions.systems.take(16),
        )

        val decoded = SystemPackCodec.decode(SystemPackCodec.encode(original))

        assertThat(decoded).isEqualTo(SystemPackDecodeResult.Success(original))
    }

    @Test
    fun `invalid traversal folder is rejected`() {
        val decoded = SystemPackCodec.decode(
            packJson(
                systemJson = """
                    {
                      "id": "dos",
                      "displayName": "DOS",
                      "folder": "../dos",
                      "extensions": { "zip": "CERTAIN" }
                    }
                """.trimIndent(),
            ),
        )

        assertThat(decoded).isEqualTo(
            SystemPackDecodeResult.Failure(SystemPackError.INVALID_FIELD, "systems[0]"),
        )
    }

    @Test
    fun `unknown magic rule is rejected`() {
        val decoded = SystemPackCodec.decode(
            packJson(
                systemJson = """
                    {
                      "id": "dos",
                      "displayName": "DOS",
                      "folder": "dos",
                      "extensions": { "dosz": "CERTAIN" },
                      "magicRuleIds": ["download-code-from-web"]
                    }
                """.trimIndent(),
            ),
        )

        assertThat(decoded).isEqualTo(
            SystemPackDecodeResult.Failure(
                SystemPackError.UNKNOWN_MAGIC_RULE,
                "download-code-from-web",
            ),
        )
    }

    @Test
    fun `a certain claim shared inside one pack is rejected`() {
        val decoded = SystemPackCodec.decode(
            """
                {
                  "schemaVersion": 1,
                  "packId": "user.example",
                  "displayName": "Example",
                  "systems": [
                    { "id": "one", "displayName": "One", "folder": "one", "extensions": { "rom": "CERTAIN" } },
                    { "id": "two", "displayName": "Two", "folder": "two", "extensions": { "rom": "PROBABLE" } }
                  ]
                }
            """.trimIndent(),
        )

        assertThat(decoded).isEqualTo(
            SystemPackDecodeResult.Failure(SystemPackError.CERTAIN_EXTENSION_CONFLICT, "rom"),
        )
    }

    @Test
    fun `target folders are unique regardless of case`() {
        val decoded = SystemPackCodec.decode(
            """
                {
                  "schemaVersion": 1,
                  "packId": "user.example",
                  "displayName": "Example",
                  "systems": [
                    { "id": "one", "displayName": "One", "folder": "DOS", "extensions": {} },
                    { "id": "two", "displayName": "Two", "folder": "dos", "extensions": {} }
                  ]
                }
            """.trimIndent(),
        )

        assertThat(decoded).isEqualTo(
            SystemPackDecodeResult.Failure(SystemPackError.DUPLICATE_FOLDER, "DOS"),
        )
    }

    @Test
    fun `registry rejects built in ids without replacing built ins`() {
        val registry = SystemRegistry()
        val json = registry.encodeCustomPack(
            listOf(
                SystemDefinition(
                    id = "gba",
                    displayName = "Fake GBA",
                    esdeFolder = "fakegba",
                    extensions = mapOf("fake" to Confidence.CERTAIN),
                ),
            ),
        )

        val result = registry.validateCustomPack(json)
        registry.applyCustomPackJson(json)

        assertThat(result).isEqualTo(
            SystemPackDecodeResult.Failure(SystemPackError.BUILTIN_ID_COLLISION, "gba"),
        )
        assertThat(registry.state.value.customSystems).isEmpty()
        assertThat(registry.byId("gba")?.displayName).isEqualTo("Game Boy Advance")
    }

    @Test
    fun `shared custom extension becomes warning and remains ambiguous`() {
        val registry = SystemRegistry()
        val json = registry.encodeCustomPack(
            listOf(
                SystemDefinition(
                    id = "customhandheld",
                    displayName = "Custom Handheld",
                    esdeFolder = "customhandheld",
                    extensions = mapOf("gba" to Confidence.CERTAIN),
                ),
            ),
        )

        registry.applyCustomPackJson(json)
        val detection = DetectionEngine(registry).detect("game.gba")

        assertThat(registry.state.value.conflicts.map { it.extension }).containsExactly("gba")
        assertThat(detection.confidence).isEqualTo(Confidence.UNKNOWN)
        assertThat(detection.system).isNull()
    }

    @Test
    fun `conflict preview does not activate the candidate system`() {
        val registry = SystemRegistry()
        val candidate = SystemDefinition(
            id = "previewonly",
            displayName = "Preview Only",
            esdeFolder = "previewonly",
            extensions = mapOf("gba" to Confidence.CERTAIN),
        )

        val conflicts = registry.conflictsForCustomSystems(listOf(candidate))

        assertThat(conflicts.map { it.extension }).containsExactly("gba")
        assertThat(registry.byId("previewonly")).isNull()
        assertThat(registry.state.value.customSystems).isEmpty()
    }

    @Test
    fun `registry activates a pack from persisted settings`() {
        val pack = SystemPack(
            schemaVersion = 1,
            packId = "user.persisted",
            displayName = "Persisted",
            systems = listOf(
                SystemDefinition(
                    id = "persisted",
                    displayName = "Persisted System",
                    esdeFolder = "persisted",
                    extensions = mapOf("prs" to Confidence.CERTAIN),
                ),
            ),
        )
        val repository = ReadOnlySettingsRepository(
            AppSettings(customSystemPackJson = SystemPackCodec.encode(pack)),
        )

        val registry = SystemRegistry(repository, Dispatchers.Unconfined)

        assertThat(registry.byId("persisted")?.displayName).isEqualTo("Persisted System")
        assertThat(registry.state.value.builtInFallbackUsed).isFalse()
    }

    private fun packJson(systemJson: String): String = """
        {
          "schemaVersion": 1,
          "packId": "user.example",
          "displayName": "Example",
          "systems": [$systemJson]
        }
    """.trimIndent()

    private companion object {
        const val BUILTIN_RESOURCE = "system-packs/builtin-v1.json"
    }

    private class ReadOnlySettingsRepository(initial: AppSettings) : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(initial)
        override suspend fun setRomBasePath(path: String) = unused()
        override suspend fun setDownloadPath(path: String) = unused()
        override suspend fun setDeleteArchivesAfterExtract(enabled: Boolean) = unused()
        override suspend fun setAutoUpdateCheck(enabled: Boolean) = unused()
        override suspend fun setWatcherEnabled(enabled: Boolean) = unused()
        override suspend fun addSourcePath(path: String) = unused()
        override suspend fun removeSourcePath(path: String) = unused()
        override suspend fun setTrashInsteadOfDelete(enabled: Boolean) = unused()
        override suspend fun setDatFolderPath(path: String?) = unused()
        override suspend fun setThemeId(themeId: String) = unused()
        override suspend fun lastSeenVersionCode(): Int = 0
        override suspend fun setLastSeenVersionCode(versionCode: Int) = unused()
        override suspend fun setFolderOverride(systemId: String, folder: String?) = unused()
        override suspend fun setCustomSystemPack(json: String?) = unused()
        override suspend fun replaceSettings(settings: AppSettings) = unused()

        private fun unused(): Nothing = error("Not used by this test")
    }
}
