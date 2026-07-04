package dev.thor.rombutler.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.ui.components.ConfidenceChip
import dev.thor.rombutler.ui.components.SystemPickerDialog
import dev.thor.rombutler.ui.components.formatFileSize
import dev.thor.rombutler.ui.components.goldGlow
import dev.thor.rombutler.ui.components.neonGlow
import java.io.File

/**
 * Review screen: the user confirms or corrects the system assignment for
 * every detected ROM. Missing target folders can be created from here.
 * Moving the files is M6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onMoved: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Existing installs never ran the (new) setup notification prompt —
    // ask here so the extraction progress notification is visible.
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Which item's system picker is open (null = none)
    var pickerItemId by remember { mutableStateOf<String?>(null) }

    val folderResultText = state.folderResult?.let { result ->
        if (result.failed == 0) {
            stringResource(R.string.review_folders_created, result.created)
        } else {
            stringResource(R.string.review_folders_partly_failed, result.created, result.failed)
        }
    }
    LaunchedEffect(folderResultText) {
        if (folderResultText != null) {
            snackbarHostState.showSnackbar(folderResultText)
            viewModel.consumeFolderResult()
        }
    }

    val moveFailureText = state.moveSummary?.takeIf { it.failed > 0 }?.let { summary ->
        state.lastFailureMessage ?: stringResource(R.string.review_move_failed_many, summary.failed)
    }
    // Successful runs go to the log. Failed items stay on review for retry.
    LaunchedEffect(state.moveSummary, moveFailureText) {
        val summary = state.moveSummary
        if (summary != null && summary.failed == 0) {
            viewModel.consumeMoveSummary()
            onMoved()
        } else if (summary != null) {
            snackbarHostState.showSnackbar(moveFailureText.orEmpty())
            viewModel.consumeMoveSummary()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Confirm all PROBABLE suggestions with one tap
                    if (state.openSuggestionCount > 0) {
                        TextButton(onClick = viewModel::acceptAllSuggestions) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    R.string.review_accept_all,
                                    state.openSuggestionCount,
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            ReviewBottomBar(
                state = state,
                onCreateFolders = viewModel::createMissingFolders,
                onMove = viewModel::extractAssigned,
                onCancel = viewModel::cancelExtraction,
            )
        },
    ) { innerPadding ->
        // Adaptive grid: two+ columns on wide/landscape screens (AYN Thor!)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "legend", span = { GridItemSpan(maxLineSpan) }) {
                // One-line legend for the confidence colors
                Text(
                    text = stringResource(R.string.review_legend),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(state.items, key = { it.id }) { item ->
                ReviewItemCard(
                    modifier = Modifier.animateItem(),
                    item = item,
                    onPickSystem = { pickerItemId = item.id },
                    onAcceptSuggestion = {
                        item.rom.detection.system?.let { viewModel.selectSystem(item.id, it.id) }
                    },
                    onSetOverwrite = { viewModel.setOverwrite(item.id, it) },
                )
            }
        }
    }

    pickerItemId?.let { itemId ->
        SystemPickerDialog(
            systems = viewModel.registry.systems,
            onSelect = { system ->
                viewModel.selectSystem(itemId, system.id)
                pickerItemId = null
            },
            onDismiss = { pickerItemId = null },
        )
    }
}

@Composable
private fun ReviewBottomBar(
    state: ReviewUiState,
    onCreateFolders: () -> Unit,
    onMove: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.review_assigned_count,
                    state.assignedCount,
                    state.items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.missingFolderCount > 0 || state.creatingFolders) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCreateFolders,
                    enabled = !state.creatingFolders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (state.creatingFolders) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.review_create_folders))
                    }
                }
            }
            val movableCount = state.processableCount
            if (state.moving) {
                // Live progress: bar (byte-based) + "ROM x von y: name"
                Spacer(Modifier.height(12.dp))
                val progress = state.progress
                LinearProgressIndicator(
                    progress = { progress?.fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (progress != null) {
                            stringResource(
                                R.string.review_extract_progress,
                                progress.currentIndex,
                                progress.totalCount,
                                progress.currentName,
                            )
                        } else {
                            stringResource(R.string.review_moving)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            } else if (movableCount > 0) {
                Spacer(Modifier.height(10.dp))
                val preflight = computePreflight(state)
                PreflightHint(preflight)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onMove,
                    enabled = !state.creatingFolders && !preflight.insufficient,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .goldGlow(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.retryAvailable) {
                            pluralStringResource(
                                R.plurals.review_retry_failed_button,
                                movableCount,
                                movableCount,
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.review_move_button,
                                movableCount,
                                movableCount,
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreflightHint(preflight: Preflight) {
    Text(
        text = if (preflight.freeBytes != null) {
            stringResource(
                R.string.review_preflight_storage,
                formatFileSize(preflight.requiredBytes),
                formatFileSize(preflight.freeBytes),
            )
        } else {
            stringResource(
                R.string.review_preflight_storage_unknown,
                formatFileSize(preflight.requiredBytes),
            )
        },
        style = MaterialTheme.typography.labelMedium,
        // Red when the run cannot fit — the numbers alone are easy to miss
        color = if (preflight.insufficient) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    if (preflight.insufficient) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.review_preflight_insufficient),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (preflight.hasSevenZ) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.review_preflight_7z_warning),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** Aggregated storage preflight of all processable items. */
private data class Preflight(
    val requiredBytes: Long,
    val freeBytes: Long?,
    val hasSevenZ: Boolean,
) {
    /** True when the run cannot fit (64-MB margin, like the extractor). */
    val insufficient: Boolean
        get() = freeBytes != null && requiredBytes + 64L * 1024 * 1024 > freeBytes
}

private fun computePreflight(state: ReviewUiState): Preflight {
    val processableItems = state.items.filter {
        it.selectedSystemId != null && (!it.targetOccupied || it.overwrite)
    }
    return Preflight(
        requiredBytes = processableItems.sumOf { it.rom.totalSizeBytes.coerceAtLeast(0L) },
        freeBytes = processableItems
            .mapNotNull { it.targetPath }
            .distinct()
            .mapNotNull { usableSpaceForPath(it) }
            .filter { it > 0L }
            .minOrNull(),
        hasSevenZ = processableItems.any { item ->
            (item.source as? RomSource.ArchiveEntry)?.archiveType == ArchiveType.SEVEN_ZIP
        },
    )
}

/**
 * One ROM group awaiting assignment. Layout:
 * name + confidence, source archive, then the target row
 * (assigned folder / suggestion / manual pick).
 */
@Composable
private fun ReviewItemCard(
    item: ReviewItem,
    onPickSystem: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onSetOverwrite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Assigned cards glow neon, open decisions stay visually calm
    Card(
        modifier = modifier
            .fillMaxWidth()
            .neonGlow(elevation = if (item.selectedSystemId != null) 8.dp else 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.rom.group.primary,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ConfidenceChip(item.rom.detection.confidence)
            }

            Spacer(Modifier.height(4.dp))
            val members = item.rom.group.members.size
            val sourceName = when (val source = item.source) {
                is RomSource.ArchiveEntry -> source.archiveFileName
                is RomSource.LooseFiles -> stringResource(R.string.review_source_loose)
                is RomSource.WholeArchive -> stringResource(R.string.review_source_whole)
            }
            Text(
                text = if (members > 1) {
                    stringResource(
                        R.string.review_source_with_members,
                        sourceName,
                        members,
                        formatFileSize(item.rom.totalSizeBytes),
                    )
                } else {
                    stringResource(
                        R.string.review_source,
                        sourceName,
                        formatFileSize(item.rom.totalSizeBytes),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            TargetRow(
                item = item,
                onPickSystem = onPickSystem,
                onAcceptSuggestion = onAcceptSuggestion,
                onSetOverwrite = onSetOverwrite,
            )
        }
    }
}

@Composable
private fun TargetRow(
    item: ReviewItem,
    onPickSystem: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onSetOverwrite: (Boolean) -> Unit,
) {
    val selected = item.selectedSystemId != null
    val suggestion = item.rom.detection.system

    when {
        // Assigned: show target folder (+ "will be created" hint)
        selected -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.targetPath.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPickSystem) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.review_change_system),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (item.targetExists == false) {
                Text(
                    text = stringResource(R.string.review_folder_missing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (item.targetOccupied) {
                // Duplicate at the target: user must explicitly opt in
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.overwrite) {
                            stringResource(R.string.review_duplicate_replace)
                        } else {
                            stringResource(R.string.review_duplicate_skip)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.overwrite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = item.overwrite,
                        onCheckedChange = onSetOverwrite,
                    )
                }
            }
        }

        // Probable suggestion: user must actively accept it
        suggestion != null && item.rom.detection.confidence == Confidence.PROBABLE -> Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onAcceptSuggestion, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.review_accept_suggestion, suggestion.displayName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onPickSystem) {
                Text(stringResource(R.string.review_pick_other))
            }
        }

        // Unknown: manual pick only
        else -> OutlinedButton(
            onClick = onPickSystem,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.review_pick_system))
        }
    }
}

private fun usableSpaceForPath(path: String): Long? {
    var file: File? = File(path)
    while (file != null && !file.exists()) {
        file = file.parentFile
    }
    return file?.usableSpace?.takeIf { it > 0L }
}
