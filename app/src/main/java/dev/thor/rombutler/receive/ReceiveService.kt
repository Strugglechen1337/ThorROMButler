package dev.thor.rombutler.receive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.thor.rombutler.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service keeping the process alive while the LAN receive
 * server runs; the notification shows the URL and a stop action.
 */
@AndroidEntryPoint
class ReceiveService : Service() {

    @Inject
    lateinit var manager: ReceiveManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            manager.stop()
            return START_NOT_STICKY
        }
        createChannel()
        val current = manager.state.value
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification((current as? ReceiveState.Running)?.url.orEmpty(), 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (observeJob == null) {
            observeJob = scope.launch {
                manager.state.collect { state ->
                    when (state) {
                        is ReceiveState.Running -> {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(
                                NOTIFICATION_ID,
                                buildNotification(state.url, state.receivedCount),
                            )
                        }

                        ReceiveState.Off -> {
                            ServiceCompat.stopForeground(
                                this@ReceiveService,
                                ServiceCompat.STOP_FOREGROUND_REMOVE,
                            )
                            stopSelf()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(url: String, received: Int): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ReceiveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentTitle(getString(R.string.notification_receive_title))
            .setContentText(
                if (received > 0) {
                    getString(R.string.notification_receive_text_count, url, received)
                } else {
                    url
                },
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.receive_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_receive),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "receive"
        private const val NOTIFICATION_ID = 3
        const val ACTION_STOP = "dev.thor.rombutler.action.STOP_RECEIVE"
    }
}
