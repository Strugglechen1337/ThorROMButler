package dev.thor.rombutler.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.backup.BackupRunState
import dev.thor.rombutler.domain.repository.LibraryArchiveIssue
import dev.thor.rombutler.domain.repository.LibraryArchiveProblem
import dev.thor.rombutler.domain.repository.LibraryBackupState
import dev.thor.rombutler.domain.repository.LibraryBiosState
import dev.thor.rombutler.domain.repository.LibraryDatState
import dev.thor.rombutler.domain.repository.LibraryReferenceIssue
import dev.thor.rombutler.domain.repository.LibraryReferenceProblem
import dev.thor.rombutler.domain.repository.LibraryReport
import dev.thor.rombutler.domain.library.VariantRecommendationReason
import dev.thor.rombutler.ui.components.formatFileSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDoctorScreen(
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    viewModel: LibraryDoctorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val duplicateState by viewModel.duplicateState.collectAsStateWithLifecycle()
    val ignoredIds by viewModel.ignoredIssueIds.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        if (state == DoctorState.Idle) viewModel.checkLibrary()
    }
    LaunchedEffect(backupState) {
        val finished = backupState as? BackupRunState.Finished ?: return@LaunchedEffect
        val message = when {
            finished.summary.errorMessage != null -> finished.summary.errorMessage
            finished.summary.cancelled -> resources.getString(R.string.doctor_backup_cancelled)
            finished.summary.failed > 0 -> resources.getString(R.string.doctor_backup_failed)
            else -> resources.getString(R.string.doctor_backup_done)
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.acknowledgeBackupAndRefresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doctor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val current = state) {
            DoctorState.Idle,
            DoctorState.Running,
            -> DoctorLoading(Modifier.padding(padding))

            is DoctorState.Failed -> DoctorFailure(
                message = current.message,
                onRetry = viewModel::checkLibrary,
                modifier = Modifier.padding(padding),
            )

            is DoctorState.Done -> DoctorReport(
                report = current.report,
                duplicateState = duplicateState,
                ignoredIds = ignoredIds,
                backupRunState = backupState,
                onIgnore = viewModel::ignoreIssue,
                onShowIgnored = viewModel::showIgnoredIssues,
                onReviewMisplaced = {
                    if (viewModel.prepareMisplacedReview(current.report)) onOpenReview()
                },
                onFindDuplicates = viewModel::findExactDuplicates,
                onStartBackup = viewModel::startBackup,
                onConfigure = onBack,
                onExport = {
                    viewModel.exportReport(
                        report = current.report,
                        exactDuplicates = (duplicateState as? DoctorDuplicateState.Done)?.report,
                    ) { success ->
                        android.widget.Toast.makeText(
                            context,
                            resources.getString(
                                if (success) R.string.doctor_export_done else R.string.doctor_export_failed,
                            ),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                onRefresh = viewModel::checkLibrary,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DoctorLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.doctor_scanning),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoctorFailure(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.size(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.doctor_retry)) }
    }
}

@Composable
private fun DoctorReport(
    report: LibraryReport,
    duplicateState: DoctorDuplicateState,
    ignoredIds: Set<String>,
    backupRunState: BackupRunState,
    onIgnore: (String) -> Unit,
    onShowIgnored: () -> Unit,
    onReviewMisplaced: () -> Unit,
    onFindDuplicates: () -> Unit,
    onStartBackup: () -> Unit,
    onConfigure: () -> Unit,
    onExport: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DoctorSummary(report, ignoredIds.size, onShowIgnored)
        }

        if (report.misplaced.isNotEmpty() && LibraryDoctorViewModel.MISPLACED_ID !in ignoredIds) {
            item {
                DoctorFindingCard(
                    icon = Icons.Filled.Folder,
                    title = pluralStringResource(
                        R.plurals.doctor_misplaced_title,
                        report.misplaced.size,
                        report.misplaced.size,
                    ),
                    message = stringResource(R.string.doctor_misplaced_text),
                    details = report.misplaced.map { it.group.primary },
                    primaryLabel = stringResource(R.string.doctor_review),
                    onPrimary = onReviewMisplaced,
                    onIgnore = { onIgnore(LibraryDoctorViewModel.MISPLACED_ID) },
                )
            }
        }

        items(
            report.referenceIssues.filter {
                LibraryDoctorViewModel.issueId(it) !in ignoredIds
            },
            key = { it.filePath },
        ) { issue ->
            ReferenceFinding(issue) {
                onIgnore(LibraryDoctorViewModel.issueId(issue))
            }
        }

        items(
            report.archiveIssues.filter {
                LibraryDoctorViewModel.issueId(it) !in ignoredIds
            },
            key = { it.filePath },
        ) { issue ->
            ArchiveFinding(issue) {
                onIgnore(LibraryDoctorViewModel.issueId(issue))
            }
        }

        if (report.duplicates.isNotEmpty() && LibraryDoctorViewModel.VARIANTS_ID !in ignoredIds) {
            item {
                DoctorFindingCard(
                    icon = Icons.Filled.ContentCopy,
                    title = pluralStringResource(
                        R.plurals.doctor_variants_title,
                        report.duplicates.size,
                        report.duplicates.size,
                    ),
                    message = stringResource(R.string.doctor_variants_text),
                    details = buildList {
                        for (group in report.duplicates.take(20)) {
                            add("${group.systemName}: ${group.title}")
                            group.recommendation?.let { recommendation ->
                                val reasons = recommendation.reasons
                                    .map { reason -> stringResource(reason.stringResourceId()) }
                                    .joinToString()
                                add(
                                    stringResource(
                                        R.string.doctor_variant_recommendation,
                                        recommendation.fileName,
                                        reasons,
                                    ),
                                )
                            }
                            group.variants.forEach { add("  $it") }
                        }
                    },
                    onIgnore = { onIgnore(LibraryDoctorViewModel.VARIANTS_ID) },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.doctor_readiness_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        item { BiosReadiness(report, onConfigure) }
        item { DatReadiness(report, onConfigure) }
        item {
            BackupReadiness(
                report = report,
                runState = backupRunState,
                onStartBackup = onStartBackup,
                onConfigure = onConfigure,
            )
        }
        item {
            DuplicateCheck(
                state = duplicateState,
                onRun = onFindDuplicates,
            )
        }
        item {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doctor_export))
            }
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doctor_retry))
            }
        }
    }
}

private fun VariantRecommendationReason.stringResourceId(): Int = when (this) {
    VariantRecommendationReason.PREFERRED_LANGUAGE -> R.string.doctor_variant_reason_language
    VariantRecommendationReason.PREFERRED_REGION -> R.string.doctor_variant_reason_region
    VariantRecommendationReason.CLEAN_DUMP -> R.string.doctor_variant_reason_clean
    VariantRecommendationReason.NEWER_REVISION -> R.string.doctor_variant_reason_revision
}

@Composable
private fun DoctorSummary(
    report: LibraryReport,
    ignoredCount: Int,
    onShowIgnored: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (report.problemCount == 0) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.HealthAndSafety
            },
            contentDescription = null,
            tint = if (report.problemCount == 0) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.doctor_summary,
                    report.totalRoms,
                    formatFileSize(report.totalBytes),
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (report.problemCount == 0) {
                    stringResource(R.string.doctor_no_problems)
                } else {
                    pluralStringResource(
                        R.plurals.doctor_problem_count,
                        report.problemCount,
                        report.problemCount,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (ignoredCount > 0) {
        TextButton(onClick = onShowIgnored) {
            Text(
                pluralStringResource(
                    R.plurals.doctor_ignored_count,
                    ignoredCount,
                    ignoredCount,
                ),
            )
        }
    }
}

@Composable
private fun ReferenceFinding(issue: LibraryReferenceIssue, onIgnore: () -> Unit) {
    val message = when (issue.problem) {
        LibraryReferenceProblem.EMPTY -> stringResource(R.string.doctor_reference_empty)
        LibraryReferenceProblem.MISSING_FILES -> stringResource(R.string.doctor_reference_missing)
        LibraryReferenceProblem.UNREADABLE -> stringResource(R.string.doctor_reference_unreadable)
    }
    val details = buildList {
        add(issue.filePath)
        issue.missingReferences.forEach {
            add(stringResource(R.string.doctor_missing_prefix, it))
        }
    }
    DoctorFindingCard(
        icon = Icons.Filled.Description,
        title = File(issue.filePath).name,
        message = message,
        details = details,
        onIgnore = onIgnore,
    )
}

@Composable
private fun ArchiveFinding(issue: LibraryArchiveIssue, onIgnore: () -> Unit) {
    DoctorFindingCard(
        icon = Icons.Filled.Storage,
        title = File(issue.filePath).name,
        message = stringResource(
            if (issue.problem == LibraryArchiveProblem.EMPTY) {
                R.string.doctor_archive_empty
            } else {
                R.string.doctor_archive_unreadable
            },
        ),
        details = listOf(issue.filePath),
        onIgnore = onIgnore,
    )
}

@Composable
private fun DoctorFindingCard(
    icon: ImageVector,
    title: String,
    message: String,
    details: List<String>,
    onIgnore: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.size(10.dp))
                details.forEach { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onPrimary != null && primaryLabel != null) {
                Spacer(Modifier.size(10.dp))
                Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                    Text(primaryLabel)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (expanded) R.string.doctor_less else R.string.doctor_details))
                }
                TextButton(onClick = onIgnore, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.doctor_ignore))
                }
            }
        }
    }
}

@Composable
private fun BiosReadiness(report: LibraryReport, onConfigure: () -> Unit) {
    val health = report.biosHealth
    val ready = health.state == LibraryBiosState.READY
    val status = when (health.state) {
        LibraryBiosState.NOT_CONFIGURED -> stringResource(R.string.doctor_bios_not_configured)
        LibraryBiosState.FOLDER_MISSING -> stringResource(R.string.doctor_bios_folder_missing)
        LibraryBiosState.NONE_DETECTED -> stringResource(R.string.doctor_bios_none)
        LibraryBiosState.READY -> pluralStringResource(
            R.plurals.doctor_bios_ready,
            health.knownFileCount,
            health.knownFileCount,
        )
    }
    DoctorReadinessCard(
        icon = Icons.Filled.HealthAndSafety,
        title = stringResource(R.string.doctor_bios_title),
        status = status,
        ready = ready,
        action = if (ready) null else stringResource(R.string.doctor_configure),
        onAction = if (ready) null else onConfigure,
    )
}

@Composable
private fun DatReadiness(report: LibraryReport, onConfigure: () -> Unit) {
    val health = report.datHealth
    val ready = health.state == LibraryDatState.READY
    val status = when (health.state) {
        LibraryDatState.NOT_CONFIGURED -> stringResource(R.string.doctor_dat_not_configured)
        LibraryDatState.FOLDER_MISSING -> stringResource(R.string.doctor_dat_folder_missing)
        LibraryDatState.NO_DAT_FILES -> stringResource(R.string.doctor_dat_none)
        LibraryDatState.NO_USABLE_ENTRIES -> stringResource(R.string.doctor_dat_unusable)
        LibraryDatState.READY -> pluralStringResource(
            R.plurals.doctor_dat_ready,
            health.datFileCount,
            health.datFileCount,
        )
    }
    DoctorReadinessCard(
        icon = Icons.AutoMirrored.Filled.FactCheck,
        title = stringResource(R.string.doctor_dat_title),
        status = status,
        ready = ready,
        action = if (ready) null else stringResource(R.string.doctor_configure),
        onAction = if (ready) null else onConfigure,
    )
}

@Composable
private fun BackupReadiness(
    report: LibraryReport,
    runState: BackupRunState,
    onStartBackup: () -> Unit,
    onConfigure: () -> Unit,
) {
    val health = report.backupHealth
    val ready = health.state == LibraryBackupState.CURRENT
    val status = when (health.state) {
        LibraryBackupState.NOT_CONFIGURED -> stringResource(R.string.doctor_backup_not_configured)
        LibraryBackupState.FOLDER_MISSING -> stringResource(R.string.doctor_backup_folder_missing)
        LibraryBackupState.NO_MANIFEST -> stringResource(R.string.doctor_backup_none)
        LibraryBackupState.DIFFERENT_LIBRARY -> stringResource(R.string.doctor_backup_different)
        LibraryBackupState.OUTDATED -> stringResource(R.string.doctor_backup_outdated)
        LibraryBackupState.CURRENT -> stringResource(R.string.doctor_backup_current)
    }
    val canRun = health.state == LibraryBackupState.NO_MANIFEST ||
        health.state == LibraryBackupState.OUTDATED ||
        health.state == LibraryBackupState.CURRENT
    val action = when {
        runState is BackupRunState.Running -> null
        canRun -> stringResource(R.string.doctor_backup_run)
        else -> stringResource(R.string.doctor_configure)
    }
    DoctorReadinessCard(
        icon = Icons.Filled.Backup,
        title = stringResource(R.string.doctor_backup_title),
        status = buildString {
            append(status)
            health.lastBackupMillis?.let { timestamp ->
                append("\n")
                append(
                    stringResource(
                        R.string.doctor_backup_last,
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(timestamp)),
                    ),
                )
            }
        },
        ready = ready,
        action = action,
        onAction = when {
            runState is BackupRunState.Running -> null
            canRun -> onStartBackup
            else -> onConfigure
        },
        progress = (runState as? BackupRunState.Running)?.progress?.fraction,
    )
}

@Composable
private fun DoctorReadinessCard(
    icon: ImageVector,
    title: String,
    status: String,
    ready: Boolean,
    action: String?,
    onAction: (() -> Unit)?,
    progress: Float? = null,
) {
    val tint = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ready) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tint)
                }
            }
            if (progress != null) {
                Spacer(Modifier.size(10.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (action != null && onAction != null) {
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun DuplicateCheck(state: DoctorDuplicateState, onRun: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.doctor_exact_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.doctor_exact_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            when (state) {
                DoctorDuplicateState.Idle -> OutlinedButton(
                    onClick = onRun,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.doctor_exact_run)) }

                DoctorDuplicateState.Running -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.doctor_exact_running))
                }

                is DoctorDuplicateState.Failed -> Column {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.doctor_exact_run))
                    }
                }

                is DoctorDuplicateState.Done -> Column {
                    Text(
                        text = if (state.report.groups.isEmpty()) {
                            stringResource(R.string.doctor_exact_none)
                        } else {
                            pluralStringResource(
                                R.plurals.doctor_exact_groups,
                                state.report.groups.size,
                                state.report.groups.size,
                                state.report.duplicateFiles,
                                formatFileSize(state.report.reclaimableBytes),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.size(6.dp))
                    OutlinedButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.doctor_exact_run))
                    }
                }
            }
        }
    }
}
