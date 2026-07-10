package dev.thor.rombutler

import android.annotation.SuppressLint
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

private fun android.content.Intent.sharedUris(): List<android.net.Uri> = when (action) {
    android.content.Intent.ACTION_SEND ->
        listOfNotNull(getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java))

    android.content.Intent.ACTION_SEND_MULTIPLE ->
        getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            .orEmpty()

    else -> emptyList()
}

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
        handleShareIntent(intent)
        setContent {
            val themeId by viewModel.themeId.collectAsStateWithLifecycle()
            ThorRomButlerTheme(themeId = themeId) {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                // Wait for DataStore before building the graph — avoids a
                // visible flash from Setup to Scan on configured devices.
                startDestination?.let { destination ->
                    AppNavHost(
                        navController = rememberNavController(),
                        startDestination = destination,
                    )
                }

                // Toast after taking over shared files
                val shareResult by viewModel.shareResult.collectAsStateWithLifecycle()
                shareResult?.let { count ->
                    android.widget.Toast.makeText(
                        this,
                        resources.getQuantityString(R.plurals.share_received, count, count),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    viewModel.consumeShareResult()
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

    private fun handleShareIntent(intent: android.content.Intent?) {
        intent?.sharedUris()?.takeIf { it.isNotEmpty() }?.let(viewModel::handleSharedUris)
    }

    /**
     * Gamepad mapping (additive to touch): A confirms like DPAD_CENTER,
     * B navigates back. Everything else (D-pad, stick as D-pad) already
     * drives Compose focus traversal natively.
     */
    // ComponentActivity restricts this callback for library callers, but an
    // Activity override is the only point where BUTTON_A can be remapped
    // before Compose performs focus activation. This is exercised on hardware.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A ->
                return super.dispatchKeyEvent(
                    android.view.KeyEvent(
                        event.action,
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    ),
                )

            android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == android.view.KeyEvent.ACTION_UP) {
                    onBackPressedDispatcher.onBackPressed()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
