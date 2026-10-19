package com.carebelt.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carebelt.tracker.BuildConfig
import com.carebelt.tracker.model.ConnectionMode
import com.carebelt.tracker.model.VibrationPattern
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeltTrackerScreen(
    viewModel: BeltTrackerViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showConnectionDialog by rememberSaveable { mutableStateOf(false) }
    var showSafeZoneDialog by rememberSaveable { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.telemetry.latLng, 17.5f)
    }

    // Automatically follow the patient's belt coordinates when enabled
    LaunchedEffect(uiState.telemetry.latLng, uiState.followWearerOnMap) {
        if (uiState.followWearerOnMap) {
            runCatching {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLng(uiState.telemetry.latLng),
                    durationMs = 700
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

    val isPlaceholderApiKey = BuildConfig.MAPS_API_KEY.isBlank() ||
        BuildConfig.MAPS_API_KEY == "YOUR_GOOGLE_MAPS_API_KEY_HERE"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CareBelt Live Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.connectionMode.label} • ${uiState.telemetry.satellites} sats",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Belt Battery Pill
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryFull,
                                contentDescription = "Belt Battery",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.telemetry.batteryPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Safe Zone Dialog Button
                    IconButton(onClick = { showSafeZoneDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Safe Zone Settings"
                        )
                    }

                    // ESP32 Connection Settings Button
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
            // Optional warning banner if Google Maps API key is still placeholder
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

            // Geofence Breach Alert Banner
            AnimatedVisibility(visible = uiState.isOutsideSafeZone) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
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
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Safe Zone Breach",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SAFE ZONE BREACH (${uiState.distanceFromHomeMeters.toInt()}m from Home)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Home is ${uiState.bearingToHomeCardinal} (${uiState.bearingToHomeDeg}°) • Auto-vibration active",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.triggerBeltVibration(VibrationPattern.RETURN_HOME_CUE)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Buzz Home Cue", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

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
                                viewModel.setConnectionMode(ConnectionMode.DEMO_SIMULATOR)
                            }
                        ) {
                            Text("Use Demo", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Map Container (Upper portion of screen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val patientMarkerState = remember(uiState.telemetry.latLng) {
                    MarkerState(position = uiState.telemetry.latLng)
                }
                val homeMarkerState = remember(uiState.safeZone.homeLocation) {
                    MarkerState(position = uiState.safeZone.homeLocation)
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapType = MapType.NORMAL,
                        isBuildingEnabled = true
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = true,
                        mapToolbarEnabled = true
                    )
                ) {
                    // Safe Zone Geofence Circle Overlay
                    if (uiState.safeZone.isEnabled) {
                        val zoneStrokeColor = if (uiState.isOutsideSafeZone) {
                            Color(0xFFDC2626)
                        } else {
                            Color(0xFF0F766E)
                        }
                        val zoneFillColor = if (uiState.isOutsideSafeZone) {
                            Color(0x33DC2626)
                        } else {
                            Color(0x260F766E)
                        }

                        Circle(
                            center = uiState.safeZone.homeLocation,
                            radius = uiState.safeZone.radiusMeters,
                            strokeColor = zoneStrokeColor,
                            strokeWidth = 5f,
                            fillColor = zoneFillColor
                        )

                        Marker(
                            state = homeMarkerState,
                            title = "Safe Zone Home",
                            snippet = "Radius: ${uiState.safeZone.radiusMeters.toInt()}m",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_AZURE
                            )
                        )
                    }

                    // Breadcrumb trail of recent NEO-6M GPS coordinates
                    if (uiState.locationHistory.size >= 2) {
                        Polyline(
                            points = uiState.locationHistory,
                            color = Color(0xFF2563EB),
                            width = 9f
                        )
                    }

                    // Live Patient Belt Marker
                    Marker(
                        state = patientMarkerState,
                        title = uiState.telemetry.patientName,
                        snippet = String.format(
                            Locale.US,
                            "%.5f, %.5f • %.1f km/h",
                            uiState.telemetry.latitude,
                            uiState.telemetry.longitude,
                            uiState.telemetry.speedKmh
                        ),
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (uiState.isOutsideSafeZone) {
                                BitmapDescriptorFactory.HUE_RED
                            } else {
                                BitmapDescriptorFactory.HUE_GREEN
                            }
                        )
                    )
                }

                // Live Coordinate Badge Overlay (Top-Left of Map)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isConnected) Color(0xFF16A34A) else Color(0xFFDC2626)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.telemetry.isVibrating) {
                                    "BELT VIBRATING NOW"
                                } else {
                                    "NEO-6M GPS LOCK"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.telemetry.isVibrating) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Lat: %.6f\nLng: %.6f",
                                uiState.telemetry.latitude,
                                uiState.telemetry.longitude
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
                        onClick = { viewModel.setSafeZoneHomeToCurrentBeltLocation() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Set Current Belt Position as Home"
                        )
                    }

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
                            coroutineScope.launch {
                                runCatching {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            uiState.telemetry.latLng,
                                            17.5f
                                        )
                                    )
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Center on Patient Belt"
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
                            .padding(bottom = 12.dp, start = 16.dp, end = 72.dp)
                    ) {
                        Text(
                            text = uiState.statusBannerMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Bottom Caregiver Telemetry & Vibration Control Panel
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Telemetry summary row
                    val timeFormatter = remember {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${uiState.distanceFromHomeMeters.toInt()}m from Home",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${String.format(Locale.US, "%.1f km/h", uiState.telemetry.speedKmh)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Last sync: ${timeFormatter.format(Date(uiState.telemetry.timestampMillis))} • Clasp: ${if (uiState.telemetry.isBeltFastened) "Fastened" else "UNBUCKLED"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Quick Mode Switcher / Simulator Wandering Button
                        if (uiState.connectionMode == ConnectionMode.DEMO_SIMULATOR) {
                            FilterChip(
                                selected = uiState.isSimulatingWandering,
                                onClick = { viewModel.toggleSimulateWandering() },
                                label = {
                                    Text(
                                        text = if (uiState.isSimulatingWandering) {
                                            "Wandering: ON"
                                        } else {
                                            "Test Wander"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.isSimulatingWandering) {
                                            Icons.Default.Warning
                                        } else {
                                            Icons.Default.GpsFixed
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        } else {
                            AssistChip(
                                onClick = { showConnectionDialog = true },
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors()
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Vibration Motor Remote Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Belt Vibration Motor Cues (ESP32)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Tap to buzz belt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        VibrationPattern.entries.forEach { pattern ->
                            OutlinedButton(
                                onClick = { viewModel.triggerBeltVibration(pattern) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pattern.title,
                                    style = MaterialTheme.typography.labelMedium,
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

    // Dialog 1: ESP32 Connection Settings
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

    // Dialog 2: Safe Zone Geofence Settings
    if (showSafeZoneDialog) {
        SafeZoneConfigDialog(
            radiusMeters = uiState.safeZone.radiusMeters,
            isEnabled = uiState.safeZone.isEnabled,
            autoVibrateOnExit = uiState.safeZone.autoVibrateOnExit,
            onSetHomeHere = {
                viewModel.setSafeZoneHomeToCurrentBeltLocation()
            },
            onDismiss = { showSafeZoneDialog = false },
            onSave = { radius, enabled, autoVibrate ->
                viewModel.updateSafeZoneSettings(radius, enabled, autoVibrate)
                showSafeZoneDialog = false
            }
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
        title = { Text("ESP32 + NEO-6M Belt Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose how the app fetches GPS coordinates and sends vibration commands:",
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
                    label = { Text("ESP32 IP or Endpoint URL") },
                    placeholder = { Text("http://192.168.4.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "• AP Mode default: http://192.168.4.1\n• Polls GET /location every 2s\n• Sends POST /vibrate?pattern=...",
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

@Composable
private fun SafeZoneConfigDialog(
    radiusMeters: Double,
    isEnabled: Boolean,
    autoVibrateOnExit: Boolean,
    onSetHomeHere: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Double, Boolean, Boolean) -> Unit
) {
    var sliderRadius by remember { mutableFloatStateOf(radiusMeters.toFloat()) }
    var zoneEnabled by remember { mutableStateOf(isEnabled) }
    var autoVibrate by remember { mutableStateOf(autoVibrateOnExit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safe Zone & Auto-Vibration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Safe Zone Perimeter", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = zoneEnabled, onCheckedChange = { zoneEnabled = it })
                }

                Column {
                    Text(
                        text = "Safe Zone Radius: ${sliderRadius.toInt()} meters",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = sliderRadius,
                        onValueChange = { sliderRadius = it },
                        valueRange = 50f..500f,
                        steps = 8
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Vibrate Belt on Exit",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Triggers ESP32 vibration motor if patient walks outside Safe Zone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoVibrate, onCheckedChange = { autoVibrate = it })
                }

                OutlinedButton(
                    onClick = onSetHomeHere,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Set Belt's Current Location as Home")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(sliderRadius.toDouble(), zoneEnabled, autoVibrate) },
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
