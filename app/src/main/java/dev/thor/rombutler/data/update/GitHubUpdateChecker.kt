package dev.thor.rombutler.data.update

import dev.thor.rombutler.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a manual update check.
 *
 * @property latestVersion version of the newest GitHub release (no "v").
 * @property releaseUrl browser URL of that release.
 * @property isNewer true when the release is newer than the installed app.
 */
data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val isNewer: Boolean,
)

/**
 * Manual update check against the GitHub Releases API. This is the ONLY
 * network access of the app and happens exclusively on user request.
 */
@Singleton
class GitHubUpdateChecker @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Fetches the latest release and compares it to [currentVersion]
     * (the installed versionName, e.g. "0.1.0").
     */
    suspend fun check(currentVersion: String): Result<UpdateInfo> = withContext(ioDispatcher) {
        runCatching {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val body = try {
                if (connection.responseCode != 200) {
                    throw java.io.IOException("GitHub antwortete mit HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val json = JSONObject(body)
            val tag = json.getString("tag_name").removePrefix("v")
            UpdateInfo(
                latestVersion = tag,
                releaseUrl = json.optString("html_url", RELEASES_URL),
                isNewer = isNewerVersion(tag, currentVersion.removePrefix("v")),
            )
        }
    }

    companion object {
        const val REPO_OWNER = "REPLACE_ME_OWNER" // set when the repo exists
        const val REPO_NAME = "ThorRomButtler"
        private const val API_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        const val RELEASES_URL =
            "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

        /** Numeric dot-segment comparison: "0.2.0" > "0.1.9" > "0.1". */
        fun isNewerVersion(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
