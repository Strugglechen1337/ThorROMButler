package dev.thor.rombutler.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the foreground service accompanying a backup run. Abstracted away
 * from [BackupManager] so the manager is unit-testable on the JVM.
 */
fun interface BackupServiceLauncher {
    fun launch()
}

/** Production implementation: starts [BackupService] as FGS. */
@Singleton
class AndroidBackupServiceLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackupServiceLauncher {
    override fun launch() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, BackupService::class.java),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindBackupServiceLauncher(
        impl: AndroidBackupServiceLauncher,
    ): BackupServiceLauncher
}
