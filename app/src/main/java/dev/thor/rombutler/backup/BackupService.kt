package dev.thor.rombutler.backup

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
 * Foreground service keeping the process alive during a backup/restore run
 * and mirroring the [BackupManager] progress into a notification with a
 * cancel action — the same glue pattern as the extraction service.
 */
@AndroidEntryPoint
class BackupService : Service() {

    @Inject
    lateinit var manager: BackupManager

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
                        is BackupRunState.Running -> {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(NOTIFICATION_ID, buildNotification(state.progress))
                        }

                        is BackupRunState.Finished, BackupRunState.Idle -> {
                            ServiceCompat.stopForeground(
                                this@BackupService,
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

    /** Android 15+ calls this when the shared data-sync quota is exhausted. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.cancel()
        stopSelf()
    }

    private fun buildNotification(progress: BackupProgress?): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BackupService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (progress?.mode) {
            BackupMode.RESTORE -> getString(R.string.notification_restore_title)
            else -> getString(R.string.notification_backup_title)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentTitle(title)
            .setContentText(
                progress?.let {
                    getString(
                        R.string.backup_progress,
                        it.copiedFiles,
                        it.totalFiles,
                        it.currentName,
                    )
                } ?: getString(R.string.backup_preparing),
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
            getString(R.string.notification_channel_backup),
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "backup"
        private const val NOTIFICATION_ID = 4
        const val ACTION_CANCEL = "dev.thor.rombutler.action.CANCEL_BACKUP"
    }
}
