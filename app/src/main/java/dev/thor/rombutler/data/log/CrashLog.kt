package dev.thor.rombutler.data.log

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-friendly local crash log: uncaught exceptions are appended to an
 * app-internal file — nothing leaves the device unless the user explicitly
 * shares it from the settings screen (for a GitHub issue).
 */
@Singleton
class CrashLog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** Installs the handler; delegates to the previous one afterwards. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.appendText("=== $stamp (${thread.name}) ===\n")
                file.appendText(throwable.stackTraceToString() + "\n")
                trim()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun exists(): Boolean = file.isFile && file.length() > 0

    /** Last portion of the crash log for sharing. */
    fun read(): String? {
        if (!exists()) return null
        val text = file.readText()
        return if (text.length > MAX_SHARE_CHARS) text.takeLast(MAX_SHARE_CHARS) else text
    }

    fun clear() {
        file.delete()
    }

    private fun trim() {
        if (file.length() > MAX_FILE_BYTES) {
            file.writeText(file.readText().takeLast(MAX_SHARE_CHARS))
        }
    }

    private companion object {
        const val FILE_NAME = "crash_log.txt"
        const val MAX_FILE_BYTES = 256L * 1024
        const val MAX_SHARE_CHARS = 100_000
    }
}
