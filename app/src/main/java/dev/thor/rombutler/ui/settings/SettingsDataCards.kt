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

/** Settings card: Settings backup. */
@Composable
internal fun SettingsBackupCard(
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    SettingsCard {
        SectionHeader(
            title = stringResource(R.string.settings_backup_title),
            hint = stringResource(R.string.settings_backup_hint),
        )
        Spacer(Modifier.size(4.dp))
        Row {
            OutlinedButton(
                onClick = {
                    viewModel.exportSettings { ok ->
                        android.widget.Toast.makeText(
                            context,
                            resources.getString(
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
                            resources.getString(
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
}

/** Settings card: LAN receive. */
@Composable
internal fun ReceiveCard(
    viewModel: SettingsViewModel,
    receiveState: dev.thor.rombutler.receive.ReceiveState,
    startReceive: () -> Unit,
    localNetworkPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
) {
    val context = LocalContext.current
    SettingsCard {
        SectionHeader(
            title = stringResource(R.string.receive_title),
            hint = stringResource(R.string.receive_hint),
        )
        when (val rs = receiveState) {
            dev.thor.rombutler.receive.ReceiveState.Off -> {
                Spacer(Modifier.size(4.dp))
                OutlinedButton(
                    onClick = {
                        if (LocalNetworkPermission.isGranted(context)) {
                            startReceive()
                        } else {
                            localNetworkPermissionLauncher.launch(
                                Manifest.permission.ACCESS_LOCAL_NETWORK,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.receive_start)) }
            }

            is dev.thor.rombutler.receive.ReceiveState.Running -> {
                Text(
                    text = stringResource(R.string.receive_running_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = rs.url,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (rs.receivedCount > 0) {
                    Text(
                        text = stringResource(R.string.receive_count, rs.receivedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, ReceivePermissionActivity::class.java),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.receive_show_session)) }
                Spacer(Modifier.size(6.dp))
                OutlinedButton(
                    onClick = viewModel::stopReceive,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.receive_stop)) }
            }
        }
    }
}

/** Settings card: Library check. */
@Composable
internal fun LibraryCard(
    viewModel: SettingsViewModel,
    libraryState: LibraryCheckState,
    exactDuplicateState: ExactDuplicateState,
    onOpenReview: () -> Unit,
    onOpenDoctor: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    SettingsCard {
        Text(
            text = stringResource(R.string.settings_library_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = onOpenDoctor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.doctor_open))
        }
        Text(
            text = stringResource(R.string.doctor_entry_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.size(12.dp))
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
                if (report.duplicates.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_library_duplicates,
                            report.duplicates.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.settings_library_exact_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_library_exact_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                when (val exact = exactDuplicateState) {
                    ExactDuplicateState.Idle -> OutlinedButton(
                        onClick = viewModel::findExactDuplicates,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_library_exact_check)) }

                    ExactDuplicateState.Running -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.settings_library_exact_running))
                    }

                    is ExactDuplicateState.Failed -> Column {
                        Text(
                            text = exact.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = viewModel::findExactDuplicates,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_library_exact_check)) }
                    }

                    is ExactDuplicateState.Done -> {
                        if (exact.report.groups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_library_exact_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.settings_library_exact_summary,
                                    exact.report.groups.size,
                                    exact.report.duplicateFiles,
                                    dev.thor.rombutler.ui.components.formatFileSize(
                                        exact.report.reclaimableBytes,
                                    ),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = stringResource(R.string.settings_library_exact_details),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        OutlinedButton(
                            onClick = viewModel::findExactDuplicates,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_library_exact_check)) }
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
                    onClick = {
                        viewModel.exportLibrary(
                            report = report,
                            exactDuplicates = (exactDuplicateState as? ExactDuplicateState.Done)?.report,
                        ) { ok ->
                            android.widget.Toast.makeText(
                                context,
                                resources.getString(
                                    if (ok) R.string.settings_backup_done else R.string.settings_backup_failed,
                                ),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_library_export)) }
                Spacer(Modifier.size(6.dp))
                OutlinedButton(
                    onClick = viewModel::checkLibrary,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_library_check)) }
            }
        }
    }
}

/** Settings card: ROM mirror backup. */
@Composable
internal fun RomBackupCard(
    settings: dev.thor.rombutler.domain.model.AppSettings,
    viewModel: SettingsViewModel,
    romBackupState: dev.thor.rombutler.backup.BackupRunState,
    backupManifest: dev.thor.rombutler.data.backup.BackupManifest?,
    setShowBackupTargetPicker: (Boolean) -> Unit,
    setShowRestoreConfirm: (Boolean) -> Unit,
) {
    SettingsCard {
        SectionHeader(
            title = stringResource(R.string.settings_rombackup_title),
            hint = stringResource(R.string.settings_rombackup_hint),
        )
        Spacer(Modifier.size(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShowBackupTargetPicker(true) },
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
                    text = stringResource(R.string.settings_rombackup_choose_target),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = settings.backupTargetPath
                        ?: stringResource(R.string.settings_folder_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        val manifest = backupManifest
        Text(
            text = if (manifest != null) {
                stringResource(
                    R.string.settings_rombackup_last,
                    java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM,
                        java.text.DateFormat.SHORT,
                    ).format(java.util.Date(manifest.createdAtMillis)),
                    manifest.fileCount,
                    dev.thor.rombutler.ui.components.formatFileSize(manifest.totalBytes),
                )
            } else {
                stringResource(R.string.settings_rombackup_none)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        when (val backup = romBackupState) {
            is dev.thor.rombutler.backup.BackupRunState.Running -> Column {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { backup.progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(
                        R.string.backup_progress,
                        backup.progress.copiedFiles,
                        backup.progress.totalFiles,
                        backup.progress.currentName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = viewModel::cancelRomBackup,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_cancel)) }
            }

            else -> Column {
                val ready = !settings.romBasePath.isNullOrBlank() &&
                    !settings.backupTargetPath.isNullOrBlank()
                Button(
                    onClick = viewModel::startRomBackup,
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_rombackup_run)) }
                Spacer(Modifier.size(6.dp))
                OutlinedButton(
                    onClick = { setShowRestoreConfirm(true) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_rombackup_restore)) }
            }
        }
    }
}
