package dev.thor.rombutler.receive

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile toggling the LAN receive mode without opening the
 * app. Active state shows the upload URL as subtitle so the user can type
 * it into the PC browser straight from the notification shade.
 *
 * Starting the foreground service from here is permitted: a Quick Settings
 * click counts as a user interaction, which exempts the app from
 * background-start restrictions.
 */
@AndroidEntryPoint
class ReceiveTileService : TileService() {

    @Inject
    lateinit var manager: ReceiveManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob = serviceScope.launch {
            manager.state.collect { render(it) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        when (manager.state.value) {
            is ReceiveState.Running -> manager.stop()
            ReceiveState.Off -> serviceScope.launch {
                if (
                    Build.VERSION.SDK_INT >= LocalNetworkPermission.ANDROID_17_API_LEVEL &&
                    !LocalNetworkPermission.isGranted(this@ReceiveTileService)
                ) {
                    requestLocalNetworkPermission()
                    return@launch
                }
                // start() returns false without Wi-Fi or download folder;
                // the tile then simply stays inactive.
                if (!manager.start()) render(ReceiveState.Off)
            }
        }
    }

    private fun render(state: ReceiveState) {
        val tile = qsTile ?: return
        when (state) {
            is ReceiveState.Running -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = state.url.removePrefix("http://")
            }

            ReceiveState.Off -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = null
            }
        }
        tile.updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestLocalNetworkPermission() {
        val intent = Intent(this, ReceivePermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(pendingIntent)
    }
}
