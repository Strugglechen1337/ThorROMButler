package dev.thor.rombutler.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.domain.model.ArchiveAnalysis
import dev.thor.rombutler.domain.model.ArchiveType
import dev.thor.rombutler.domain.model.DetectedRom
import dev.thor.rombutler.ui.components.ConfidenceChip
import dev.thor.rombutler.ui.components.formatDate
import dev.thor.rombutler.ui.components.formatFileSize
import dev.thor.rombutler.ui.components.goldGlow
import dev.thor.rombutler.ui.components.neonGlow

/**
 * Lists all archive candidates found in the download folder as cards.
 * The detection/review flow starts from here (M4/M5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onOpenSetup: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenLog: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh when coming back (moved archives must disappear); the guard
    // in rescanIfIdle prevents a double scan on first composition.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.rescanIfIdle()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val found = state as? ScanUiState.Found
            if (found != null && found.hasReviewableRoms) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Button(
                        onClick = { if (viewModel.prepareReview()) onOpenReview() },
                        enabled = found.analysisComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(52.dp)
                            .then(if (found.analysisComplete) Modifier.goldGlow() else Modifier),
                    ) {
                        Text(
                            text = if (found.analysisComplete) {
                                stringResource(R.string.scan_to_review)
                            } else {
                                stringResource(R.string.scan_analyzing_wait)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.scan_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::rescan) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.scan_refresh),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onOpenLog) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.log_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSetup) {
                        // Gold dot when the opt-in start-up check found an update
                        val update by viewModel.updateAvailable.collectAsStateWithLifecycle()
                        BadgedBox(
                            badge = {
                                if (update != null) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.scan_open_setup),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        // Crossfade between scanning / empty / results
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            contentKey = { it::class },
            label = "scanState",
        ) { s ->
            when (s) {
                ScanUiState.Scanning -> ScanningIndicator(Modifier.padding(innerPadding))
                ScanUiState.Empty -> EmptyState(Modifier.padding(innerPadding))
                is ScanUiState.Found -> ArchiveList(
                    items = s.items,
                    looseRoms = s.looseRoms,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun ScanningIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.scan_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.scan_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.scan_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArchiveList(
    items: List<ArchiveListItem>,
    looseRoms: List<DetectedRom>,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.scan_found_count, items.size + looseRoms.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (looseRoms.isNotEmpty()) {
            item(key = "loose") {
                LooseRomsCard(looseRoms, modifier = Modifier.animateItem())
            }
        }
        items(items, key = { it.archive.path }) { item ->
            ArchiveCard(item, modifier = Modifier.animateItem())
        }
    }
}

/** Unarchived ROM files found directly in the download folder. */
@Composable
private fun LooseRomsCard(looseRoms: List<DetectedRom>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .neonGlow(elevation = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.scan_loose_title, looseRoms.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (rom in looseRoms.take(MAX_ROM_ROWS)) {
                    DetectedRomRow(rom)
                }
                val more = looseRoms.size - MAX_ROM_ROWS
                if (more > 0) {
                    Text(
                        text = stringResource(R.string.analysis_more_roms, more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One archive as a card: format badge, file name, size, date and the
 * detection results as soon as the analysis finished.
 */
@Composable
private fun ArchiveCard(item: ArchiveListItem, modifier: Modifier = Modifier) {
    val archive = item.archive
    Card(
        modifier = modifier
            .fillMaxWidth()
            .neonGlow(elevation = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(archive.type)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = archive.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatFileSize(archive.sizeBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDate(archive.lastModifiedMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnalysisSection(item.analysis)
        }
    }
}

/** Analysis part of the card: progress, results, warning or error. */
@Composable
private fun AnalysisSection(analysis: ArchiveAnalysis?) {
    Spacer(Modifier.size(10.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.size(10.dp))

    when (analysis) {
        null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.analysis_running),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is ArchiveAnalysis.Unsupported -> IconTextRow(
            icon = Icons.Filled.Warning,
            text = stringResource(R.string.scan_rar5_unsupported),
            tint = MaterialTheme.colorScheme.secondary,
        )

        is ArchiveAnalysis.Failed -> IconTextRow(
            icon = Icons.Filled.Warning,
            text = stringResource(R.string.analysis_failed, analysis.message),
            tint = MaterialTheme.colorScheme.error,
        )

        is ArchiveAnalysis.Success -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (analysis.roms.isEmpty()) {
                Text(
                    text = if (analysis.otherExtensions.isEmpty()) {
                        stringResource(R.string.analysis_no_roms)
                    } else {
                        // Show WHAT was in there so "no ROMs" is explainable
                        // (e.g. .adf = Amiga, not on the v0.1 system list)
                        stringResource(
                            R.string.analysis_no_roms_with_types,
                            analysis.otherExtensions.joinToString(", ") { ".$it" },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (rom in analysis.roms.take(MAX_ROM_ROWS)) {
                    DetectedRomRow(rom)
                }
                val more = analysis.roms.size - MAX_ROM_ROWS
                if (more > 0) {
                    Text(
                        text = stringResource(R.string.analysis_more_roms, more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (analysis.ignoredBiosCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.analysis_bios_ignored,
                        analysis.ignoredBiosCount,
                        analysis.ignoredBiosCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetectedRomRow(rom: DetectedRom) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = rom.group.primary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        rom.detection.system?.let { system ->
            Text(
                text = system.esdeFolder,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        ConfidenceChip(rom.detection.confidence)
    }
}

@Composable
private fun IconTextRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

private const val MAX_ROM_ROWS = 4

/** Small rounded chip showing the container format. */
@Composable
private fun TypeBadge(type: ArchiveType) {
    val (container, content) = if (type.supported) {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Text(
        text = type.displayName,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .background(color = container, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
