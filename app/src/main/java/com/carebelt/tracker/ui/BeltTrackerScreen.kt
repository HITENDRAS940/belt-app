package com.carebelt.tracker.ui

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carebelt.tracker.BuildConfig
import com.carebelt.tracker.data.DeviceLocationTracker
import com.carebelt.tracker.model.ConnectionMode
import com.carebelt.tracker.model.MotorVibrationLogEntry
import com.carebelt.tracker.model.NavDirection
import com.carebelt.tracker.model.RouteGuidanceState
import com.carebelt.tracker.model.SafetyUrgency
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeltTrackerScreen(
    viewModel: BeltTrackerViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionLogs by viewModel.connectionLogs.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val deviceLocationTracker = remember(context) {
        DeviceLocationTracker(context)
    }
    var hasLocationPermission by remember {
        mutableStateOf(deviceLocationTracker.hasLocationPermission())
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted || deviceLocationTracker.hasLocationPermission()
        if (hasLocationPermission) {
            deviceLocationTracker.getBestLastKnownLocation()?.let { fix ->
                viewModel.onDeviceLocationFix(fix, userRequestedSnap = true)
            }
            deviceLocationTracker.startContinuousTracking { fix ->
                viewModel.onDeviceLocationFix(fix, userRequestedSnap = false)
            }
        }
    }

    // Request device location permission on startup and begin continuous location tracking
    LaunchedEffect(Unit) {
        if (deviceLocationTracker.hasLocationPermission()) {
            hasLocationPermission = true
            deviceLocationTracker.getBestLastKnownLocation()?.let { fix ->
                viewModel.onDeviceLocationFix(fix, userRequestedSnap = true)
            }
            deviceLocationTracker.startContinuousTracking { fix ->
                viewModel.onDeviceLocationFix(fix, userRequestedSnap = false)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            deviceLocationTracker.stopContinuousTracking()
        }
    }

    // Trigger subtle phone haptic feedback when simulated belt motor is vibrating
    LaunchedEffect(uiState.telemetry.timestampMillis, uiState.telemetry.navCommand) {
        if (uiState.telemetry.navCommand != NavDirection.NONE) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(VibratorManager::class.java)
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
    }

    var showConnectionDialog by rememberSaveable { mutableStateOf(false) }
    var showGpsDebugPage by rememberSaveable { mutableStateOf(false) }
    var debugInitialMode by rememberSaveable { mutableStateOf(DebugDisplayMode.ESP_LOGS) }
    var showManualMotorControls by rememberSaveable { mutableStateOf(false) }

    if (showGpsDebugPage) {
        GpsDebugScreen(
            uiState = uiState,
            connectionLogs = connectionLogs,
            initialMode = debugInitialMode,
            onRunDiagnostic = { viewModel.runEsp32Diagnostic() },
            onClearLogs = { viewModel.clearEsp32Logs() },
            onBackToMap = { showGpsDebugPage = false }
        )
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.telemetry.latLng, 17.2f)
    }

    // Center camera when device location is newly detected or user taps "Detect Location"
    LaunchedEffect(uiState.cameraCenterRequestId) {
        val target = uiState.cameraCenterTarget ?: return@LaunchedEffect
        if (uiState.cameraCenterRequestId > 0L) {
            runCatching {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(target, 17.5f),
                    durationMs = 700
                )
            }
        }
    }

    // Continuously follow the device coordinates on the map as it moves along the route
    LaunchedEffect(uiState.telemetry.latLng, uiState.followWearerOnMap) {
        if (uiState.followWearerOnMap) {
            runCatching {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLng(uiState.telemetry.latLng),
                    durationMs = 550
                )
            }
        }
    }

    // Auto-dismiss transient status toast messages after 4 seconds
    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(4000)
            viewModel.dismissBannerMessage()
        }
    }

    val detectCurrentDeviceLocation: () -> Unit = {
        if (deviceLocationTracker.hasLocationPermission()) {
            hasLocationPermission = true
            val immediateFix = deviceLocationTracker.getBestLastKnownLocation()
            if (immediateFix != null) {
                viewModel.onDeviceLocationFix(immediateFix, userRequestedSnap = true)
            } else {
                coroutineScope.launch {
                    runCatching {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(uiState.telemetry.latLng, 17.5f)
                        )
                    }
                }
            }
            deviceLocationTracker.startContinuousTracking { fix ->
                viewModel.onDeviceLocationFix(fix, userRequestedSnap = false)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val isPlaceholderApiKey = BuildConfig.MAPS_API_KEY.isBlank() ||
        BuildConfig.MAPS_API_KEY == "YOUR_GOOGLE_MAPS_API_KEY_HERE"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CareBelt Route Navigator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.hasDetectedDeviceLocation) {
                                "Device GPS Active • ${uiState.telemetry.satellites} sats • Fix: YES"
                            } else {
                                "${uiState.connectionMode.label} • ${uiState.telemetry.satellites} sats"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    UrgencyBadge(urgency = uiState.telemetry.urgency)

                    IconButton(onClick = detectCurrentDeviceLocation) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Detect Current Device Location",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = { showGpsDebugPage = true }) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "NEO-6M GPS Debug Page"
                        )
                    }

                    IconButton(onClick = { showConnectionDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "ESP32 Connection Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isPlaceholderApiKey) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set MAPS_API_KEY in local.properties to load Google Maps tiles.",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // SOS Emergency Button Alert Banner
            AnimatedVisibility(visible = uiState.telemetry.sosActive) {
                Surface(
                    color = Color(0xFFB91C1C),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "SOS Emergency",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SOS EMERGENCY ACTIVE — Patient pressed the belt SOS button!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Belt Clasp Unbuckled Warning Banner
            AnimatedVisibility(visible = !uiState.telemetry.isBeltFastened) {
                Surface(
                    color = Color(0xFFD97706),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Belt Unbuckled",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BELT UNBUCKLED — Clasp sensor reports beltWorn = false",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Live Route & Turn-by-Turn Guidance Header Card
            RouteGuidanceBanner(
                routeGuidance = uiState.routeGuidance,
                onRecalculateRoute = { viewModel.recalculateRoute() },
                onClearDestination = { viewModel.clearDestinationAndStopGuidance() }
            )

            // Connection error banner when polling real ESP32
            AnimatedVisibility(visible = uiState.connectionError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = uiState.connectionError.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = {
                                debugInitialMode = DebugDisplayMode.ESP_LOGS
                                showGpsDebugPage = true
                            }
                        ) {
                            Text("ESP Logs", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                viewModel.setConnectionMode(ConnectionMode.DEMO_SIMULATOR)
                            }
                        ) {
                            Text("Use Demo", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Map Container (Tap anywhere on the map to set the Destination!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val patientMarkerState = remember(uiState.telemetry.latLng) {
                    MarkerState(position = uiState.telemetry.latLng)
                }
                val sourceLatLng = uiState.routeGuidance.sourceLocation
                val sourceMarkerState = remember(sourceLatLng) {
                    sourceLatLng?.let { MarkerState(position = it) }
                }
                val destinationLatLng = uiState.routeGuidance.destination
                val destinationMarkerState = remember(destinationLatLng) {
                    destinationLatLng?.let { MarkerState(position = it) }
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapType = MapType.NORMAL,
                        isBuildingEnabled = true,
                        isMyLocationEnabled = hasLocationPermission
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = true,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = false
                    ),
                    onMapClick = { tappedLatLng ->
                        viewModel.onMapDestinationSelected(tappedLatLng)
                    }
                ) {
                    // 1. Full Generated Route Polyline (Source -> Destination)
                    if (uiState.routeGuidance.routePoints.size >= 2) {
                        Polyline(
                            points = uiState.routeGuidance.routePoints,
                            color = Color(0x550F766E),
                            width = 10f
                        )

                        // Active Remaining Route from Current Tracked Device Location -> Destination
                        val remainingPoints = uiState.routeGuidance.routePoints
                            .drop(uiState.routeGuidance.nextWaypointIndex.coerceAtLeast(0))
                        val fullLiveRoute = listOf(uiState.telemetry.latLng) + remainingPoints

                        Polyline(
                            points = fullLiveRoute,
                            color = Color(0xFF0F766E),
                            width = 14f
                        )
                    }

                    // 2. Breadcrumb trail of where the device has moved
                    if (uiState.locationHistory.size >= 2) {
                        Polyline(
                            points = uiState.locationHistory,
                            color = Color(0xFF2563EB),
                            width = 8f
                        )
                    }

                    // 3. Immediate Next-Waypoint Vector & Target Ring
                    val nextWaypoint = uiState.routeGuidance.nextWaypoint
                    if (nextWaypoint != null && !uiState.routeGuidance.hasArrived) {
                        Polyline(
                            points = listOf(uiState.telemetry.latLng, nextWaypoint),
                            color = Color(0xFF7C3AED),
                            width = 10f
                        )
                        Circle(
                            center = nextWaypoint,
                            radius = 5.0,
                            strokeColor = Color(0xFF7C3AED),
                            strokeWidth = 4f,
                            fillColor = Color(0x447C3AED)
                        )
                    }

                    // 4. Source Marker (Starting Device Location)
                    if (sourceLatLng != null && sourceMarkerState != null && destinationLatLng != null) {
                        Marker(
                            state = sourceMarkerState,
                            title = "Source (Detected Start Location)",
                            snippet = String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                sourceLatLng.latitude,
                                sourceLatLng.longitude
                            ),
                            icon = BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_AZURE
                            )
                        )
                    }

                    // 5. Destination Marker & Arrival Zone Circle
                    if (destinationLatLng != null && destinationMarkerState != null) {
                        Circle(
                            center = destinationLatLng,
                            radius = 15.0,
                            strokeColor = Color(0xFFEA580C),
                            strokeWidth = 4f,
                            fillColor = Color(0x33EA580C)
                        )
                        Marker(
                            state = destinationMarkerState,
                            title = "Selected Destination",
                            snippet = String.format(
                                Locale.US,
                                "%.5f, %.5f • %dm remaining",
                                destinationLatLng.latitude,
                                destinationLatLng.longitude,
                                uiState.routeGuidance.totalDistanceRemainingMeters.toInt()
                            ),
                            icon = BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_ORANGE
                            )
                        )
                    }

                    // 6. Continuously Tracked Device / Belt Marker
                    Marker(
                        state = patientMarkerState,
                        title = "Tracked Device (${uiState.telemetry.navCommand.motorName})",
                        snippet = String.format(
                            Locale.US,
                            "Pos: %.5f, %.5f • Motor: %s",
                            uiState.telemetry.latitude,
                            uiState.telemetry.longitude,
                            uiState.telemetry.navCommand.motorDescription
                        ),
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (uiState.telemetry.sosActive) {
                                BitmapDescriptorFactory.HUE_RED
                            } else {
                                BitmapDescriptorFactory.HUE_GREEN
                            }
                        )
                    )
                }

                // Live Coordinate & Active Motor Overlay (Top-Left of Map)
                Card(
                    onClick = { showGpsDebugPage = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.telemetry.navCommand != NavDirection.NONE) {
                                            Color(0xFFEA580C)
                                        } else {
                                            Color(0xFF16A34A)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.telemetry.navCommand != NavDirection.NONE) {
                                    "${uiState.telemetry.navCommand.motorName}: VIBRATING"
                                } else if (uiState.routeGuidance.hasArrived) {
                                    "ARRIVED • MOTORS OFF"
                                } else {
                                    "LIVE DEVICE GPS TRACKING"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.telemetry.navCommand != NavDirection.NONE) {
                                    Color(0xFFEA580C)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Lat: %.6f\nLng: %.6f\nCourse: %.0f°",
                                uiState.telemetry.latitude,
                                uiState.telemetry.longitude,
                                uiState.telemetry.courseDegrees
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Map Floating Action Controls (Bottom-Right of Map)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.clearTrailHistory() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Clear Movement Trail"
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            viewModel.toggleFollowWearer(true)
                            detectCurrentDeviceLocation()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Detect Current Location & Center Map"
                        )
                    }
                }

                // Transient confirmation toast at bottom of map
                if (uiState.statusBannerMessage != null) {
                    Surface(
                        color = Color(0xE60F172A),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp, start = 14.dp, end = 72.dp)
                    ) {
                        Text(
                            text = uiState.statusBannerMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Bottom Panel: Active Vibration Motors + Live Motor & Location Tracking Logs
            BottomMotorTrackingLogsPanel(
                uiState = uiState,
                showManualControls = showManualMotorControls,
                onToggleManualControls = { showManualMotorControls = !showManualMotorControls },
                onDetectCurrentLocation = detectCurrentDeviceLocation,
                onToggleSimulateWalk = { viewModel.toggleSimulateWalkingRoute() },
                onReplayRoute = { viewModel.recalculateRoute() },
                onClearMotorLogs = { viewModel.clearMotorVibrationLogs() },
                onSendManualDirection = { dir -> viewModel.sendNavigationCommand(dir) },
                onToggleAutoGuidance = { enabled -> viewModel.toggleAutoGuidance(enabled) },
                onOpenEspConfig = { showConnectionDialog = true }
            )
        }
    }

    // ESP32 Connection Settings Dialog
    if (showConnectionDialog) {
        Esp32ConnectionDialog(
            currentMode = uiState.connectionMode,
            currentUrl = uiState.esp32BaseUrl,
            onDismiss = { showConnectionDialog = false },
            onSave = { selectedMode, newUrl ->
                viewModel.updateEsp32Config(newUrl, selectedMode)
                showConnectionDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomMotorTrackingLogsPanel(
    uiState: BeltUiState,
    showManualControls: Boolean,
    onToggleManualControls: () -> Unit,
    onDetectCurrentLocation: () -> Unit,
    onToggleSimulateWalk: () -> Unit,
    onReplayRoute: () -> Unit,
    onClearMotorLogs: () -> Unit,
    onSendManualDirection: (NavDirection) -> Unit,
    onToggleAutoGuidance: (Boolean) -> Unit,
    onOpenEspConfig: () -> Unit
) {
    val logListState = rememberLazyListState()

    // Keep newest log item visible at top as continuous updates stream in
    LaunchedEffect(uiState.motorVibrationLogs.firstOrNull()?.id) {
        if (uiState.motorVibrationLogs.isNotEmpty()) {
            runCatching {
                logListState.scrollToItem(0)
            }
        }
    }

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 1. Header Row: Route Distance Status + Quick Location / Demo Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                uiState.routeGuidance.hasArrived ->
                                    "Arrived at Destination! (0m)"
                                uiState.routeGuidance.destination != null ->
                                    "Tracking Route: ${uiState.routeGuidance.totalDistanceRemainingMeters.toInt()}m remaining"
                                else ->
                                    "Tap Map to Generate Route"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "Device Pos: %.5f, %.5f • Active: %s",
                            uiState.telemetry.latitude,
                            uiState.telemetry.longitude,
                            uiState.telemetry.navCommand.motorName
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = onDetectCurrentLocation,
                        label = {
                            Text(
                                text = "My Location",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )

                    if (uiState.connectionMode == ConnectionMode.DEMO_SIMULATOR) {
                        if (uiState.routeGuidance.hasArrived && uiState.routeGuidance.destination != null) {
                            AssistChip(
                                onClick = onReplayRoute,
                                label = {
                                    Text(
                                        text = "Replay Walk",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        } else {
                            FilterChip(
                                selected = uiState.simulateWalkingRoute,
                                onClick = onToggleSimulateWalk,
                                label = {
                                    Text(
                                        text = if (uiState.simulateWalkingRoute) {
                                            "Auto-Track: ON"
                                        } else {
                                            "Live GPS"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    } else {
                        AssistChip(
                            onClick = onOpenEspConfig,
                            label = {
                                Text(
                                    text = uiState.esp32BaseUrl.removePrefix("http://"),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Live 3-Motor Vibration Status Indicator Strip (Left, Front, Right Motors)
            LiveMotorStatusStrip(activeDirection = uiState.telemetry.navCommand)

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Live Motor Vibration & Continuous Location Tracking Logs Box
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MOTOR VIBRATION & TRACKING LOGS (${uiState.motorVibrationLogs.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onToggleManualControls,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showManualControls) "Hide Controls" else "Manual Test",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            TextButton(
                                onClick = onClearMotorLogs,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Motor Logs",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(
                            items = uiState.motorVibrationLogs,
                            key = { it.id }
                        ) { entry ->
                            MotorVibrationLogRow(entry = entry)
                        }
                    }
                }
            }

            // 4. Optional Collapsible Manual Motor Override Controls
            AnimatedVisibility(visible = showManualControls) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Manual Motor Override (/navigate?dir=...)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Auto-Guide",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = uiState.routeGuidance.isAutoGuidanceEnabled,
                                onCheckedChange = onToggleAutoGuidance
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxItemsInEachRow = 2
                    ) {
                        NavDirection.entries.forEach { direction ->
                            val isSelected = uiState.telemetry.navCommand == direction
                            val icon = when (direction) {
                                NavDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
                                NavDirection.FORWARD -> Icons.Default.ArrowUpward
                                NavDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
                                NavDirection.NONE -> Icons.Default.StopCircle
                            }

                            if (isSelected && direction != NavDirection.NONE) {
                                Button(
                                    onClick = { onSendManualDirection(direction) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${direction.title} (${direction.motorName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSendManualDirection(direction) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${direction.title} (${direction.dirParam})",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMotorStatusStrip(activeDirection: NavDirection) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MotorIndicatorPill(
            title = "LEFT MOTOR",
            pinLabel = "GPIO 25",
            isVibrating = activeDirection == NavDirection.LEFT,
            activeColor = Color(0xFFEA580C),
            modifier = Modifier.weight(1f)
        )
        MotorIndicatorPill(
            title = "FRONT MOTOR",
            pinLabel = "GPIO 32",
            isVibrating = activeDirection == NavDirection.FORWARD,
            activeColor = Color(0xFF16A34A),
            modifier = Modifier.weight(1f)
        )
        MotorIndicatorPill(
            title = "RIGHT MOTOR",
            pinLabel = "GPIO 33",
            isVibrating = activeDirection == NavDirection.RIGHT,
            activeColor = Color(0xFF2563EB),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MotorIndicatorPill(
    title: String,
    pinLabel: String,
    isVibrating: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isVibrating) activeColor else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isVibrating) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .then(
                if (isVibrating) {
                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isVibrating) Color.White else Color(0xFF94A3B8)
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                text = if (isVibrating) "VIBRATING ($pinLabel)" else "IDLE ($pinLabel)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isVibrating) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MotorVibrationLogRow(entry: MotorVibrationLogEntry) {
    val (badgeBg, badgeFg) = when {
        entry.isArrivalEvent -> Color(0xFF16A34A) to Color.White
        entry.isSystemEvent -> Color(0xFF334155) to Color(0xFFE2E8F0)
        entry.activeMotor == NavDirection.LEFT -> Color(0xFFEA580C) to Color.White
        entry.activeMotor == NavDirection.FORWARD -> Color(0xFF0D9488) to Color.White
        entry.activeMotor == NavDirection.RIGHT -> Color(0xFF2563EB) to Color.White
        else -> Color(0xFF475569) to Color(0xFFF1F5F9)
    }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = entry.motorStatusTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (entry.remainingDistanceMeters != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${entry.remainingDistanceMeters}m left",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = entry.guidanceInstruction,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF8FAFC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.coordinatesText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.stepProgressText != null) {
                    Text(
                        text = entry.stepProgressText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteGuidanceBanner(
    routeGuidance: RouteGuidanceState,
    onRecalculateRoute: () -> Unit,
    onClearDestination: () -> Unit
) {
    val hasDestination = routeGuidance.destination != null
    val bgColor = when {
        !hasDestination -> MaterialTheme.colorScheme.secondaryContainer
        routeGuidance.hasArrived -> Color(0xFF15803D)
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        !hasDestination -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> Color.White
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val directionIcon = when {
                    !hasDestination -> Icons.Default.Flag
                    routeGuidance.hasArrived -> Icons.Default.CheckCircle
                    routeGuidance.recommendedDirection == NavDirection.LEFT ->
                        Icons.AutoMirrored.Filled.ArrowBack
                    routeGuidance.recommendedDirection == NavDirection.RIGHT ->
                        Icons.AutoMirrored.Filled.ArrowForward
                    else -> Icons.Default.ArrowUpward
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = routeGuidance.guidanceHeadline,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = routeGuidance.guidanceSubtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.9f)
                    )
                }
            }

            if (hasDestination) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRecalculateRoute) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Replay / Recalculate Route",
                            tint = contentColor
                        )
                    }
                    IconButton(onClick = onClearDestination) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Destination",
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UrgencyBadge(urgency: SafetyUrgency) {
    val (bgColor, textColor) = when (urgency) {
        SafetyUrgency.SAFE -> Color(0xFFDCFCE7) to Color(0xFF166534)
        SafetyUrgency.LOW -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        SafetyUrgency.MEDIUM -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        SafetyUrgency.HIGH -> Color(0xFFFED7AA) to Color(0xFF9A3412)
        SafetyUrgency.CRITICAL -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = "Urgency: ${urgency.apiValue}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Esp32ConnectionDialog(
    currentMode: ConnectionMode,
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (ConnectionMode, String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var urlInput by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ESP32 Belt Server Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose how the app polls telemetry and sends tactile navigation cues:",
                    style = MaterialTheme.typography.bodySmall
                )

                ConnectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.label) },
                        leadingIcon = if (selectedMode == mode) {
                            {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("ESP32 Host URL (Port 80)") },
                    placeholder = { Text("http://192.168.4.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "• GET /data — Polls GPS, urgency, navCommand, beltWorn, sosActive\n• GET /navigate?dir=left|right|forward|none — Sends tactile motor cues\n• GET / — Built-in ESP32 HTML browser dashboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedMode, urlInput) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
