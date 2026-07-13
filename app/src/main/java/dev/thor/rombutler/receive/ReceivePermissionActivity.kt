package dev.thor.rombutler.receive

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.thor.rombutler.R
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Requests LAN access when receive mode is started from the Quick Settings tile. */
@AndroidEntryPoint
class ReceivePermissionActivity : ComponentActivity() {

    @Inject
    lateinit var receiveManager: ReceiveManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startReceive()
        } else {
            Toast.makeText(this, R.string.receive_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LocalNetworkPermission.isGranted(this)) {
            startReceive()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    private fun startReceive() {
        lifecycleScope.launch {
            if (!receiveManager.start()) {
                Toast.makeText(
                    this@ReceivePermissionActivity,
                    R.string.receive_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }
}
