package dev.thor.rombutler.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.domain.detection.SystemPackCodec
import dev.thor.rombutler.domain.model.SystemPackError
import dev.thor.rombutler.receive.LocalNetworkPermission
import dev.thor.rombutler.receive.ReceivePermissionActivity
import dev.thor.rombutler.ui.components.FolderPickerDialog

/**
 * App settings: folders, extraction behavior, manual update check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenDoctor: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val exactDuplicateState by viewModel.exactDuplicateState.collectAsStateWithLifecycle()
    val receiveState by viewModel.receiveState.collectAsStateWithLifecycle()
    val romBackupState by viewModel.romBackupState.collectAsStateWithLifecycle()
    val backupManifest by viewModel.backupManifest.collectAsStateWithLifecycle()
    val biosSuggestion by viewModel.biosSuggestion.collectAsStateWithLifecycle()
    val registryState by viewModel.registryState.collectAsStateWithLifecycle()
    val systemPackResult by viewModel.systemPackResult.collectAsStateWithLifecycle()
    val systemPackImportPreview by viewModel.systemPackImportPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    fun startReceive() {
        viewModel.startReceive(
            onStarted = {
                context.startActivity(Intent(context, ReceivePermissionActivity::class.java))
            },
            onFailed = {
                android.widget.Toast.makeText(
                    context,
                    resources.getString(R.string.receive_failed),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            },
        )
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startReceive()
        } else {
            android.widget.Toast.makeText(
                context,
                resources.getString(R.string.receive_permission_denied),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    var showDownloadPicker by remember { mutableStateOf(false) }
    var showRomBasePicker by remember { mutableStateOf(false) }
    var showFolderOverrides by remember { mutableStateOf(false) }
    var showFrontendProfiles by remember { mutableStateOf(false) }
    var showBackupTargetPicker by remember { mutableStateOf(false) }
    var showBiosPicker by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showClearLearningConfirm by remember { mutableStateOf(false) }

    // Load the manifest info whenever the backup target changes
    LaunchedEffect(settings.backupTargetPath) {
        viewModel.refreshBackupManifest()
    }

    // One-shot toast when a backup/restore run finishes
    LaunchedEffect(romBackupState) {
        val finished = romBackupState as? dev.thor.rombutler.backup.BackupRunState.Finished
            ?: return@LaunchedEffect
        val summary = finished.summary
        val message = when {
            summary.errorMessage != null -> summary.errorMessage
            else -> resources.getString(
                if (summary.mode == dev.thor.rombutler.backup.BackupMode.BACKUP) {
                    R.string.backup_result_backup
                } else {
                    R.string.backup_result_restore
                },
                summary.copied,
                summary.skipped,
            ) + (if (summary.failed > 0) {
                resources.getString(R.string.backup_result_failed_suffix, summary.failed)
            } else {
                ""
            }) + (if (summary.cancelled) {
                " · " + resources.getString(R.string.backup_result_cancelled)
            } else {
                ""
            })
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.acknowledgeRomBackupFinished()
    }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showDatPicker by remember { mutableStateOf(false) }
    var showSystemPacks by remember { mutableStateOf(false) }

    LaunchedEffect(systemPackResult) {
        val result = systemPackResult ?: return@LaunchedEffect
        android.widget.Toast.makeText(
            context,
            resources.systemPackActionMessage(result),
            android.widget.Toast.LENGTH_LONG,
        ).show()
        viewModel.consumeSystemPackResult()
    }

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
            FoldersCard(
                settings = settings,
                viewModel = viewModel,
                biosSuggestion = biosSuggestion,
                setShowDownloadPicker = { showDownloadPicker = it },
                setShowRomBasePicker = { showRomBasePicker = it },
                setShowBiosPicker = { showBiosPicker = it },
                setShowDatPicker = { showDatPicker = it },
            )

            // Behavior
            BehaviorCard(
                settings = settings,
                viewModel = viewModel,
                setShowClearLearningConfirm = { showClearLearningConfirm = it },
            )

            // Additional source folders (Telegram, USB imports, ...)
            SourcesCard(
                settings = settings,
                viewModel = viewModel,
                setShowSourcePicker = { showSourcePicker = it },
            )

            // Settings backup: export/import via JSON in the download folder
            SettingsBackupCard(
                viewModel = viewModel,
            )

            // Color theme
            ThemeCard(
                settings = settings,
                viewModel = viewModel,
            )

            // LAN receive mode
            ReceiveCard(
                viewModel = viewModel,
                receiveState = receiveState,
                startReceive = ::startReceive,
                localNetworkPermissionLauncher = localNetworkPermissionLauncher,
            )

            // Library check: statistics + misplaced ROMs
            LibraryCard(
                viewModel = viewModel,
                libraryState = libraryState,
                exactDuplicateState = exactDuplicateState,
                onOpenReview = onOpenReview,
                onOpenDoctor = onOpenDoctor,
            )

            // Incremental mirror backup of the whole ROM library
            RomBackupCard(
                settings = settings,
                viewModel = viewModel,
                romBackupState = romBackupState,
                backupManifest = backupManifest,
                setShowBackupTargetPicker = { showBackupTargetPicker = it },
                setShowRestoreConfirm = { showRestoreConfirm = it },
            )

            // Validated local extensions to the built-in system registry
            SystemPacksCard(
                registryState = registryState,
                setShowSystemPacks = { showSystemPacks = it },
            )

            // System folder overrides (non-ES-DE frontends) + battery hint
            FolderOverridesCard(
                settings = settings,
                setShowFolderOverrides = { showFolderOverrides = it },
                setShowFrontendProfiles = { showFrontendProfiles = it },
            )

            // Explicitly shared, privacy-friendly support report.
            DiagnosticsCard(
                viewModel = viewModel,
            )

            // Version + update check
            UpdateCard(
                viewModel = viewModel,
                updateState = updateState,
            )

            // Optional support
            SupportCard(
            )
        }
    }

    if (showDatPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.settings_dat_title),
            initialPath = settings.datFolderPath ?: settings.downloadPath,
            onSelect = {
                viewModel.setDatFolderPath(it)
                showDatPicker = false
            },
            onDismiss = { showDatPicker = false },
        )
    }
    if (showBiosPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.settings_bios_title),
            initialPath = settings.biosFolderPath ?: settings.romBasePath,
            onSelect = {
                viewModel.setBiosFolderPath(it)
                showBiosPicker = false
            },
            onDismiss = { showBiosPicker = false },
        )
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
            systems = registryState.systems,
            overrides = settings.folderOverrides,
            onSave = viewModel::setFolderOverride,
            onDismiss = { showFolderOverrides = false },
        )
    }
    if (showFrontendProfiles) {
        FrontendProfileDialog(
            onApply = { profile ->
                viewModel.applyFrontendProfile(profile)
                showFrontendProfiles = false
            },
            onDismiss = { showFrontendProfiles = false },
        )
    }
    if (showBackupTargetPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.settings_rombackup_choose_target),
            initialPath = settings.backupTargetPath,
            onSelect = {
                viewModel.setBackupTargetPath(it)
                showBackupTargetPicker = false
            },
            onDismiss = { showBackupTargetPicker = false },
        )
    }
    if (showRestoreConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.settings_rombackup_restore_title)) },
            text = { Text(stringResource(R.string.settings_rombackup_restore_text)) },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    viewModel.startRomRestore()
                }) { Text(stringResource(R.string.settings_rombackup_restore)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showClearLearningConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearLearningConfirm = false },
            title = { Text(stringResource(R.string.settings_assignment_learning_clear_title)) },
            text = { Text(stringResource(R.string.settings_assignment_learning_clear_text)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearLearnedAssignments()
                    showClearLearningConfirm = false
                }) { Text(stringResource(R.string.settings_assignment_learning_clear)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showClearLearningConfirm = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (showSystemPacks) {
        SystemPackManagerDialog(
            state = registryState,
            importPreview = systemPackImportPreview,
            onSave = viewModel::saveCustomSystem,
            onDelete = viewModel::deleteCustomSystem,
            onRequestImport = viewModel::previewSystemPackImport,
            onConfirmImport = viewModel::confirmSystemPackImport,
            onCancelImport = viewModel::cancelSystemPackImport,
            onExport = viewModel::exportSystemPack,
            onDismiss = {
                viewModel.cancelSystemPackImport()
                showSystemPacks = false
            },
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

private fun android.content.res.Resources.systemPackActionMessage(
    result: SystemPackActionResult,
): String = when (result) {
    is SystemPackActionResult.Success -> getString(
        when (result.action) {
            SystemPackAction.SAVED -> R.string.settings_system_pack_saved
            SystemPackAction.DELETED -> R.string.settings_system_pack_deleted
            SystemPackAction.IMPORTED -> R.string.settings_system_pack_imported
            SystemPackAction.EXPORTED -> R.string.settings_system_pack_exported
        },
    )

    is SystemPackActionResult.Failed -> getString(
        when (result.error) {
            SystemPackActionError.NO_DOWNLOAD_FOLDER -> R.string.settings_system_pack_no_download
            SystemPackActionError.FILE_NOT_FOUND -> R.string.settings_system_pack_file_missing
            SystemPackActionError.NO_CUSTOM_SYSTEMS -> R.string.settings_system_pack_no_systems
            SystemPackActionError.IO_ERROR -> R.string.settings_system_pack_io_error
        },
    )

    is SystemPackActionResult.Invalid -> {
        val base = getString(
            when (result.failure.error) {
                SystemPackError.MALFORMED_JSON -> R.string.settings_system_pack_error_malformed
                SystemPackError.PACK_TOO_LARGE -> R.string.settings_system_pack_error_large
                SystemPackError.UNSUPPORTED_VERSION -> R.string.settings_system_pack_error_version
                SystemPackError.INVALID_FIELD -> R.string.settings_system_pack_error_field
                SystemPackError.EMPTY_PACK -> R.string.settings_system_pack_error_empty
                SystemPackError.TOO_MANY_SYSTEMS -> R.string.settings_system_pack_error_many
                SystemPackError.DUPLICATE_ID -> R.string.settings_system_pack_error_duplicate_id
                SystemPackError.DUPLICATE_FOLDER -> R.string.settings_system_pack_error_duplicate_folder
                SystemPackError.CERTAIN_EXTENSION_CONFLICT -> {
                    R.string.settings_system_pack_error_certain_conflict
                }
                SystemPackError.UNKNOWN_MAGIC_RULE -> R.string.settings_system_pack_error_magic
                SystemPackError.BUILTIN_ID_COLLISION -> R.string.settings_system_pack_error_builtin_id
                SystemPackError.BUILTIN_FOLDER_COLLISION -> {
                    R.string.settings_system_pack_error_builtin_folder
                }
                SystemPackError.RESERVED_PACK_ID -> R.string.settings_system_pack_error_reserved
            },
        )
        result.failure.detail?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.settings_system_pack_error_detail, base, it) }
            ?: base
    }
}

internal const val SUPPORT_URL = "https://paypal.me/marcelstrohmeyer"

/**
 * Frontend presets: applying one replaces ALL folder overrides so the
 * target folders match the chosen frontend's convention in one step.
 */
@Composable
private fun FrontendProfileDialog(
    onApply: (dev.thor.rombutler.domain.model.FrontendProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember {
        mutableStateOf(dev.thor.rombutler.domain.model.FrontendProfile.ESDE)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_frontend_profile)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_frontend_profile_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                for (profile in dev.thor.rombutler.domain.model.FrontendProfile.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = profile }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selected == profile,
                            onClick = { selected = profile },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (profile) {
                                    dev.thor.rombutler.domain.model.FrontendProfile.ESDE ->
                                        stringResource(R.string.settings_frontend_esde)

                                    dev.thor.rombutler.domain.model.FrontendProfile.BATOCERA ->
                                        stringResource(R.string.settings_frontend_batocera)

                                    dev.thor.rombutler.domain.model.FrontendProfile.ONION ->
                                        stringResource(R.string.settings_frontend_onion)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (profile.overrides.isEmpty()) {
                                    stringResource(R.string.settings_frontend_default_folders)
                                } else {
                                    stringResource(
                                        R.string.settings_frontend_override_count,
                                        profile.overrides.size,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onApply(selected) }) {
                Text(stringResource(R.string.settings_frontend_apply))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

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
        val trimmed = value.trim()
        val valid = trimmed.isEmpty() || SystemPackCodec.isValidFolder(trimmed)
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
                        Text(
                            if (valid) {
                                stringResource(
                                    R.string.settings_folder_override_default,
                                    system.esdeFolder,
                                )
                            } else {
                                stringResource(R.string.settings_system_pack_error_field)
                            },
                        )
                    },
                    isError = !valid,
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    onSave(
                        system.id,
                        trimmed.takeIf { it.isNotBlank() && it != system.esdeFolder },
                    )
                    editing = null
                }, enabled = valid) { Text(stringResource(R.string.action_save)) }
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
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Card header with an optional long explanation collapsed behind an
 * info toggle — keeps the settings list scannable instead of a text
 * wall, while every detail stays one tap away.
 */
@Composable
internal fun SectionHeader(
    title: String,
    hint: String? = null,
    icon: ImageVector? = null,
) {
    var showHint by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            IconButton(onClick = { showHint = !showHint }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.settings_hint_toggle),
                    tint = if (showHint) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
    if (hint != null) {
        androidx.compose.animation.AnimatedVisibility(visible = showHint) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
internal fun FolderRow(
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
internal fun UpdateSection(
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
