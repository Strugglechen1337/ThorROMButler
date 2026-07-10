package dev.thor.rombutler.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thor.rombutler.domain.detection.SystemRegistry
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.model.SystemPack
import dev.thor.rombutler.ui.theme.ThorRomButlerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemPackDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun importPreviewShowsExactPackAndConflictsBeforeConfirmation() {
        val registry = SystemRegistry()
        val custom = SystemDefinition(
            id = "preview",
            displayName = "Preview System",
            esdeFolder = "preview",
            extensions = mapOf("gba" to Confidence.CERTAIN),
        )
        val preview = SystemPackImportPreview(
            pack = SystemPack(1, "user.preview", "Preview Pack", listOf(custom)),
            conflicts = registry.conflictsForCustomSystems(listOf(custom)),
        )
        val registryState = registry.state.value
        var confirmed = false

        compose.setContent {
            ThorRomButlerTheme {
                SystemPackManagerDialog(
                    state = registryState,
                    importPreview = preview,
                    onSave = { _, _ -> },
                    onDelete = {},
                    onRequestImport = {},
                    onConfirmImport = { confirmed = true },
                    onCancelImport = {},
                    onExport = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(SystemPackTestTags.IMPORT_PREVIEW).assertIsDisplayed()
        compose.onNodeWithText("Preview Pack").assertIsDisplayed()
        compose.onNode(hasText(".gba", substring = true)).assertIsDisplayed()
        compose.onNodeWithTag(SystemPackTestTags.CONFIRM_IMPORT).performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun editorRejectsBuiltInIdAndAcceptsSafeCustomDefinition() {
        val registry = SystemRegistry()
        val registryState = registry.state.value
        var saved: SystemDefinition? = null

        compose.setContent {
            ThorRomButlerTheme {
                SystemPackManagerDialog(
                    state = registryState,
                    importPreview = null,
                    onSave = { _, definition -> saved = definition },
                    onDelete = {},
                    onRequestImport = {},
                    onConfirmImport = {},
                    onCancelImport = {},
                    onExport = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(SystemPackTestTags.ADD_SYSTEM).performClick()
        compose.onNodeWithTag(SystemPackTestTags.NAME_FIELD).performTextInput("Custom Handheld")
        compose.onNodeWithTag(SystemPackTestTags.ID_FIELD).performTextInput("gba")
        compose.onNodeWithTag(SystemPackTestTags.FOLDER_FIELD).performTextInput("customhandheld")
        compose.onNodeWithTag(SystemPackTestTags.EXTENSIONS_FIELD).performTextInput("gba")
        compose.onNodeWithTag(SystemPackTestTags.SAVE_SYSTEM).assertIsNotEnabled()

        compose.onNodeWithTag(SystemPackTestTags.ID_FIELD).performTextClearance()
        compose.onNodeWithTag(SystemPackTestTags.ID_FIELD).performTextInput("customhandheld")
        compose.onNodeWithTag(SystemPackTestTags.SAVE_SYSTEM).assertIsEnabled().performClick()

        compose.runOnIdle { assertEquals("customhandheld", saved?.id) }
    }
}
