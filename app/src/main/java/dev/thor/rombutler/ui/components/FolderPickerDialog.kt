package dev.thor.rombutler.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thor.rombutler.R
import java.io.File

/**
 * Lightweight folder browser built directly on [java.io.File].
 *
 * Deliberately no SAF: the app already holds MANAGE_EXTERNAL_STORAGE, and
 * plain File access keeps navigation instant on large SD cards.
 *
 * @param title dialog title.
 * @param initialPath folder to open first; falls back to the storage roots
 *   overview when it does not exist.
 * @param onSelect called with the absolute path of the confirmed folder.
 * @param onDismiss called when the user cancels.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    initialPath: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // null = showing the storage-roots overview (internal storage / SD cards)
    var currentDir by remember {
        mutableStateOf(initialPath?.let(::File)?.takeIf { it.isDirectory })
    }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onCreate = { name ->
                val created = currentDir?.let { File(it, name) }
                if (created != null && (created.isDirectory || created.mkdirs())) {
                    currentDir = created // jump into the new folder
                }
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Current location + up navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dir = currentDir
                    if (dir != null) {
                        IconButton(onClick = { currentDir = dir.parentFile?.takeIf { it.canRead() } }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.picker_up),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = dir?.absolutePath ?: stringResource(R.string.picker_storage_roots),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (dir != null) {
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.CreateNewFolder,
                                contentDescription = stringResource(R.string.picker_new_folder),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Directory list. weight(fill=false) instead of a fixed
                // height: on low landscape screens (AYN Thor!) the list
                // shrinks so the action buttons below always stay visible.
                val entries = remember(currentDir) {
                    currentDir?.listDirectories() ?: storageRoots()
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(entries, key = { it.absolutePath }) { entry ->
                        FolderRow(
                            file = entry,
                            isRoot = currentDir == null,
                            onClick = { currentDir = entry },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.size(12.dp))

                // Actions: cancel / select the folder we are currently in
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { currentDir?.let { onSelect(it.absolutePath) } },
                        enabled = currentDir != null,
                    ) {
                        // Name the folder so it is obvious WHAT gets picked
                        val dir = currentDir
                        Text(
                            text = if (dir != null) {
                                stringResource(R.string.picker_select_named_folder, dir.displayName())
                            } else {
                                stringResource(R.string.picker_select_this_folder)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(file: File, isRoot: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (isRoot) Icons.Filled.SdCard else Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            val isInternalRoot = isRoot &&
                file.absolutePath == android.os.Environment.getExternalStorageDirectory().absolutePath
            Text(
                text = if (isInternalRoot) stringResource(R.string.picker_internal_storage) else file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small name-input dialog for creating a subfolder in the current dir. */
@Composable
private fun NewFolderDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_new_folder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.picker_new_folder_hint)) },
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank() && !name.contains('/') && !name.contains('\\'),
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Folder name for UI labels; the internal-storage root gets a real name. */
@Composable
private fun File.displayName(): String =
    if (absolutePath == android.os.Environment.getExternalStorageDirectory().absolutePath) {
        stringResource(R.string.picker_internal_storage)
    } else {
        name
    }

/** Sorted, readable subdirectories (hidden folders excluded). */
private fun File.listDirectories(): List<File> =
    listFiles { f: File -> f.isDirectory && !f.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

/**
 * Available storage volumes: internal storage plus any mounted SD cards
 * found under /storage (relevant on gaming handhelds).
 */
private fun storageRoots(): List<File> {
    val internal = android.os.Environment.getExternalStorageDirectory()
    val external = File("/storage")
        .listFiles { f: File -> f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead() }
        ?.toList()
        .orEmpty()
    return listOf(internal) + external.sortedBy { it.name }
}
