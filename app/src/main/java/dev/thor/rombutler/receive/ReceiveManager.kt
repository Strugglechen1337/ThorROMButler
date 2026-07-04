package dev.thor.rombutler.receive

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thor.rombutler.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
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

    /** Starts the server. @return false when no WLAN IP / no download folder. */
    suspend fun start(): Boolean {
        if (_state.value is ReceiveState.Running) return true
        val downloadPath = settingsRepository.settings.first().downloadPath ?: return false
        val ip = localIpv4() ?: return false

        return runCatching {
            val newServer = ReceiveServer(
                port = ReceiveServer.DEFAULT_PORT,
                targetDir = File(downloadPath),
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
                url = "http://$ip:${ReceiveServer.DEFAULT_PORT}",
                receivedCount = 0,
            )
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReceiveService::class.java),
            )
            true
        }.getOrDefault(false)
    }

    fun stop() {
        server?.stop()
        server = null
        _state.value = ReceiveState.Off
    }

    /** First site-local IPv4 (the device's WLAN address). */
    private fun localIpv4(): String? =
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
}
