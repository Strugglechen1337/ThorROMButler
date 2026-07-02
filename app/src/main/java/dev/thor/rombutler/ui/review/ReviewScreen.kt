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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.ui.components.ConfidenceChip
import dev.thor.rombutler.ui.components.SystemPickerDialog
import dev.thor.rombutler.ui.components.formatFileSize
import dev.thor.rombutler.ui.components.goldGlow
import dev.thor.rombutler.ui.components.neonGlow

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

    // Move run finished -> hand over to the log screen
    LaunchedEffect(state.moveSummary) {
        if (state.moveSummary != null) {
            viewModel.consumeMoveSummary()
            onMoved()
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            ReviewBottomBar(
                state = state,
                onCreateFolders = viewModel::createMissingFolders,
                onMove = viewModel::moveAssigned,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                ReviewItemCard(
                    modifier = Modifier.animateItem(),
                    item = item,
                    onPickSystem = { pickerItemId = item.id },
                    onAcceptSuggestion = {
                        item.rom.detection.system?.let { viewModel.selectSystem(item.id, it.id) }
                    },
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
            if (state.blockedArchiveCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.review_blocked_hint, state.blockedArchiveCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
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
            val movableCount = state.movableArchivePaths.size
            if (movableCount > 0 || state.moving) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onMove,
                    enabled = !state.moving && !state.creatingFolders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .then(if (!state.moving) Modifier.goldGlow() else Modifier),
                ) {
                    if (state.moving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.review_moving))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.review_move_button,
                                movableCount,
                                movableCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
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
            Text(
                text = if (members > 1) {
                    stringResource(
                        R.string.review_source_with_members,
                        item.archiveFileName,
                        members,
                        formatFileSize(item.rom.totalSizeBytes),
                    )
                } else {
                    stringResource(
                        R.string.review_source,
                        item.archiveFileName,
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
            )
        }
    }
}

@Composable
private fun TargetRow(
    item: ReviewItem,
    onPickSystem: () -> Unit,
    onAcceptSuggestion: () -> Unit,
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
