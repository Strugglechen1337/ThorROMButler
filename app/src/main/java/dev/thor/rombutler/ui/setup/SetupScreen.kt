package dev.thor.rombutler.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thor.rombutler.R
import dev.thor.rombutler.ui.components.FolderPickerDialog
import dev.thor.rombutler.ui.components.goldGlow
import dev.thor.rombutler.ui.components.neonGlow

/**
 * First-run setup: request the all-files permission and pick the two folders
 * (download source, ROM base). Steps unlock in order — the folder pickers
 * need file access to browse at all.
 */
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The permission is granted in the system settings app; re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermission()
    }

    var showDownloadPicker by remember { mutableStateOf(false) }
    var showRomBasePicker by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with a gently pulsing bolt (Thor motif, "dezent")
            val pulse = rememberInfiniteTransition(label = "boltPulse")
            val boltAlpha by pulse.animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "boltAlpha",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { alpha = boltAlpha },
                )
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Step 1: all-files permission
            SetupCard(
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.setup_permission_title),
                description = stringResource(R.string.setup_permission_description),
                done = state.hasAllFilesAccess,
                doneText = stringResource(R.string.setup_permission_granted),
                buttonText = stringResource(R.string.setup_permission_button),
                enabled = true,
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
            )

            // Step 2: download folder (scan source)
            SetupCard(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.setup_download_title),
                description = stringResource(R.string.setup_download_description),
                done = !state.downloadPath.isNullOrBlank(),
                doneText = state.downloadPath.orEmpty(),
                buttonText = stringResource(R.string.setup_choose_folder),
                enabled = state.hasAllFilesAccess,
                onClick = { showDownloadPicker = true },
            )

            // Step 3: ROM base folder (move target)
            SetupCard(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.setup_rombase_title),
                description = stringResource(R.string.setup_rombase_description),
                done = !state.romBasePath.isNullOrBlank(),
                doneText = state.romBasePath.orEmpty(),
                buttonText = stringResource(R.string.setup_choose_folder),
                enabled = state.hasAllFilesAccess,
                onClick = { showRomBasePicker = true },
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSetupComplete,
                enabled = state.isComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(if (state.isComplete) Modifier.goldGlow() else Modifier),
            ) {
                Text(
                    text = stringResource(R.string.setup_done_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showDownloadPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.setup_download_title),
            initialPath = state.downloadPath ?: viewModel.defaultDownloadDir(),
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
            initialPath = state.romBasePath ?: viewModel.defaultRomBaseDir(),
            onSelect = {
                viewModel.setRomBasePath(it)
                showRomBasePicker = false
            },
            onDismiss = { showRomBasePicker = false },
        )
    }
}

/**
 * One setup step as a large touch-friendly card.
 */
@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    description: String,
    done: Boolean,
    doneText: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(elevation = if (done) 10.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.setup_step_done),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (done) doneText else description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = if (done) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(text = buttonText)
            }
        }
    }
}
