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

/** Settings card: Folders. */
@Composable
internal fun FoldersCard(
    settings: dev.thor.rombutler.domain.model.AppSettings,
    viewModel: SettingsViewModel,
    biosSuggestion: dev.thor.rombutler.data.files.BiosFolderSuggestion?,
    setShowDownloadPicker: (Boolean) -> Unit,
    setShowRomBasePicker: (Boolean) -> Unit,
    setShowBiosPicker: (Boolean) -> Unit,
    setShowDatPicker: (Boolean) -> Unit,
) {
    SettingsCard {
        FolderRow(
            icon = Icons.Filled.Download,
            title = stringResource(R.string.setup_download_title),
            path = settings.downloadPath,
            onClick = { setShowDownloadPicker(true) },
        )
        Spacer(Modifier.size(12.dp))
        FolderRow(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.setup_rombase_title),
            path = settings.romBasePath,
            onClick = { setShowRomBasePicker(true) },
        )
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { setShowDatPicker(true) },
            ) {
                Text(
                    text = stringResource(R.string.settings_dat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = settings.datFolderPath
                        ?: stringResource(R.string.settings_dat_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (settings.datFolderPath != null) {
                IconButton(onClick = { viewModel.setDatFolderPath(null) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.settings_sources_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { setShowBiosPicker(true) },
            ) {
                Text(
                    text = stringResource(R.string.settings_bios_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = settings.biosFolderPath
                        ?: stringResource(R.string.settings_bios_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (settings.biosFolderPath != null) {
                IconButton(onClick = { viewModel.setBiosFolderPath(null) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.settings_sources_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val suggestion = biosSuggestion
        if (settings.biosFolderPath == null && suggestion != null) {
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.settings_bios_suggestion,
                            suggestion.path,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                    Text(
                        text = stringResource(
                            when {
                                suggestion.containsBios ->
                                    R.string.settings_bios_suggestion_found

                                suggestion.exists ->
                                    R.string.settings_bios_suggestion_existing

                                else -> R.string.settings_bios_suggestion_create
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { viewModel.applyBiosSuggestion(suggestion) }) {
                    Text(stringResource(R.string.settings_bios_suggestion_apply))
                }
            }
        }
    }
}

/** Settings card: Additional sources. */
@Composable
internal fun SourcesCard(
    settings: dev.thor.rombutler.domain.model.AppSettings,
    viewModel: SettingsViewModel,
    setShowSourcePicker: (Boolean) -> Unit,
) {
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
            onClick = { setShowSourcePicker(true) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_sources_add)) }
    }
}
