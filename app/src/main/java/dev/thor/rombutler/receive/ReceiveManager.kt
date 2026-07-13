package dev.thor.rombutler.receive

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.Inet4Address
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** State of the LAN receive mode. */
sealed interface ReceiveState {
    data object Off : ReceiveState

    /** Server running: open [url] in a browser on the PC. */
    data class Running(val url: String, val receivedCount: Int) : ReceiveState
}

/**
 * Owns the LAN receive server: files uploaded from a PC browser land in
 * the download folder, where the normal scan flow picks them up. Runs
 * alongside [ReceiveService] so the process stays alive.
 */
@Singleton
class ReceiveManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val _state = MutableStateFlow<ReceiveState>(ReceiveState.Off)
    val state: StateFlow<ReceiveState> = _state.asStateFlow()

    private var server: ReceiveServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timeoutJob: Job? = null

    /** Starts the server. @return false when no WLAN IP / no download folder. */
    suspend fun start(): Boolean {
        if (_state.value is ReceiveState.Running) return true
        if (!LocalNetworkPermission.isGranted(context)) return false
        val downloadPath = settingsRepository.settings.first().downloadPath ?: return false
        val ip = localIpv4() ?: return false
        val sessionToken = newSessionToken()

        return runCatching {
            val newServer = ReceiveServer(
                port = ReceiveServer.DEFAULT_PORT,
                targetDir = File(downloadPath),
                sessionToken = sessionToken,
                onFileReceived = {
                    _state.update { current ->
                        if (current is ReceiveState.Running) {
                            current.copy(receivedCount = current.receivedCount + 1)
                        } else {
                            current
                        }
                    }
                },
            )
            newServer.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            _state.value = ReceiveState.Running(
                url = "http://$ip:${ReceiveServer.DEFAULT_PORT}/$sessionToken/",
                receivedCount = 0,
            )
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(SESSION_TIMEOUT_MILLIS)
                stop()
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReceiveService::class.java),
            )
            true
        }.getOrDefault(false)
    }

    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        server?.stop()
        server = null
        _state.value = ReceiveState.Off
    }

    /** Site-local IPv4 of Android's active network (normally Wi-Fi). */
    private fun localIpv4(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return null
        return connectivity.getLinkProperties(network)?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }

    private fun newSessionToken(): String = buildString(SESSION_TOKEN_LENGTH) {
        repeat(SESSION_TOKEN_LENGTH) {
            append(SESSION_TOKEN_ALPHABET[secureRandom.nextInt(SESSION_TOKEN_ALPHABET.length)])
        }
    }

    private companion object {
        const val SESSION_TIMEOUT_MILLIS = 30L * 60 * 1000
        const val SESSION_TOKEN_LENGTH = 6
        const val SESSION_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val secureRandom = SecureRandom()
    }
}
