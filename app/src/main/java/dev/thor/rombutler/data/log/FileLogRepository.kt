package dev.thor.rombutler.data.log

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.di.IoDispatcher
import dev.thor.rombutler.domain.model.LogEntry
import dev.thor.rombutler.domain.model.LogLevel
import dev.thor.rombutler.domain.model.UndoInfo
import dev.thor.rombutler.domain.model.UndoKind
import dev.thor.rombutler.domain.repository.LogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LogRepository] persisting to a plain text file in app-internal storage
 * (`filesDir/action_log.txt`), one entry per line:
 * `<epochMillis>\t<LEVEL>\t<message>` — newlines in messages are escaped.
 */
@Singleton
class FileLogRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LogRepository {

    private val logFile: File get() = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    /** Lines currently in the file (guarded by [mutex] after start-up). */
    private var fileLines = 0
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    override val entries: StateFlow<List<LogEntry>> = _entries

    init {
        // A corrupt/unreadable log must never crash the app at start-up
        scope.launch { _entries.value = runCatching { load() }.getOrDefault(emptyList()) }
    }

    override suspend fun append(level: LogLevel, message: String, undo: UndoInfo?) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val entry = LogEntry(
                    timestampMillis = System.currentTimeMillis(),
                    level = level,
                    message = message,
                    undo = undo,
                )
                val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
                // Disk full / IO error: keep the in-memory entry, never crash the caller.
                // Rotation is lazy: the file may grow by ROTATE_SLACK lines before it is
                // rewritten, so a long sorting run does not rewrite the log per ROM.
                runCatching {
                    logFile.appendText(entry.toLine() + "\n")
                    fileLines++
                    if (fileLines > MAX_ENTRIES + ROTATE_SLACK) {
                        rewrite(updated)
                        fileLines = updated.size
                    }
                }
                _entries.value = updated
            }
        }

    override suspend fun markUndone(entry: LogEntry) = withContext(ioDispatcher) {
        mutex.withLock {
            val updated = _entries.value.map {
                if (it.timestampMillis == entry.timestampMillis && it.message == entry.message) {
                    it.copy(undone = true)
                } else {
                    it
                }
            }
            rewrite(updated)
            fileLines = updated.size
            _entries.value = updated
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        mutex.withLock {
            logFile.delete()
            fileLines = 0
            _entries.value = emptyList()
        }
    }

    private fun rewrite(entries: List<LogEntry>) {
        logFile.writeText(
            entries.asReversed().joinToString("\n", postfix = "\n") { it.toLine() },
        )
    }

    private fun load(): List<LogEntry> {
        if (!logFile.isFile) return emptyList()
        val lines = logFile.readLines()
        fileLines = lines.size
        return lines
            .mapNotNull { it.toEntryOrNull() }
            .asReversed() // file is oldest-first, UI wants newest-first
            .take(MAX_ENTRIES)
    }

    companion object {
        private const val FILE_NAME = "action_log.txt"

        /** Rotation cap — the log must not grow unbounded. */
        private const val MAX_ENTRIES = 500

        /** Extra lines tolerated in the file before it is compacted. */
        private const val ROTATE_SLACK = 200

        // Line format v2: ts \t level \t undone(0/1) \t undoJson("-" = none) \t message
        // (message last because it is the only free-text field). v1 lines
        // (ts \t level \t message) are still parsed for existing logs.
        private fun LogEntry.toLine(): String {
            val json = undo?.let { info ->
                org.json.JSONObject()
                    .put("kind", info.kind.name)
                    .put("created", org.json.JSONArray(info.createdFiles))
                    .put("restore", org.json.JSONArray(info.restoreTo))
                    .putOpt("archive", info.sourceArchivePath)
                    .toString()
            } ?: "-"
            return "$timestampMillis\t$level\t${if (undone) 1 else 0}\t$json\t" +
                message.replace("\n", "\\n")
        }

        private fun String.toEntryOrNull(): LogEntry? {
            val parts = split('\t', limit = 5)
            val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val level = parts.getOrNull(1)
                ?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: return null
            return when (parts.size) {
                3 -> LogEntry(timestamp, level, parts[2].replace("\\n", "\n"))
                5 -> LogEntry(
                    timestampMillis = timestamp,
                    level = level,
                    message = parts[4].replace("\\n", "\n"),
                    undone = parts[2] == "1",
                    undo = parts[3].takeIf { it != "-" }?.let { json ->
                        runCatching {
                            val obj = org.json.JSONObject(json)
                            UndoInfo(
                                kind = UndoKind.valueOf(obj.getString("kind")),
                                createdFiles = obj.getJSONArray("created").toStringList(),
                                restoreTo = obj.getJSONArray("restore").toStringList(),
                                sourceArchivePath = obj.optString("archive").takeIf { it.isNotEmpty() },
                            )
                        }.getOrNull()
                    },
                )

                else -> null
            }
        }

        private fun org.json.JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }
    }
}
