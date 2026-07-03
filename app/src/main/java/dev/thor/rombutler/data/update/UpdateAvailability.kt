package dev.thor.rombutler.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the result of the (opt-in) automatic update check on
 * start. The scan screen shows a badge on the settings icon while a newer
 * release is known.
 */
@Singleton
class UpdateAvailability @Inject constructor() {

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    fun publish(info: UpdateInfo?) {
        _available.value = info?.takeIf { it.isNewer }
    }
}
