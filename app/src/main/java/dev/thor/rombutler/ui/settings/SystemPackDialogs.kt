package dev.thor.rombutler.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.thor.rombutler.R
import dev.thor.rombutler.domain.detection.SystemPackCodec
import dev.thor.rombutler.domain.detection.SystemRegistryState
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.SystemDefinition

/** Manages the optional, validated user system pack. */
@Composable
internal fun SystemPackManagerDialog(
    state: SystemRegistryState,
    importPreview: SystemPackImportPreview?,
    onSave: (originalId: String?, definition: SystemDefinition) -> Unit,
    onDelete: (systemId: String) -> Unit,
    onRequestImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<SystemDefinition?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SystemDefinition?>(null) }

    if (adding || editing != null) {
        SystemEditorDialog(
            initial = editing,
            allSystems = state.systems,
            customSystems = state.customSystems,
            onSave = { definition ->
                onSave(editing?.id, definition)
                editing = null
                adding = false
            },
            onDismiss = {
                editing = null
                adding = false
            },
        )
    }

    deleting?.let { system ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.settings_system_delete_title)) },
            text = { Text(stringResource(R.string.settings_system_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(system.id)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (importPreview != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(stringResource(R.string.settings_system_import_preview_title)) },
            text = {
                Column(
                    modifier = Modifier.testTag(SystemPackTestTags.IMPORT_PREVIEW),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = importPreview.pack.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_system_import_system_count,
                            importPreview.pack.systems.size,
                            importPreview.pack.systems.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            if (state.customSystems.isEmpty()) {
                                R.string.settings_system_import_add_body
                            } else {
                                R.string.settings_system_import_body
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (importPreview.conflicts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_system_import_no_conflicts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        WarningText(
                            pluralStringResource(
                                R.plurals.settings_system_packs_conflicts,
                                importPreview.conflicts.size,
                                importPreview.conflicts.size,
                            ),
                            horizontalPadding = 0.dp,
                        )
                        for (conflict in importPreview.conflicts.take(MAX_VISIBLE_CONFLICTS)) {
                            Text(
                                text = stringResource(
                                    R.string.settings_system_packs_conflict_item,
                                    conflict.extension,
                                    conflict.systemNames.joinToString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmImport,
                    modifier = Modifier.testTag(SystemPackTestTags.CONFIRM_IMPORT),
                ) { Text(stringResource(R.string.settings_system_packs_import)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_system_packs_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                Text(
                    text = stringResource(R.string.settings_system_packs_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                if (state.customPackError != null) {
                    Spacer(Modifier.size(10.dp))
                    WarningText(stringResource(R.string.settings_system_pack_invalid_saved))
                }
                if (state.conflicts.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    WarningText(
                        pluralStringResource(
                            R.plurals.settings_system_packs_conflicts,
                            state.conflicts.size,
                            state.conflicts.size,
                        ),
                    )
                    for (conflict in state.conflicts.take(MAX_VISIBLE_CONFLICTS)) {
                        Text(
                            text = stringResource(
                                R.string.settings_system_packs_conflict_item,
                                conflict.extension,
                                conflict.systemNames.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (state.customSystems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.settings_system_packs_none),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            )
                        }
                    }
                    items(state.customSystems, key = { it.id }) { system ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = system }
                                .padding(start = 20.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = system.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${system.id}  ·  roms/${system.esdeFolder}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (system.extensions.isNotEmpty()) {
                                    Text(
                                        text = system.extensions.keys.sorted()
                                            .joinToString { ".$it" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { editing = system }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(
                                        R.string.settings_system_editor_edit,
                                    ),
                                )
                            }
                            IconButton(onClick = { deleting = system }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.settings_system_delete_title,
                                    ),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { adding = true },
                        enabled = state.customSystems.size < MAX_CUSTOM_SYSTEMS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SystemPackTestTags.ADD_SYSTEM),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_system_packs_add))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRequestImport,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(SystemPackTestTags.REQUEST_IMPORT),
                        ) { Text(stringResource(R.string.settings_system_packs_import)) }
                        OutlinedButton(
                            onClick = onExport,
                            enabled = state.customSystems.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_system_packs_export)) }
                    }
                    Text(
                        text = stringResource(R.string.settings_system_packs_file_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

@Composable
private fun WarningText(text: String, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    Row(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Editor for safe extension-based custom definitions. */
@Composable
private fun SystemEditorDialog(
    initial: SystemDefinition?,
    allSystems: List<SystemDefinition>,
    customSystems: List<SystemDefinition>,
    onSave: (SystemDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayName by remember(initial?.id) { mutableStateOf(initial?.displayName.orEmpty()) }
    var id by remember(initial?.id) { mutableStateOf(initial?.id.orEmpty()) }
    var folder by remember(initial?.id) { mutableStateOf(initial?.esdeFolder.orEmpty()) }
    var extensionsText by remember(initial?.id) {
        mutableStateOf(initial?.extensions?.keys?.sorted()?.joinToString(", ").orEmpty())
    }
    var gameSubfolder by remember(initial?.id) {
        mutableStateOf(initial?.gameSubfolder ?: false)
    }

    val normalizedId = id.trim().lowercase()
    val normalizedFolder = folder.trim()
    val extensions = extensionsText
        .split(Regex("[,;\\s]+"))
        .map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
    val extensionMap = extensions.associateWith { extension ->
        initial?.extensions?.get(extension) ?: Confidence.CERTAIN
    }
    val otherSystems = allSystems.filterNot { it.id == initial?.id }
    val otherCustomSystems = customSystems.filterNot { it.id == initial?.id }
    val nameValid = SystemPackCodec.isValidDisplayName(displayName.trim())
    val idValid = SystemPackCodec.isValidSystemId(normalizedId) &&
        otherSystems.none { it.id == normalizedId }
    val folderValid = SystemPackCodec.isValidFolder(normalizedFolder) && otherSystems.none {
        it.esdeFolder.equals(normalizedFolder, ignoreCase = true)
    }
    val extensionsValid = extensions.all(SystemPackCodec::isValidExtension) &&
        extensions.none { extension ->
            val confidence = extensionMap.getValue(extension)
            otherCustomSystems.any { other ->
                other.extensions[extension]?.let { otherConfidence ->
                    confidence == Confidence.CERTAIN || otherConfidence == Confidence.CERTAIN
                } == true
            }
        }
    val formValid = nameValid && idValid && folderValid && extensionsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) {
                        R.string.settings_system_editor_new
                    } else {
                        R.string.settings_system_editor_edit
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.settings_system_name)) },
                    isError = displayName.isNotEmpty() && !nameValid,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SystemPackTestTags.NAME_FIELD),
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.settings_system_id)) },
                    supportingText = { Text(stringResource(R.string.settings_system_id_hint)) },
                    isError = id.isNotEmpty() && !idValid,
                    enabled = initial == null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SystemPackTestTags.ID_FIELD),
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text(stringResource(R.string.settings_system_folder)) },
                    isError = folder.isNotEmpty() && !folderValid,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SystemPackTestTags.FOLDER_FIELD),
                )
                OutlinedTextField(
                    value = extensionsText,
                    onValueChange = { extensionsText = it },
                    label = { Text(stringResource(R.string.settings_system_extensions)) },
                    supportingText = {
                        Text(stringResource(R.string.settings_system_extensions_hint))
                    },
                    isError = extensionsText.isNotEmpty() && !extensionsValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SystemPackTestTags.EXTENSIONS_FIELD),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_system_game_subfolder),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = gameSubfolder,
                        onCheckedChange = { gameSubfolder = it },
                    )
                }
                if (!formValid &&
                    (displayName.isNotEmpty() || id.isNotEmpty() || folder.isNotEmpty())
                ) {
                    Text(
                        text = stringResource(R.string.settings_system_pack_error_field),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SystemDefinition(
                            id = normalizedId,
                            displayName = displayName.trim(),
                            esdeFolder = normalizedFolder,
                            extensions = extensionMap,
                            magicRules = initial?.magicRules.orEmpty(),
                            gameSubfolder = gameSubfolder,
                        ),
                    )
                },
                enabled = formValid,
                modifier = Modifier.testTag(SystemPackTestTags.SAVE_SYSTEM),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MAX_VISIBLE_CONFLICTS = 6
private const val MAX_CUSTOM_SYSTEMS = 128

internal object SystemPackTestTags {
    const val ADD_SYSTEM = "system_pack_add_system"
    const val REQUEST_IMPORT = "system_pack_request_import"
    const val IMPORT_PREVIEW = "system_pack_import_preview"
    const val CONFIRM_IMPORT = "system_pack_confirm_import"
    const val NAME_FIELD = "system_pack_name"
    const val ID_FIELD = "system_pack_id"
    const val FOLDER_FIELD = "system_pack_folder"
    const val EXTENSIONS_FIELD = "system_pack_extensions"
    const val SAVE_SYSTEM = "system_pack_save"
}
