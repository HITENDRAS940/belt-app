package com.carebelt.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carebelt.tracker.data.BeltRepository
import com.carebelt.tracker.model.BeltTelemetry
import com.carebelt.tracker.model.ConnectionMode
import com.carebelt.tracker.model.GeoUtils
import com.carebelt.tracker.model.SafeZoneConfig
import com.carebelt.tracker.model.VibrationPattern
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BeltUiState(
    val telemetry: BeltTelemetry = BeltTelemetry(
        latitude = 28.6139,
        longitude = 77.2090
    ),
    val locationHistory: List<LatLng> = listOf(LatLng(28.6139, 77.2090)),
    val connectionMode: ConnectionMode = ConnectionMode.DEMO_SIMULATOR,
    val esp32BaseUrl: String = "http://192.168.4.1",
    val pollIntervalMs: Long = 2_000L,
    val isConnected: Boolean = true,
    val connectionError: String? = null,
    val safeZone: SafeZoneConfig = SafeZoneConfig(),
    val distanceFromHomeMeters: Double = 0.0,
    val bearingToHomeDeg: Int = 0,
    val bearingToHomeCardinal: String = "N",
    val isOutsideSafeZone: Boolean = false,
    val followWearerOnMap: Boolean = true,
    val isSimulatingWandering: Boolean = false,
    val statusBannerMessage: String? = null
)

class BeltTrackerViewModel(
    private val repository: BeltRepository = BeltRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BeltUiState())
    val uiState: StateFlow<BeltUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var lastAutoVibrateTimestampMs: Long = 0L

    init {
        startTelemetryPolling()
    }

    private fun startTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                try {
                    when (state.connectionMode) {
                        ConnectionMode.DEMO_SIMULATOR -> {
                            val simulated = repository.nextSimulatedTelemetry(
                                state.safeZone.homeLocation
                            )
                            applyNewTelemetry(simulated)
                        }

                        ConnectionMode.ESP32_HTTP -> {
                            val result = repository.fetchEsp32Telemetry(state.esp32BaseUrl)
                            result.fold(
                                onSuccess = { liveTelemetry ->
                                    applyNewTelemetry(liveTelemetry)
                                },
                                onFailure = { error ->
                                    if (error is CancellationException) throw error
                                    _uiState.update { current ->
                                        current.copy(
                                            isConnected = false,
                                            connectionError = "Unable to reach ESP32 at ${current.esp32BaseUrl} (${error.localizedMessage ?: "Timeout"})"
                                        )
                                    }
                                }
                            )
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isConnected = false,
                            connectionError = e.localizedMessage ?: "Unexpected telemetry error"
                        )
                    }
                }
                delay(_uiState.value.pollIntervalMs)
            }
        }
    }

    private fun applyNewTelemetry(newTelemetry: BeltTelemetry) {
        val currentSafeZone = _uiState.value.safeZone
        val newLatLng = newTelemetry.latLng
        val distanceMeters = GeoUtils.distanceMeters(currentSafeZone.homeLocation, newLatLng)
        val (bearingDeg, cardinal) = GeoUtils.bearingToHome(newLatLng, currentSafeZone.homeLocation)
        val outsideSafeZone = currentSafeZone.isEnabled && distanceMeters > currentSafeZone.radiusMeters

        _uiState.update { current ->
            val updatedTrail = (current.locationHistory + newLatLng).takeLast(60)
            current.copy(
                telemetry = newTelemetry,
                locationHistory = updatedTrail,
                isConnected = true,
                connectionError = null,
                distanceFromHomeMeters = distanceMeters,
                bearingToHomeDeg = bearingDeg,
                bearingToHomeCardinal = cardinal,
                isOutsideSafeZone = outsideSafeZone
            )
        }

        // Automatically vibrate belt when patient crosses outside the Safe Zone (cooldown: 20s)
        val now = System.currentTimeMillis()
        if (outsideSafeZone &&
            currentSafeZone.autoVibrateOnExit &&
            now - lastAutoVibrateTimestampMs > 20_000L
        ) {
            lastAutoVibrateTimestampMs = now
            triggerBeltVibration(VibrationPattern.BOUNDARY_ALERT, isAutoTriggered = true)
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _uiState.update {
            it.copy(
                connectionMode = mode,
                connectionError = null,
                statusBannerMessage = "Switched to ${mode.label}"
            )
        }
    }

    fun updateEsp32Config(baseUrl: String, mode: ConnectionMode) {
        _uiState.update {
            it.copy(
                esp32BaseUrl = baseUrl.trim(),
                connectionMode = mode,
                connectionError = null,
                statusBannerMessage = "Updated belt source: ${mode.label}"
            )
        }
    }

    fun toggleFollowWearer(follow: Boolean) {
        _uiState.update { it.copy(followWearerOnMap = follow) }
    }

    fun toggleSimulateWandering() {
        val nextValue = !repository.isSimulatingWandering()
        repository.setSimulateWandering(nextValue)
        _uiState.update {
            it.copy(
                isSimulatingWandering = nextValue,
                statusBannerMessage = if (nextValue) {
                    "Simulating patient wandering outside Safe Zone..."
                } else {
                    "Simulating patient walking safely near Home"
                }
            )
        }
    }

    fun setSafeZoneHomeToCurrentBeltLocation() {
        val currentLocation = _uiState.value.telemetry.latLng
        _uiState.update { state ->
            state.copy(
                safeZone = state.safeZone.copy(homeLocation = currentLocation),
                locationHistory = listOf(currentLocation),
                distanceFromHomeMeters = 0.0,
                isOutsideSafeZone = false,
                statusBannerMessage = "Safe Zone Home updated to belt's current GPS position"
            )
        }
    }

    fun updateSafeZoneSettings(
        radiusMeters: Double,
        isEnabled: Boolean,
        autoVibrateOnExit: Boolean
    ) {
        _uiState.update { state ->
            val updatedZone = state.safeZone.copy(
                radiusMeters = radiusMeters,
                isEnabled = isEnabled,
                autoVibrateOnExit = autoVibrateOnExit
            )
            val outside = updatedZone.isEnabled &&
                state.distanceFromHomeMeters > updatedZone.radiusMeters
            state.copy(
                safeZone = updatedZone,
                isOutsideSafeZone = outside,
                statusBannerMessage = "Safe Zone updated (${radiusMeters.toInt()}m radius)"
            )
        }
    }

    fun clearTrailHistory() {
        _uiState.update { state ->
            state.copy(
                locationHistory = listOf(state.telemetry.latLng),
                statusBannerMessage = "Movement trail cleared"
            )
        }
    }

    fun dismissBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun triggerBeltVibration(
        pattern: VibrationPattern,
        isAutoTriggered: Boolean = false
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val isSim = state.connectionMode == ConnectionMode.DEMO_SIMULATOR
            val result = repository.sendVibrationCommand(
                baseUrl = state.esp32BaseUrl,
                pattern = pattern,
                isSimulator = isSim
            )
            result.fold(
                onSuccess = { msg ->
                    val prefix = if (isAutoTriggered) "[Auto Geofence] " else ""
                    _uiState.update {
                        it.copy(statusBannerMessage = "$prefix$msg")
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "Vibration command failed: ${err.localizedMessage ?: "ESP32 unreachable"}"
                        )
                    }
                }
            )
        }
    }
}
