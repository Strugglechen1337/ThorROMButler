package dev.thor.rombutler.extraction

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
 * Starts the foreground service accompanying an extraction run. Abstracted
 * away from [ExtractionManager] so the manager is unit-testable on the JVM.
 */
fun interface ExtractionServiceLauncher {
    fun launch()
}

/** Production implementation: starts [ExtractionService] as FGS. */
@Singleton
class AndroidExtractionServiceLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExtractionServiceLauncher {
    override fun launch() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ExtractionService::class.java),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractionModule {

    @Binds
    @Singleton
    abstract fun bindServiceLauncher(
        impl: AndroidExtractionServiceLauncher,
    ): ExtractionServiceLauncher
}
