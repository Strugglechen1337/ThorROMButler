package dev.thor.rombutler.data.settings

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.AppSettings
import org.junit.Test

class SettingsBackupCodecTest {

    @Test
    fun `round trip preserves every configurable setting`() {
        val original = AppSettings(
            romBasePath = "/storage/emulated/0/ROMs",
            downloadPath = "/storage/emulated/0/Download",
            deleteArchivesAfterExtract = true,
            autoUpdateCheck = true,
            watcherEnabled = true,
            additionalSourcePaths = listOf("/storage/emulated/0/Telegram", "/storage/1234/USB"),
            trashInsteadOfDelete = true,
            folderOverrides = mapOf("psx" to "PlayStation"),
            datFolderPath = "/storage/emulated/0/DATs",
            themeId = "odin",
            customSystemPackJson = VALID_PACK,
        )

        val decoded = SettingsBackupCodec.decode(
            json = SettingsBackupCodec.encode(original),
            current = AppSettings(themeId = "crt"),
            canonicalizeCustomPack = { VALID_PACK },
        )

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `old backups keep settings that did not exist yet`() {
        val current = AppSettings(
            datFolderPath = "/storage/emulated/0/DATs",
            themeId = "crt",
            customSystemPackJson = VALID_PACK,
        )

        val decoded = SettingsBackupCodec.decode(
            json = """{"deleteArchivesAfterExtract":true}""",
            current = current,
            canonicalizeCustomPack = { error("No pack expected") },
        )

        assertThat(decoded).isEqualTo(current.copy(deleteArchivesAfterExtract = true))
    }

    @Test
    fun `explicit null values clear optional settings`() {
        val exported = SettingsBackupCodec.encode(AppSettings())
        val current = AppSettings(
            romBasePath = "/storage/emulated/0/ROMs",
            downloadPath = "/storage/emulated/0/Download",
            datFolderPath = "/storage/emulated/0/DATs",
            customSystemPackJson = VALID_PACK,
        )

        val decoded = SettingsBackupCodec.decode(
            json = exported,
            current = current,
            canonicalizeCustomPack = { error("Null must not be canonicalized") },
        )

        assertThat(decoded.romBasePath).isNull()
        assertThat(decoded.downloadPath).isNull()
        assertThat(decoded.datFolderPath).isNull()
        assertThat(decoded.customSystemPackJson).isNull()
    }

    @Test
    fun `unsafe folder override rejects the complete backup`() {
        val result = runCatching {
            SettingsBackupCodec.decode(
                json = """
                    {
                      "schemaVersion": 1,
                      "folderOverrides": { "psx": "../outside" }
                    }
                """.trimIndent(),
                current = AppSettings(),
                canonicalizeCustomPack = { it },
            )
        }

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `invalid custom pack rejects the complete backup`() {
        val result = runCatching {
            SettingsBackupCodec.decode(
                json = """
                    {
                      "schemaVersion": 1,
                      "customSystemPack": { "schemaVersion": 999 }
                    }
                """.trimIndent(),
                current = AppSettings(),
                canonicalizeCustomPack = { error("Invalid pack") },
            )
        }

        assertThat(result.isFailure).isTrue()
    }

    private companion object {
        val VALID_PACK = """
            {
              "schemaVersion": 1,
              "packId": "user.backup",
              "displayName": "Backup pack",
              "systems": [
                {
                  "id": "backup",
                  "displayName": "Backup System",
                  "folder": "backup",
                  "extensions": { "bak": "CERTAIN" }
                }
              ]
            }
        """.trimIndent()
    }
}
