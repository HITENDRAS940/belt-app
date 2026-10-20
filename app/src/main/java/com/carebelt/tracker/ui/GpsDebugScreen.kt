package com.carebelt.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carebelt.tracker.model.BeltTelemetry
import com.carebelt.tracker.model.EspConnectionLogEntry
import com.carebelt.tracker.model.EspLogLevel
import com.carebelt.tracker.model.GpsDebugParameter
import com.carebelt.tracker.model.toGpsDebugParameters
import java.util.Locale

enum class DebugDisplayMode(val label: String) {
    ESP_LOGS("ESP32 Logs"),
    CARDS("12 GPS Cards"),
    TABLE("Full Table (1–12)"),
    RAW_JSON("Raw JSON")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GpsDebugScreen(
    uiState: BeltUiState,
    connectionLogs: List<EspConnectionLogEntry>,
    initialMode: DebugDisplayMode = DebugDisplayMode.ESP_LOGS,
    onRunDiagnostic: () -> Unit,
    onClearLogs: () -> Unit,
    onBackToMap: () -> Unit
) {
    val telemetry = uiState.telemetry
    val debugParams = telemetry.toGpsDebugParameters()
    var displayMode by rememberSaveable { mutableStateOf(initialMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToMap) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Map"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "ESP32 & NEO-6M Debug Console",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.connectionMode.label} • Target: ${uiState.esp32BaseUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Summary Diagnostic Banner
            item {
                GpsDiagnosticSummaryCard(uiState = uiState)
            }

            // View Mode Selector (ESP32 Logs | 12 GPS Cards | Full Table | Raw JSON)
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = displayMode == DebugDisplayMode.ESP_LOGS,
                        onClick = { displayMode = DebugDisplayMode.ESP_LOGS },
                        label = { Text("${DebugDisplayMode.ESP_LOGS.label} (${connectionLogs.size})") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = displayMode == DebugDisplayMode.CARDS,
                        onClick = { displayMode = DebugDisplayMode.CARDS },
                        label = { Text(DebugDisplayMode.CARDS.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ViewAgenda,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = displayMode == DebugDisplayMode.TABLE,
                        onClick = { displayMode = DebugDisplayMode.TABLE },
                        label = { Text(DebugDisplayMode.TABLE.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = displayMode == DebugDisplayMode.RAW_JSON,
                        onClick = { displayMode = DebugDisplayMode.RAW_JSON },
                        label = { Text(DebugDisplayMode.RAW_JSON.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            when (displayMode) {
                DebugDisplayMode.ESP_LOGS -> {
                    item {
                        EspNetworkDiagnosticHeaderCard(
                            uiState = uiState,
                            onRunDiagnostic = onRunDiagnostic,
                            onClearLogs = onClearLogs
                        )
                    }

                    if (connectionLogs.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No connection logs yet. Switch to Live ESP32 mode or tap 'Test ESP32 Connection' above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(connectionLogs, key = { it.id }) { entry ->
                            EspConnectionLogCard(entry = entry)
                        }
                    }
                }

                DebugDisplayMode.CARDS -> {
                    items(debugParams, key = { it.number }) { param ->
                        GpsParameterCard(param = param)
                    }
                }

                DebugDisplayMode.TABLE -> {
                    item {
                        GpsFullSpecificationTable(parameters = debugParams)
                    }
                }

                DebugDisplayMode.RAW_JSON -> {
                    item {
                        RawTelemetryJsonCard(telemetry = telemetry, baseUrl = uiState.esp32BaseUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun EspNetworkDiagnosticHeaderCard(
    uiState: BeltUiState,
    onRunDiagnostic: () -> Unit,
    onClearLogs: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ESP32 Network & Logcat Debugger",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = if (uiState.isConnected) Color(0xFF16A34A) else Color(0xFFDC2626),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (uiState.isConnected) "ONLINE" else "UNREACHABLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Target Endpoint: ${uiState.esp32BaseUrl}/data\n" +
                    "• Phone IPv4 Interfaces: ${uiState.localNetworkSummary.ifBlank { "Checking..." }}\n" +
                    "• Logcat Command: adb logcat -s CareBeltESP32\n" +
                    "• Tip: If connected to ESP32 Wi-Fi (192.168.4.1), turn OFF Mobile Data so Android routes HTTP over Wi-Fi!",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunDiagnostic,
                    enabled = !uiState.isRunningDiagnostic,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isRunningDiagnostic) {
                            "Testing ESP32..."
                        } else {
                            "Test ESP32 Connection"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(onClick = onClearLogs) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EspConnectionLogCard(entry: EspConnectionLogEntry) {
    val (badgeColor, badgeTextColor) = when (entry.level) {
        EspLogLevel.SUCCESS -> Color(0xFFDCFCE7) to Color(0xFF166534)
        EspLogLevel.INFO -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        EspLogLevel.WARN -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        EspLogLevel.ERROR -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.level.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.httpCode != null) {
                        Text(
                            text = "HTTP ${entry.httpCode} • ",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (entry.latencyMs != null) {
                        Text(
                            text = "${entry.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.endpoint,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (entry.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsDiagnosticSummaryCard(uiState: BeltUiState) {
    val telemetry = uiState.telemetry
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SatelliteAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "u-blox NEO-6M Receiver Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${telemetry.gpsDate} • ${telemetry.gpsTimeUtc}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Surface(
                    color = if (telemetry.hasValidGpsFix) Color(0xFF16A34A) else Color(0xFFDC2626),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = telemetry.fixType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetricPill(label = "Satellites", value = "${telemetry.satellites}")
                SummaryMetricPill(
                    label = "HDOP",
                    value = String.format(Locale.US, "%.2f", telemetry.hdop)
                )
                SummaryMetricPill(
                    label = "Altitude",
                    value = String.format(Locale.US, "%.1f m", telemetry.altitudeMeters)
                )
                SummaryMetricPill(
                    label = "Geoid Sep.",
                    value = String.format(Locale.US, "%.1f m", telemetry.geoidSeparationMeters)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GpsParameterCard(param: GpsDebugParameter) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "${param.number}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = param.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Unit: ${param.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (param.isHealthy) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (param.isHealthy) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (param.isHealthy) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = param.liveValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (param.isHealthy) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "What it tells you",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = param.whatItTellsYou,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Application in CareBelt",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = param.applicationInCareBelt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsFullSpecificationTable(parameters: List<GpsDebugParameter>) {
    val scrollState = rememberScrollState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(text = "No.", width = 40.dp, isHeader = true)
                TableCell(text = "Value (Name)", width = 140.dp, isHeader = true)
                TableCell(text = "Live Reading", width = 175.dp, isHeader = true)
                TableCell(text = "Unit", width = 115.dp, isHeader = true)
                TableCell(text = "What it tells you", width = 230.dp, isHeader = true)
                TableCell(text = "Application in CareBelt", width = 240.dp, isHeader = true)
            }

            parameters.forEach { param ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(text = "${param.number}", width = 40.dp, isBold = true)
                    TableCell(text = param.name, width = 140.dp, isBold = true)
                    TableCell(
                        text = param.liveValue,
                        width = 175.dp,
                        isMonospace = true,
                        isBold = true
                    )
                    TableCell(text = param.unit, width = 115.dp)
                    TableCell(text = param.whatItTellsYou, width = 230.dp)
                    TableCell(text = param.applicationInCareBelt, width = 240.dp)
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    isMonospace: Boolean = false
) {
    Text(
        text = text,
        style = if (isHeader) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.bodySmall
        },
        fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
        color = if (isHeader) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp)
    )
}

@Composable
private fun RawTelemetryJsonCard(telemetry: BeltTelemetry, baseUrl: String) {
    val rawDataJson = """
        {
          "latitude": ${String.format(Locale.US, "%.6f", telemetry.latitude)},
          "longitude": ${String.format(Locale.US, "%.6f", telemetry.longitude)},
          "satellites": ${telemetry.satellites},
          "hasFix": ${telemetry.hasValidGpsFix},
          "urgency": "${telemetry.urgency.apiValue}",
          "navCommand": "${telemetry.navCommand.dirParam}",
          "beltWorn": ${telemetry.isBeltFastened},
          "sosActive": ${telemetry.sosActive},
          "altitude": ${String.format(Locale.US, "%.1f", telemetry.altitudeMeters)},
          "gpsTimeUtc": "${telemetry.gpsTimeUtc}",
          "gpsDate": "${telemetry.gpsDate}",
          "speedKmh": ${String.format(Locale.US, "%.2f", telemetry.speedKmh)},
          "speedKnots": ${String.format(Locale.US, "%.2f", telemetry.speedKnots)},
          "courseDeg": ${String.format(Locale.US, "%.1f", telemetry.courseDegrees)},
          "fixStatus": "${telemetry.fixStatusCode}",
          "hdop": ${String.format(Locale.US, "%.2f", telemetry.hdop)},
          "geoidSeparation": ${String.format(Locale.US, "%.1f", telemetry.geoidSeparationMeters)},
          "fixType": "${telemetry.fixType}"
        }
    """.trimIndent()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "GET $baseUrl/data — Live ESP32 Telemetry Payload",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = rawDataJson,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE2E8F0),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
