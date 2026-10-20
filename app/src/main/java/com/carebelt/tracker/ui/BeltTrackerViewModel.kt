package com.carebelt.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carebelt.tracker.BuildConfig
import com.carebelt.tracker.data.BeltRepository
import com.carebelt.tracker.data.DeviceLocationFix
import com.carebelt.tracker.model.BeltTelemetry
import com.carebelt.tracker.model.ConnectionMode
import com.carebelt.tracker.model.EspConnectionLogEntry
import com.carebelt.tracker.model.GeoUtils
import com.carebelt.tracker.model.MotorVibrationLogEntry
import com.carebelt.tracker.model.NavDirection
import com.carebelt.tracker.model.RouteGuidanceState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class BeltUiState(
    val telemetry: BeltTelemetry = BeltTelemetry(
        latitude = 28.6139,
        longitude = 77.2090
    ),
    val locationHistory: List<LatLng> = listOf(LatLng(28.6139, 77.2090)),
    val connectionMode: ConnectionMode = ConnectionMode.DEMO_SIMULATOR,
    val esp32BaseUrl: String = "http://192.168.4.1",
    val pollIntervalMs: Long = 1_300L,
    val isConnected: Boolean = true,
    val connectionError: String? = null,
    val routeGuidance: RouteGuidanceState = RouteGuidanceState(),
    val followWearerOnMap: Boolean = true,
    val simulateWalkingRoute: Boolean = true,
    val hasDetectedDeviceLocation: Boolean = false,
    val deviceLocationAccuracyMeters: Float? = null,
    val cameraCenterTarget: LatLng? = null,
    val cameraCenterRequestId: Long = 0L,
    val motorVibrationLogs: List<MotorVibrationLogEntry> = emptyList(),
    val statusBannerMessage: String? = null,
    val localNetworkSummary: String = "",
    val isRunningDiagnostic: Boolean = false
)

class BeltTrackerViewModel(
    private val repository: BeltRepository = BeltRepository()
) : ViewModel() {

    private val motorLogIdGenerator = AtomicLong(1L)
    private val logTimeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _uiState = MutableStateFlow(
        BeltUiState(
            localNetworkSummary = repository.getLocalNetworkInterfacesSummary(),
            motorVibrationLogs = listOf(
                MotorVibrationLogEntry(
                    id = 0L,
                    timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                    activeMotor = NavDirection.NONE,
                    motorStatusTitle = "SYSTEM READY • ALL MOTORS IDLE",
                    guidanceInstruction = "Detecting device location... Tap anywhere on the map to set destination.",
                    coordinatesText = "Waiting for GPS lock or destination selection",
                    isSystemEvent = true
                )
            )
        )
    )
    val uiState: StateFlow<BeltUiState> = _uiState.asStateFlow()

    val connectionLogs: StateFlow<List<EspConnectionLogEntry>> = repository.connectionLogs

    private var pollingJob: Job? = null
    private var routeFetchJob: Job? = null
    private var lastAutoSentDirection: NavDirection? = null
    private var lastAutoNavTimestampMs: Long = 0L

    init {
        startTelemetryPolling()
    }

    private fun formatLogTimestamp(): String = synchronized(logTimeFormatter) {
        logTimeFormatter.format(Date())
    }

    private fun appendMotorLog(
        activeMotor: NavDirection,
        motorStatusTitle: String,
        guidanceInstruction: String,
        coordinatesText: String,
        remainingDistanceMeters: Int? = null,
        stepProgressText: String? = null,
        isArrivalEvent: Boolean = false,
        isSystemEvent: Boolean = false
    ) {
        val entry = MotorVibrationLogEntry(
            id = motorLogIdGenerator.getAndIncrement(),
            timestamp = formatLogTimestamp(),
            activeMotor = activeMotor,
            motorStatusTitle = motorStatusTitle,
            guidanceInstruction = guidanceInstruction,
            coordinatesText = coordinatesText,
            remainingDistanceMeters = remainingDistanceMeters,
            stepProgressText = stepProgressText,
            isArrivalEvent = isArrivalEvent,
            isSystemEvent = isSystemEvent
        )
        _uiState.update { current ->
            current.copy(
                motorVibrationLogs = (listOf(entry) + current.motorVibrationLogs).take(100)
            )
        }
    }

    fun clearMotorVibrationLogs() {
        _uiState.update { current ->
            current.copy(motorVibrationLogs = emptyList())
        }
        appendMotorLog(
            activeMotor = _uiState.value.telemetry.navCommand,
            motorStatusTitle = "LOGS CLEARED • ${
                if (_uiState.value.telemetry.navCommand == NavDirection.NONE) {
                    "ALL MOTORS OFF"
                } else {
                    "${_uiState.value.telemetry.navCommand.motorName} VIBRATING"
                }
            }",
            guidanceInstruction = "Tap map to set a destination and watch live motor & tracking logs",
            coordinatesText = String.format(
                Locale.US,
                "Pos: %.5f, %.5f",
                _uiState.value.telemetry.latitude,
                _uiState.value.telemetry.longitude
            ),
            isSystemEvent = true
        )
    }

    /**
     * Called when the Android device's own GPS / Network location is detected or updated.
     */
    fun onDeviceLocationFix(fix: DeviceLocationFix, userRequestedSnap: Boolean = false) {
        val currentState = _uiState.value
        val isActivelySimWalking = currentState.connectionMode == ConnectionMode.DEMO_SIMULATOR &&
            currentState.simulateWalkingRoute &&
            currentState.routeGuidance.destination != null &&
            !currentState.routeGuidance.hasArrived

        // If user explicitly tapped "Detect Current Location", or this is our first GPS fix,
        // or we are not currently mid-auto-walk, update the device's position to the real GPS fix
        if (userRequestedSnap || !currentState.hasDetectedDeviceLocation || !isActivelySimWalking) {
            val isFirstFix = !currentState.hasDetectedDeviceLocation
            repository.setSimulatedDeviceLocation(fix.latLng, fix.bearingDegrees)

            val updatedTelemetry = currentState.telemetry.copy(
                latitude = fix.latLng.latitude,
                longitude = fix.latLng.longitude,
                altitudeMeters = fix.altitudeMeters,
                speedKmh = fix.speedKmh,
                speedKnots = fix.speedKmh / 1.852,
                courseDegrees = if (fix.bearingDegrees > 0.0) {
                    fix.bearingDegrees
                } else {
                    currentState.telemetry.courseDegrees
                },
                hasValidGpsFix = true,
                timestampMillis = System.currentTimeMillis()
            )

            // If user explicitly requested location snap while no destination is active, reset trail
            if (userRequestedSnap || isFirstFix) {
                _uiState.update { state ->
                    state.copy(
                        telemetry = updatedTelemetry,
                        locationHistory = if (state.routeGuidance.destination == null) {
                            listOf(fix.latLng)
                        } else {
                            (state.locationHistory + fix.latLng).takeLast(80)
                        },
                        hasDetectedDeviceLocation = true,
                        deviceLocationAccuracyMeters = fix.accuracyMeters,
                        cameraCenterTarget = fix.latLng,
                        cameraCenterRequestId = state.cameraCenterRequestId + 1L,
                        statusBannerMessage = "Current device location detected (±${fix.accuracyMeters.roundToInt()}m)"
                    )
                }

                appendMotorLog(
                    activeMotor = updatedTelemetry.navCommand,
                    motorStatusTitle = "CURRENT DEVICE LOCATION DETECTED",
                    guidanceInstruction = "Source location set from device GPS (${fix.provider.uppercase()}, ±${fix.accuracyMeters.roundToInt()}m)",
                    coordinatesText = String.format(
                        Locale.US,
                        "Source: %.6f, %.6f",
                        fix.latLng.latitude,
                        fix.latLng.longitude
                    ),
                    isSystemEvent = true
                )
            } else if (currentState.connectionMode == ConnectionMode.DEMO_SIMULATOR &&
                !currentState.simulateWalkingRoute &&
                currentState.routeGuidance.destination != null
            ) {
                // Live phone GPS walk mode: evaluate route progress using real device movement
                applyNewTelemetry(updatedTelemetry)
            } else {
                _uiState.update { state ->
                    state.copy(
                        telemetry = updatedTelemetry,
                        hasDetectedDeviceLocation = true,
                        deviceLocationAccuracyMeters = fix.accuracyMeters
                    )
                }
            }
        }
    }

    private fun startTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                try {
                    when (state.connectionMode) {
                        ConnectionMode.DEMO_SIMULATOR -> {
                            val hasActiveDestination = state.routeGuidance.destination != null &&
                                !state.routeGuidance.hasArrived &&
                                !state.routeGuidance.isFetchingRoute
                            if (state.simulateWalkingRoute && hasActiveDestination) {
                                val simulated = repository.nextSimulatedTelemetry(
                                    activeRoutePoints = state.routeGuidance.routePoints,
                                    walkAlongRoute = true
                                )
                                applyNewTelemetry(simulated)
                            }
                        }

                        ConnectionMode.ESP32_HTTP -> {
                            val result = repository.fetchEsp32Telemetry(state.esp32BaseUrl)
                            result.fold(
                                onSuccess = { liveTelemetry ->
                                    applyNewTelemetry(liveTelemetry)
                                },
                                onFailure = { error ->
                                    if (error is CancellationException) throw error
                                    val netSummary = repository.getLocalNetworkInterfacesSummary()
                                    _uiState.update { current ->
                                        current.copy(
                                            isConnected = false,
                                            localNetworkSummary = netSummary,
                                            connectionError = "Cannot reach ${current.esp32BaseUrl}/data (${error.javaClass.simpleName}: ${error.localizedMessage ?: "Timeout"})"
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
        val currentState = _uiState.value
        val wasAlreadyArrived = currentState.routeGuidance.hasArrived

        val updatedGuidance = if (currentState.routeGuidance.destination != null) {
            GeoUtils.evaluateRouteProgress(
                currentLocation = newTelemetry.latLng,
                currentCourseDeg = newTelemetry.courseDegrees,
                previousState = currentState.routeGuidance
            )
        } else {
            currentState.routeGuidance
        }

        val activeMotorForState = if (updatedGuidance.destination != null && updatedGuidance.isAutoGuidanceEnabled) {
            if (updatedGuidance.hasArrived) NavDirection.NONE else updatedGuidance.recommendedDirection
        } else {
            newTelemetry.navCommand
        }

        val telemetryWithActiveMotor = newTelemetry.copy(navCommand = activeMotorForState)

        _uiState.update { current ->
            val updatedTrail = if (telemetryWithActiveMotor.hasValidGpsFix) {
                (current.locationHistory + telemetryWithActiveMotor.latLng).takeLast(100)
            } else {
                current.locationHistory
            }
            current.copy(
                telemetry = telemetryWithActiveMotor,
                locationHistory = updatedTrail,
                isConnected = true,
                connectionError = null,
                routeGuidance = updatedGuidance
            )
        }

        // Log continuous location tracking and motor vibration state in the bottom panel
        if (updatedGuidance.destination != null) {
            if (updatedGuidance.hasArrived && !wasAlreadyArrived) {
                appendMotorLog(
                    activeMotor = NavDirection.NONE,
                    motorStatusTitle = "DESTINATION REACHED • ALL MOTORS STOPPED",
                    guidanceInstruction = "Device arrived at destination! Left, Front & Right motors turned OFF.",
                    coordinatesText = String.format(
                        Locale.US,
                        "Arrived: %.6f, %.6f",
                        telemetryWithActiveMotor.latitude,
                        telemetryWithActiveMotor.longitude
                    ),
                    remainingDistanceMeters = 0,
                    stepProgressText = "Arrived",
                    isArrivalEvent = true
                )
            } else if (!updatedGuidance.hasArrived) {
                val totalPoints = updatedGuidance.routePoints.lastIndex.coerceAtLeast(1)
                val currentIdx = updatedGuidance.nextWaypointIndex.coerceIn(1, totalPoints)
                appendMotorLog(
                    activeMotor = activeMotorForState,
                    motorStatusTitle = "${activeMotorForState.motorName} (${activeMotorForState.motorPinLabel}) VIBRATING",
                    guidanceInstruction = updatedGuidance.guidanceHeadline,
                    coordinatesText = String.format(
                        Locale.US,
                        "Tracked: %.6f, %.6f • Hdg: %.0f°",
                        telemetryWithActiveMotor.latitude,
                        telemetryWithActiveMotor.longitude,
                        telemetryWithActiveMotor.courseDegrees
                    ),
                    remainingDistanceMeters = updatedGuidance.totalDistanceRemainingMeters.roundToInt(),
                    stepProgressText = "Step $currentIdx/$totalPoints"
                )
            }
        }

        // Automatically send tactile steering cue (`/navigate?dir=left|right|forward|none`) along the route
        if (updatedGuidance.destination != null && updatedGuidance.isAutoGuidanceEnabled) {
            val now = System.currentTimeMillis()
            val desiredDir = if (updatedGuidance.hasArrived) {
                NavDirection.NONE
            } else {
                updatedGuidance.recommendedDirection
            }

            if (desiredDir != lastAutoSentDirection || (now - lastAutoNavTimestampMs > 6_000L && !updatedGuidance.hasArrived)) {
                lastAutoSentDirection = desiredDir
                lastAutoNavTimestampMs = now
                sendNavigationCommand(desiredDir, isAutoTriggered = true)
            }
        }
    }

    /**
     * Runs a 4-step network & HTTP diagnostic against the configured ESP32 URL
     * and logs every step to both Logcat (`CareBeltESP32`) and the in-app ESP32 Logs viewer.
     */
    fun runEsp32Diagnostic() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningDiagnostic = true,
                    localNetworkSummary = repository.getLocalNetworkInterfacesSummary()
                )
            }
            try {
                val summary = repository.runEsp32Diagnostic(_uiState.value.esp32BaseUrl)
                _uiState.update {
                    it.copy(
                        isRunningDiagnostic = false,
                        localNetworkSummary = repository.getLocalNetworkInterfacesSummary(),
                        statusBannerMessage = summary
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRunningDiagnostic = false,
                        statusBannerMessage = "Diagnostic error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearEsp32Logs() {
        repository.clearConnectionLogs()
        _uiState.update {
            it.copy(localNetworkSummary = repository.getLocalNetworkInterfacesSummary())
        }
    }

    /**
     * Called when the user taps a location on the Google Map to set the destination.
     * Generates a route from the device's current location (source) to [destination]
     * and starts continuous tracking + motor vibration logging until destination is reached.
     */
    fun onMapDestinationSelected(destination: LatLng) {
        routeFetchJob?.cancel()
        val origin = _uiState.value.telemetry.latLng
        repository.setSimulatedDeviceLocation(origin)
        repository.resetSimulatedRouteProgress()
        lastAutoSentDirection = null

        val fallbackPath = GeoUtils.buildFallbackStreetRoute(origin, destination)
        val initialPoints = GeoUtils.densifyRouteAdaptive(fallbackPath, targetSteps = 20)
        val initialEval = GeoUtils.evaluateRouteProgress(
            currentLocation = origin,
            currentCourseDeg = _uiState.value.telemetry.courseDegrees,
            previousState = RouteGuidanceState(
                sourceLocation = origin,
                destination = destination,
                routePoints = initialPoints,
                isFetchingRoute = true,
                isAutoGuidanceEnabled = true,
                nextWaypointIndex = 1
            )
        )

        _uiState.update {
            it.copy(
                locationHistory = listOf(origin),
                followWearerOnMap = true,
                routeGuidance = initialEval,
                statusBannerMessage = "Generating route from current location to destination..."
            )
        }

        appendMotorLog(
            activeMotor = initialEval.recommendedDirection,
            motorStatusTitle = "ROUTE CREATED • STARTING TRACKING",
            guidanceInstruction = "Source → Destination (${initialEval.totalDistanceRemainingMeters.roundToInt()}m) • ${initialEval.recommendedDirection.motorName} active",
            coordinatesText = String.format(
                Locale.US,
                "Src: %.5f, %.5f → Dest: %.5f, %.5f",
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude
            ),
            remainingDistanceMeters = initialEval.totalDistanceRemainingMeters.roundToInt(),
            isSystemEvent = true
        )

        routeFetchJob = viewModelScope.launch {
            try {
                val walkingRoute = repository.fetchWalkingRoute(
                    origin = origin,
                    destination = destination,
                    googleMapsApiKey = BuildConfig.MAPS_API_KEY
                )
                repository.setSimulatedDeviceLocation(origin)
                repository.resetSimulatedRouteProgress()

                val currentTelemetry = _uiState.value.telemetry
                val evaluated = GeoUtils.evaluateRouteProgress(
                    currentLocation = origin,
                    currentCourseDeg = currentTelemetry.courseDegrees,
                    previousState = _uiState.value.routeGuidance.copy(
                        sourceLocation = origin,
                        destination = destination,
                        routePoints = walkingRoute,
                        isFetchingRoute = false,
                        nextWaypointIndex = 1
                    )
                )
                _uiState.update {
                    it.copy(
                        routeGuidance = evaluated,
                        statusBannerMessage = "Route ready (${evaluated.totalDistanceRemainingMeters.toInt()}m) • Tracking device to destination"
                    )
                }
                sendNavigationCommand(evaluated.recommendedDirection, isAutoTriggered = true)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        routeGuidance = state.routeGuidance.copy(isFetchingRoute = false),
                        statusBannerMessage = "Tracking along generated route to destination"
                    )
                }
            }
        }
    }

    /**
     * Re-starts the demo simulation from the route's source location to the destination.
     */
    fun recalculateRoute() {
        val currentGuidance = _uiState.value.routeGuidance
        val dest = currentGuidance.destination ?: return
        val source = currentGuidance.sourceLocation ?: _uiState.value.telemetry.latLng
        repository.setSimulatedDeviceLocation(source)
        _uiState.update { state ->
            state.copy(
                telemetry = state.telemetry.copy(
                    latitude = source.latitude,
                    longitude = source.longitude
                ),
                locationHistory = listOf(source),
                cameraCenterTarget = source,
                cameraCenterRequestId = state.cameraCenterRequestId + 1L
            )
        }
        onMapDestinationSelected(dest)
    }

    fun clearDestinationAndStopGuidance() {
        routeFetchJob?.cancel()
        lastAutoSentDirection = null
        _uiState.update {
            it.copy(
                routeGuidance = RouteGuidanceState(),
                statusBannerMessage = "Destination cleared • Stopping belt motors"
            )
        }
        appendMotorLog(
            activeMotor = NavDirection.NONE,
            motorStatusTitle = "ROUTE CLEARED • ALL MOTORS STOPPED",
            guidanceInstruction = "Navigation stopped by user. Tap map to select a new destination.",
            coordinatesText = String.format(
                Locale.US,
                "Pos: %.5f, %.5f",
                _uiState.value.telemetry.latitude,
                _uiState.value.telemetry.longitude
            ),
            isSystemEvent = true
        )
        sendNavigationCommand(NavDirection.NONE, isAutoTriggered = false)
    }

    fun toggleAutoGuidance(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                routeGuidance = state.routeGuidance.copy(isAutoGuidanceEnabled = enabled),
                statusBannerMessage = if (enabled) {
                    "Auto belt route guidance enabled"
                } else {
                    "Auto guidance paused (use manual buttons below)"
                }
            )
        }
        if (!enabled) {
            sendNavigationCommand(NavDirection.NONE, isAutoTriggered = false)
        }
    }

    fun toggleSimulateWalkingRoute() {
        val next = !_uiState.value.simulateWalkingRoute
        _uiState.update {
            it.copy(
                simulateWalkingRoute = next,
                statusBannerMessage = if (next) {
                    "Auto-Sim Walk ON: Continuously tracking along route to destination"
                } else {
                    "Live Phone GPS Mode: Move physically with device to track along route"
                }
            )
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _uiState.update {
            it.copy(
                connectionMode = mode,
                localNetworkSummary = repository.getLocalNetworkInterfacesSummary(),
                connectionError = null,
                statusBannerMessage = "Switched to ${mode.label}"
            )
        }
    }

    fun updateEsp32Config(baseUrl: String, mode: ConnectionMode) {
        val normalized = repository.normalizeBaseUrl(baseUrl)
        _uiState.update {
            it.copy(
                esp32BaseUrl = normalized,
                connectionMode = mode,
                localNetworkSummary = repository.getLocalNetworkInterfacesSummary(),
                connectionError = null,
                statusBannerMessage = "Updated belt endpoint: $normalized/data"
            )
        }
        if (mode == ConnectionMode.ESP32_HTTP) {
            runEsp32Diagnostic()
        }
    }

    fun toggleFollowWearer(follow: Boolean) {
        _uiState.update { it.copy(followWearerOnMap = follow) }
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

    fun sendNavigationCommand(
        direction: NavDirection,
        isAutoTriggered: Boolean = false
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val isSim = state.connectionMode == ConnectionMode.DEMO_SIMULATOR
            val result = repository.sendNavigationCommand(
                baseUrl = state.esp32BaseUrl,
                direction = direction,
                isSimulator = isSim
            )
            result.fold(
                onSuccess = { confirmedDir ->
                    val prefix = if (isAutoTriggered) "[Auto-Guide] " else "[Manual] "
                    _uiState.update { current ->
                        current.copy(
                            telemetry = current.telemetry.copy(navCommand = confirmedDir),
                            statusBannerMessage = "$prefix${confirmedDir.motorDescription}"
                        )
                    }
                    if (!isAutoTriggered) {
                        appendMotorLog(
                            activeMotor = confirmedDir,
                            motorStatusTitle = "[MANUAL] ${confirmedDir.motorName} (${confirmedDir.motorPinLabel})",
                            guidanceInstruction = "${confirmedDir.title} — ${confirmedDir.motorDescription}",
                            coordinatesText = String.format(
                                Locale.US,
                                "Pos: %.6f, %.6f",
                                state.telemetry.latitude,
                                state.telemetry.longitude
                            )
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "/navigate?dir=${direction.dirParam} failed: ${err.localizedMessage ?: "ESP32 unreachable"}"
                        )
                    }
                }
            )
        }
    }
}
