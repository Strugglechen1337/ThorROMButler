package dev.thor.rombutler.extraction

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
 * Foreground service that keeps the process alive during an extraction run
 * and mirrors the [ExtractionManager] progress into a notification with a
 * cancel action. The actual work happens in the manager — this service is
 * pure lifecycle/notification glue.
 */
@AndroidEntryPoint
class ExtractionService : Service() {

    @Inject
    lateinit var manager: ExtractionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            manager.cancel()
            return START_NOT_STICKY
        }

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(progress = null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (observeJob == null) {
            observeJob = scope.launch {
                manager.state.collect { state ->
                    when (state) {
                        is ExtractionRunState.Running -> {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(NOTIFICATION_ID, buildNotification(state.progress))
                        }

                        is ExtractionRunState.Finished, ExtractionRunState.Idle -> {
                            ServiceCompat.stopForeground(
                                this@ExtractionService,
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

    private fun buildNotification(progress: ExtractionProgress?): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ExtractionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentTitle(getString(R.string.notification_extract_title))
            .setContentText(
                progress?.let {
                    getString(
                        R.string.review_extract_progress,
                        it.currentIndex,
                        it.totalCount,
                        it.currentName,
                    )
                } ?: getString(R.string.review_moving),
            )
            .setProgress(1000, ((progress?.fraction ?: 0f) * 1000).toInt(), progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.action_cancel), cancelIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_extract),
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "extraction"
        private const val NOTIFICATION_ID = 1
        const val ACTION_CANCEL = "dev.thor.rombutler.action.CANCEL_EXTRACTION"
    }
}
