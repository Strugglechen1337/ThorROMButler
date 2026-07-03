package dev.thor.rombutler

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.thor.rombutler.data.log.CrashLog
import javax.inject.Inject

/**
 * Application entry point. Annotated with [HiltAndroidApp] to trigger
 * Hilt's code generation and create the application-level DI container.
 */
@HiltAndroidApp
class ThorRomButlerApp : Application() {

    @Inject
    lateinit var crashLog: CrashLog

    override fun onCreate() {
        super.onCreate()
        // Local-only crash capture (shared manually via settings)
        crashLog.install()
    }
}
