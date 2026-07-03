package dev.thor.rombutler.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.ui.components.FolderPickerDialog

/**
 * App settings: folders, extraction behavior, manual update check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDownloadPicker by remember { mutableStateOf(false) }
    var showRomBasePicker by remember { mutableStateOf(false) }
    var showFolderOverrides by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Folders
            SettingsCard {
                FolderRow(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.setup_download_title),
                    path = settings.downloadPath,
                    onClick = { showDownloadPicker = true },
                )
                Spacer(Modifier.size(12.dp))
                FolderRow(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.setup_rombase_title),
                    path = settings.romBasePath,
                    onClick = { showRomBasePicker = true },
                )
            }

            // Behavior
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_delete_archives),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_delete_archives_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.deleteArchivesAfterExtract,
                        onCheckedChange = viewModel::setDeleteArchives,
                    )
                }
                if (settings.deleteArchivesAfterExtract) {
                    Spacer(Modifier.size(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_trash),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_trash_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.trashInsteadOfDelete,
                            onCheckedChange = viewModel::setTrashInsteadOfDelete,
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_update),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_auto_update_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.autoUpdateCheck,
                        onCheckedChange = viewModel::setAutoUpdateCheck,
                    )
                }
                Spacer(Modifier.size(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_watcher),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_watcher_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.watcherEnabled,
                        onCheckedChange = viewModel::setWatcherEnabled,
                    )
                }
            }

            // Additional source folders (Telegram, USB imports, ...)
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_sources_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_sources_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (path in settings.additionalSourcePaths) {
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeSourcePath(path) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.settings_sources_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { showSourcePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_sources_add)) }
            }

            // Settings backup: export/import via JSON in the download folder
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_backup_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Row {
                    OutlinedButton(
                        onClick = {
                            viewModel.exportSettings { ok ->
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.settings_backup_done else R.string.settings_backup_failed,
                                    ),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_backup_export)) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.importSettings { ok ->
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.settings_backup_done else R.string.settings_backup_failed,
                                    ),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_backup_import)) }
                }
            }

            // Library check: statistics + misplaced ROMs
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_library_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(10.dp))
                when (val lib = libraryState) {
                    LibraryCheckState.Idle -> OutlinedButton(
                        onClick = viewModel::checkLibrary,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_library_check)) }

                    LibraryCheckState.Running -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    is LibraryCheckState.Failed -> Column {
                        Text(
                            text = lib.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(
                            onClick = viewModel::checkLibrary,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_library_check)) }
                    }

                    is LibraryCheckState.Done -> Column {
                        val report = lib.report
                        Text(
                            text = stringResource(
                                R.string.settings_library_summary,
                                report.totalRoms,
                                dev.thor.rombutler.ui.components.formatFileSize(report.totalBytes),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.size(6.dp))
                        for (stat in report.stats.take(5)) {
                            Row {
                                Text(
                                    text = stat.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${stat.romCount} · " +
                                        dev.thor.rombutler.ui.components.formatFileSize(stat.totalBytes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        if (report.misplaced.isNotEmpty()) {
                            Button(
                                onClick = {
                                    if (viewModel.prepareMisplacedReview(report)) onOpenReview()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.settings_library_misplaced,
                                        report.misplaced.size,
                                    ),
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.settings_library_all_good),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        OutlinedButton(
                            onClick = viewModel::checkLibrary,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_library_check)) }
                    }
                }
            }

            // System folder overrides (non-ES-DE frontends) + battery hint
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFolderOverrides = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_folder_overrides),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_folder_overrides_hint,
                                settings.folderOverrides.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.settings_battery_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_battery_button))
                }
            }

            // Crash report (only when one was recorded)
            if (viewModel.hasCrashReport) {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_crash_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_crash_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.crashReportText()?.let { text ->
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Thor ROM Butler crash report")
                                    .putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_crash_share))
                    }
                }
            }

            // Version + update check
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_version, viewModel.appVersion),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.size(10.dp))
                UpdateSection(
                    state = updateState,
                    onCheck = viewModel::checkForUpdates,
                    onDownload = viewModel::downloadUpdate,
                    onOpenUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }

            // Optional support
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_support_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_support_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_support_button))
                }
            }
        }
    }

    if (showSourcePicker) {
        FolderPickerDialog(
            title = stringResource(R.string.settings_sources_add),
            initialPath = settings.downloadPath,
            onSelect = {
                viewModel.addSourcePath(it)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
        )
    }
    if (showFolderOverrides) {
        FolderOverridesDialog(
            systems = viewModel.registry.systems,
            overrides = settings.folderOverrides,
            onSave = viewModel::setFolderOverride,
            onDismiss = { showFolderOverrides = false },
        )
    }
    if (showDownloadPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.setup_download_title),
            initialPath = settings.downloadPath,
            onSelect = {
                viewModel.setDownloadPath(it)
                showDownloadPicker = false
            },
            onDismiss = { showDownloadPicker = false },
        )
    }
    if (showRomBasePicker) {
        FolderPickerDialog(
            title = stringResource(R.string.setup_rombase_title),
            initialPath = settings.romBasePath,
            onSelect = {
                viewModel.setRomBasePath(it)
                showRomBasePicker = false
            },
            onDismiss = { showRomBasePicker = false },
        )
    }
}

private const val SUPPORT_URL = "https://paypal.me/marcelstrohmeyer"

/**
 * Per-system folder editor: tap a system, type the folder name your
 * frontend expects; empty restores the ES-DE default.
 */
@Composable
private fun FolderOverridesDialog(
    systems: List<dev.thor.rombutler.domain.model.SystemDefinition>,
    overrides: Map<String, String>,
    onSave: (systemId: String, folder: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<dev.thor.rombutler.domain.model.SystemDefinition?>(null) }

    editing?.let { system ->
        var value by remember(system.id) {
            mutableStateOf(overrides[system.id] ?: system.esdeFolder)
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(system.displayName) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_folder_override_label)) },
                    supportingText = {
                        Text(stringResource(R.string.settings_folder_override_default, system.esdeFolder))
                    },
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    onSave(system.id, value.takeIf { it.isNotBlank() && it != system.esdeFolder })
                    editing = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onSave(system.id, null) // restore default
                    editing = null
                }) { Text(stringResource(R.string.settings_folder_override_reset)) }
            },
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_folder_overrides),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(systems.size) { index ->
                        val system = systems[index]
                        val override = overrides[system.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = system }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = system.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = override ?: system.esdeFolder,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (override != null) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun FolderRow(
    icon: ImageVector,
    title: String,
    path: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = path ?: stringResource(R.string.settings_folder_not_set),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onDownload: (dev.thor.rombutler.data.update.UpdateInfo) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    when (state) {
        UpdateCheckState.Idle -> OutlinedButton(
            onClick = onCheck,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_check_updates))
        }

        UpdateCheckState.Checking -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is UpdateCheckState.Done -> Column {
            Text(
                text = if (state.info.isNewer) {
                    stringResource(R.string.settings_update_available, state.info.latestVersion)
                } else {
                    stringResource(R.string.settings_up_to_date)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.info.isNewer) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (state.info.isNewer) {
                Spacer(Modifier.size(8.dp))
                if (state.info.apkDownloadUrl != null) {
                    // Direct download: system notification opens the installer
                    OutlinedButton(
                        onClick = { onDownload(state.info) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_download_update))
                    }
                    Spacer(Modifier.size(4.dp))
                }
                OutlinedButton(
                    onClick = { onOpenUrl(state.info.releaseUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_open_release))
                }
            }
        }

        is UpdateCheckState.Failed -> Text(
            text = stringResource(R.string.settings_update_failed, state.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
