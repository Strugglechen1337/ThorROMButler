package dev.thor.rombutler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.thor.rombutler.ui.navigation.AppNavHost
import dev.thor.rombutler.ui.theme.ThorRomButlerTheme

/**
 * Single activity hosting the whole Compose UI.
 * Navigation between screens is handled inside Compose (see ui/navigation).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThorRomButlerTheme {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                // Wait for DataStore before building the graph — avoids a
                // visible flash from Setup to Scan on configured devices.
                startDestination?.let { destination ->
                    AppNavHost(
                        navController = rememberNavController(),
                        startDestination = destination,
                    )
                }

                // One-time what's-new dialog after an update
                val whatsNew by viewModel.whatsNewVersion.collectAsStateWithLifecycle()
                whatsNew?.let { version ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = viewModel::dismissWhatsNew,
                        title = {
                            androidx.compose.material3.Text(
                                text = getString(R.string.whatsnew_title, version),
                            )
                        },
                        text = {
                            androidx.compose.material3.Text(
                                text = getString(R.string.whatsnew_body),
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = viewModel::dismissWhatsNew,
                            ) {
                                androidx.compose.material3.Text(getString(R.string.action_close))
                            }
                        },
                    )
                }
            }
        }
    }
}
