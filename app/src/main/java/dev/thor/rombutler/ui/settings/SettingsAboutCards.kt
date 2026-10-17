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

/** Settings card: Theme. */
@Composable
internal fun ThemeCard(
    settings: dev.thor.rombutler.domain.model.AppSettings,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(10.dp))
        // Three per row so five themes stay readable on narrow phones
        for (rowThemes in dev.thor.rombutler.ui.theme.AppThemes.ALL.chunked(3)) {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            for (theme in rowThemes) {
                val selected = settings.themeId == theme
                val label = when (theme) {
                    dev.thor.rombutler.ui.theme.AppThemes.ODIN -> "Odin"
                    dev.thor.rombutler.ui.theme.AppThemes.CRT -> "CRT"
                    dev.thor.rombutler.ui.theme.AppThemes.RAGNAROK -> "Ragnarök"
                    dev.thor.rombutler.ui.theme.AppThemes.AURORA -> "Aurora"
                    else -> "Thor"
                }
                if (selected) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.setThemeId(theme) },
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                }
                if (theme != rowThemes.last()) {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        }
    }
}

/** Settings card: Diagnostics. */
@Composable
internal fun DiagnosticsCard(
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    SettingsCard {
        SectionHeader(
            title = stringResource(R.string.settings_diagnostics_title),
            hint = stringResource(R.string.settings_diagnostics_hint),
            icon = Icons.Filled.BugReport,
        )
        if (viewModel.hasCrashReport) {
            Text(
                text = stringResource(R.string.settings_diagnostics_crash_included),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = {
                viewModel.buildDiagnosticReport(
                    onReady = { text ->
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Thor ROM Butler diagnostics")
                            .putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onFailed = {
                        android.widget.Toast.makeText(
                            context,
                            resources.getString(R.string.settings_diagnostics_failed),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_diagnostics_share))
        }
    }
}

/** Settings card: Update check. */
@Composable
internal fun UpdateCard(
    viewModel: SettingsViewModel,
    updateState: UpdateCheckState,
) {
    val context = LocalContext.current
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
}

/** Settings card: Support. */
@Composable
internal fun SupportCard(
) {
    val context = LocalContext.current
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
