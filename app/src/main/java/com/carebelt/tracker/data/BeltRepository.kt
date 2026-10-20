package com.carebelt.tracker.data

import android.util.Log
import com.carebelt.tracker.model.BeltTelemetry
import com.carebelt.tracker.model.EspConnectionLogEntry
import com.carebelt.tracker.model.EspLogLevel
import com.carebelt.tracker.model.GeoUtils
import com.carebelt.tracker.model.NavDirection
import com.carebelt.tracker.model.SafetyUrgency
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.cos
import kotlin.math.sin

/**
 * Repository consuming the ESP32 REST API on port 80 (`GET /data`, `GET /navigate?dir=...`)
 * with comprehensive Logcat (`tag: CareBeltESP32`) and in-app connection diagnostic logging.
 */
class BeltRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val LOG_TAG = "CareBeltESP32"
        private const val MAX_LOG_ENTRIES = 120
    }

    private val logIdGenerator = AtomicLong(1L)
    private val _connectionLogs = MutableStateFlow<List<EspConnectionLogEntry>>(emptyList())
    val connectionLogs: StateFlow<List<EspConnectionLogEntry>> = _connectionLogs.asStateFlow()

    private var stepCounter: Int = 0
    private var simulatedNavCommand: NavDirection = NavDirection.NONE
    private var simulatedLatLng: LatLng = LatLng(28.6139, 77.2090)
    private var simulatedHeadingDeg: Double = 0.0
    private var simulatedRouteWalkIndex: Int = 0

    // Previous live coordinate sample for deriving speed & course over ground if not sent in /data
    private var lastSampleLatLng: LatLng? = null
    private var lastSampleTimeMs: Long = 0L
    private var lastDerivedCourseDeg: Double = 0.0

    private val utcTimeFormat = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        recordLog(
            level = EspLogLevel.INFO,
            endpoint = "INIT",
            summary = "ESP32 Connection Logger initialized",
            details = "Active interfaces: ${getLocalNetworkInterfacesSummary()}"
        )
    }

    fun clearConnectionLogs() {
        _connectionLogs.value = emptyList()
        recordLog(
            level = EspLogLevel.INFO,
            endpoint = "CLEAR",
            summary = "Logs cleared by user",
            details = "Active interfaces: ${getLocalNetworkInterfacesSummary()}"
        )
    }

    fun resetSimulatedRouteProgress() {
        simulatedRouteWalkIndex = 1
    }

    /**
     * Synchronizes the simulator's current position with the Android device's real GPS location.
     */
    fun setSimulatedDeviceLocation(
        latLng: LatLng,
        headingDeg: Double? = null
    ) {
        simulatedLatLng = latLng
        if (headingDeg != null && headingDeg > 0.0) {
            simulatedHeadingDeg = headingDeg
        }
    }

    /**
     * Inspects local network interfaces (`wlan0`, `rmnet`, etc.) to help diagnose
     * why the phone can or cannot reach the ESP32's IP address.
     */
    fun getLocalNetworkInterfacesSummary(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val activeIps = mutableListOf<String>()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                for (addr in addrs) {
                    activeIps.add("${nif.name}=${addr.hostAddress}")
                }
            }
            if (activeIps.isEmpty()) {
                "No active IPv4 interfaces (Wi-Fi disconnected?)"
            } else {
                activeIps.joinToString(", ")
            }
        } catch (e: Exception) {
            "Interface check error: ${e.message}"
        }
    }

    private fun recordLog(
        level: EspLogLevel,
        endpoint: String,
        httpCode: Int? = null,
        latencyMs: Long? = null,
        summary: String,
        details: String
    ) {
        val timestamp = synchronized(logTimeFormat) {
            logTimeFormat.format(Date())
        }
        val entry = EspConnectionLogEntry(
            id = logIdGenerator.getAndIncrement(),
            timestamp = timestamp,
            level = level,
            endpoint = endpoint,
            httpCode = httpCode,
            latencyMs = latencyMs,
            summary = summary,
            details = details
        )
        _connectionLogs.update { current ->
            (listOf(entry) + current).take(MAX_LOG_ENTRIES)
        }

        // Also output to Android Studio Logcat (`adb logcat -s CareBeltESP32`)
        val logcatMsg = buildString {
            append("[$endpoint] ")
            if (httpCode != null) append("(HTTP $httpCode) ")
            if (latencyMs != null) append("[${latencyMs}ms] ")
            append(summary)
            if (details.isNotBlank()) append(" | $details")
        }
        runCatching {
            when (level) {
                EspLogLevel.INFO -> Log.i(LOG_TAG, logcatMsg)
                EspLogLevel.SUCCESS -> Log.d(LOG_TAG, logcatMsg)
                EspLogLevel.WARN -> Log.w(LOG_TAG, logcatMsg)
                EspLogLevel.ERROR -> Log.e(LOG_TAG, logcatMsg)
            }
        }
    }

    private fun buildExceptionDiagnosis(url: String, throwable: Throwable): String {
        val localIfaces = getLocalNetworkInterfacesSummary()
        val hint = when (throwable) {
            is SocketTimeoutException ->
                "HINT: Request timed out (4s). (1) Ensure phone is connected to ESP32 Wi-Fi/Hotspot. " +
                    "(2) Turn OFF Mobile Data on your phone! Android often routes 192.168.x.x over 4G/5G if ESP32 Wi-Fi has no internet."
            is ConnectException ->
                if (throwable.message?.contains("ECONNREFUSED", ignoreCase = true) == true) {
                    "HINT: ESP32 IP is reachable, but Port 80 refused connection. Verify server.begin() is running on port 80."
                } else {
                    "HINT: Network/Host unreachable. Check that your phone's wlan0 IP is on the same subnet as $url. " +
                        "Also disable Mobile Data temporarily."
                }
            is UnknownHostException ->
                "HINT: DNS/Hostname lookup failed for $url. Use a raw IPv4 address like http://192.168.4.1"
            is UnknownServiceException ->
                "HINT: Cleartext HTTP blocked by Android OS. Verify usesCleartextTraffic=\"true\" in AndroidManifest.xml."
            is JSONException ->
                "HINT: ESP32 responded with malformed JSON on /data. Check Serial Monitor output."
            else ->
                "HINT: Verify ESP32 power, Wi-Fi connection, and IP address."
        }
        return "${throwable.javaClass.simpleName}: ${throwable.localizedMessage ?: "No message"}\n" +
            "Phone Network Interfaces: [$localIfaces]\n$hint"
    }

    /**
     * Runs an active 4-step connection diagnostic against [baseUrl] and logs each step.
     */
    suspend fun runEsp32Diagnostic(baseUrl: String): String = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        val uri = runCatching { URI(normalizedBase) }.getOrNull()
        val host = uri?.host ?: normalizedBase.removePrefix("http://").substringBefore(":")
        val port = if (uri != null && uri.port != -1) uri.port else 80

        val interfacesInfo = getLocalNetworkInterfacesSummary()
        recordLog(
            level = EspLogLevel.INFO,
            endpoint = "DIAG [1/4] NET_IFACE",
            summary = "Checking phone network interfaces for target $host:$port",
            details = "Active IPv4 interfaces: $interfacesInfo"
        )

        // Step 2: Raw TCP Socket handshake test
        val tcpStart = System.currentTimeMillis()
        val tcpOk = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
            }
            val tcpElapsed = System.currentTimeMillis() - tcpStart
            recordLog(
                level = EspLogLevel.SUCCESS,
                endpoint = "DIAG [2/4] TCP $host:$port",
                latencyMs = tcpElapsed,
                summary = "TCP Port $port is OPEN and reachable",
                details = "Connected to $host:$port in ${tcpElapsed}ms ($interfacesInfo)"
            )
            true
        } catch (e: Exception) {
            val tcpElapsed = System.currentTimeMillis() - tcpStart
            recordLog(
                level = EspLogLevel.ERROR,
                endpoint = "DIAG [2/4] TCP $host:$port",
                latencyMs = tcpElapsed,
                summary = "TCP connection to $host:$port FAILED",
                details = buildExceptionDiagnosis(normalizedBase, e)
            )
            false
        }

        if (!tcpOk) {
            return@withContext "Diagnostic failed at TCP $host:$port — check ESP32 Logs tab for details"
        }

        // Step 3: Test GET /data
        val dataResult = fetchEsp32Telemetry(normalizedBase)
        if (dataResult.isFailure) {
            return@withContext "TCP open, but GET /data failed — check ESP32 Logs tab"
        }

        // Step 4: Test GET /
        val rootStart = System.currentTimeMillis()
        runCatching {
            val req = Request.Builder().url("$normalizedBase/").get().build()
            httpClient.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - rootStart
                val bodyPreview = resp.body?.string().orEmpty().take(120).replace("\n", " ")
                recordLog(
                    level = if (resp.isSuccessful) EspLogLevel.SUCCESS else EspLogLevel.WARN,
                    endpoint = "DIAG [4/4] GET /",
                    httpCode = resp.code,
                    latencyMs = elapsed,
                    summary = "Web Dashboard GET / responded HTTP ${resp.code}",
                    details = "Preview: $bodyPreview"
                )
            }
        }.onFailure { err ->
            val elapsed = System.currentTimeMillis() - rootStart
            recordLog(
                level = EspLogLevel.WARN,
                endpoint = "DIAG [4/4] GET /",
                latencyMs = elapsed,
                summary = "GET / check failed: ${err.javaClass.simpleName}",
                details = err.localizedMessage ?: ""
            )
        }

        "ESP32 Diagnostic PASSED for $normalizedBase"
    }

    /**
     * Fetches a street-level walking route from [origin] to [destination].
     */
    suspend fun fetchWalkingRoute(
        origin: LatLng,
        destination: LatLng,
        googleMapsApiKey: String
    ): List<LatLng> = withContext(Dispatchers.IO) {
        if (googleMapsApiKey.isNotBlank() && googleMapsApiKey != "YOUR_GOOGLE_MAPS_API_KEY_HERE") {
            val googlePoints = runCatching {
                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&mode=walking&key=$googleMapsApiKey"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val overview = routes.getJSONObject(0)
                            .optJSONObject("overview_polyline")
                            ?.optString("points")
                            .orEmpty()
                        if (overview.isNotBlank()) {
                            GeoUtils.decodePolyline(overview)
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            }.getOrDefault(emptyList())

            if (googlePoints.size >= 2) {
                return@withContext GeoUtils.densifyRouteAdaptive(googlePoints, targetSteps = 22)
            }
        }

        val osrmPoints = runCatching {
            val url = "https://router.project-osrm.org/route/v1/foot/" +
                "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
                "?overview=full&geometries=polyline"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).optString("geometry").orEmpty()
                    if (geometry.isNotBlank()) {
                        GeoUtils.decodePolyline(geometry)
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }.getOrDefault(emptyList())

        if (osrmPoints.size >= 2) {
            return@withContext GeoUtils.densifyRouteAdaptive(osrmPoints, targetSteps = 22)
        }

        val fallbackPath = GeoUtils.buildFallbackStreetRoute(origin, destination)
        GeoUtils.densifyRouteAdaptive(fallbackPath, targetSteps = 20)
    }

    /**
     * Polls `GET http://<ESP32_IP>/data` for live telemetry and logs full diagnostics.
     */
    suspend fun fetchEsp32Telemetry(baseUrl: String): Result<BeltTelemetry> =
        withContext(Dispatchers.IO) {
            val normalizedBase = normalizeBaseUrl(baseUrl)
            val endpointUrl = "$normalizedBase/data"
            val startMs = System.currentTimeMillis()

            runCatching {
                val request = Request.Builder()
                    .url(endpointUrl)
                    .header("Accept", "application/json")
                    .header("Connection", "close")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val latencyMs = System.currentTimeMillis() - startMs
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        recordLog(
                            level = EspLogLevel.ERROR,
                            endpoint = "GET $endpointUrl",
                            httpCode = response.code,
                            latencyMs = latencyMs,
                            summary = "HTTP ${response.code} ${response.message}",
                            details = "Response body: ${body.take(300)}\nInterfaces: ${getLocalNetworkInterfacesSummary()}"
                        )
                        throw IOException("ESP32 GET /data HTTP ${response.code}: ${response.message}")
                    }

                    if (body.isBlank()) {
                        recordLog(
                            level = EspLogLevel.ERROR,
                            endpoint = "GET $endpointUrl",
                            httpCode = response.code,
                            latencyMs = latencyMs,
                            summary = "Empty response body from ESP32 /data",
                            details = "HTTP 200 OK received but body was 0 bytes"
                        )
                        throw IOException("Empty response from ESP32 GET /data")
                    }

                    val json = JSONObject(body)
                    val now = Date()
                    val nowMs = now.time

                    val lat = json.getDouble("latitude")
                    val lng = json.getDouble("longitude")
                    val currentLatLng = LatLng(lat, lng)
                    val satellites = json.optInt("satellites", 0)
                    val hasFix = json.optBoolean("hasFix", false)
                    val urgency = SafetyUrgency.fromApi(json.optString("urgency", "SAFE"))
                    val navCommand = NavDirection.fromApi(json.optString("navCommand", "none"))
                    val beltWorn = json.optBoolean("beltWorn", true)
                    val sosActive = json.optBoolean("sosActive", false)

                    recordLog(
                        level = EspLogLevel.SUCCESS,
                        endpoint = "GET $endpointUrl",
                        httpCode = response.code,
                        latencyMs = latencyMs,
                        summary = "OK: lat=$lat, lng=$lng, sats=$satellites, fix=$hasFix, nav=${navCommand.dirParam}, urgency=${urgency.apiValue}",
                        details = "Raw JSON (${body.length}B): ${body.replace("\n", " ").take(320)}"
                    )

                    var derivedSpeedKmh = 0.0
                    val prevLatLng = lastSampleLatLng
                    val dtSeconds = (nowMs - lastSampleTimeMs) / 1000.0
                    if (prevLatLng != null && dtSeconds in 0.3..30.0) {
                        val movedMeters = GeoUtils.distanceMeters(prevLatLng, currentLatLng)
                        if (movedMeters > 0.4) {
                            derivedSpeedKmh = (movedMeters / dtSeconds) * 3.6
                            lastDerivedCourseDeg = GeoUtils.bearingToTarget(prevLatLng, currentLatLng)
                                .first
                                .toDouble()
                        }
                    }
                    lastSampleLatLng = currentLatLng
                    lastSampleTimeMs = nowMs

                    val speedKmh = if (json.has("speedKmh")) {
                        json.optDouble("speedKmh", 0.0)
                    } else {
                        derivedSpeedKmh
                    }
                    val speedKnots = json.optDouble("speedKnots", speedKmh / 1.852)
                    val courseDeg = json.optDouble("courseDeg", lastDerivedCourseDeg)

                    val fixStatusLabel = json.optString(
                        "fixStatus",
                        if (hasFix) "1 (Valid SPS Fix / A)" else "0 (No Fix / V)"
                    )
                    val fixTypeLabel = json.optString(
                        "fixType",
                        when {
                            !hasFix -> "No Fix (Mode 1)"
                            satellites >= 4 -> "3D Fix (Mode 3)"
                            else -> "2D Fix (Mode 2)"
                        }
                    )
                    val estimatedHdop = json.optDouble(
                        "hdop",
                        when {
                            !hasFix -> 9.99
                            satellites >= 8 -> 0.95
                            satellites >= 5 -> 1.35
                            satellites >= 4 -> 1.85
                            else -> 2.60
                        }
                    )

                    BeltTelemetry(
                        patientName = json.optString("patientName", "Patient #1 (ESP32 Belt)"),
                        latitude = lat,
                        longitude = lng,
                        satellites = satellites,
                        hasValidGpsFix = hasFix,
                        urgency = urgency,
                        navCommand = navCommand,
                        isBeltFastened = beltWorn,
                        sosActive = sosActive,
                        altitudeMeters = json.optDouble("altitude", 215.0),
                        gpsTimeUtc = json.optString("gpsTimeUtc", utcTimeFormat.format(now)),
                        gpsDate = json.optString("gpsDate", utcDateFormat.format(now)),
                        speedKmh = speedKmh,
                        speedKnots = speedKnots,
                        courseDegrees = courseDeg,
                        fixStatusCode = fixStatusLabel,
                        hdop = estimatedHdop,
                        geoidSeparationMeters = json.optDouble("geoidSeparation", -34.8),
                        fixType = fixTypeLabel,
                        batteryPercent = json.optInt("batteryPercent", 88),
                        timestampMillis = nowMs
                    )
                }
            }.onFailure { err ->
                val latencyMs = System.currentTimeMillis() - startMs
                recordLog(
                    level = EspLogLevel.ERROR,
                    endpoint = "GET $endpointUrl",
                    latencyMs = latencyMs,
                    summary = "Connection Failed: ${err.javaClass.simpleName}",
                    details = buildExceptionDiagnosis(endpointUrl, err)
                )
            }
        }

    /**
     * Sends tactile steering guidance to the ESP32 belt:
     * `GET http://<ESP32_IP>/navigate?dir=left|right|forward|none`
     */
    suspend fun sendNavigationCommand(
        baseUrl: String,
        direction: NavDirection,
        isSimulator: Boolean
    ): Result<NavDirection> = withContext(Dispatchers.IO) {
        if (isSimulator) {
            simulatedNavCommand = direction
            recordLog(
                level = EspLogLevel.INFO,
                endpoint = "SIM /navigate?dir=${direction.dirParam}",
                latencyMs = 1L,
                summary = "Simulated navCommand = ${direction.dirParam}",
                details = direction.motorDescription
            )
            return@withContext Result.success(direction)
        }

        val normalizedBase = normalizeBaseUrl(baseUrl)
        val endpointUrl = "$normalizedBase/navigate?dir=${direction.dirParam}"
        val startMs = System.currentTimeMillis()

        runCatching {
            val request = Request.Builder()
                .url(endpointUrl)
                .header("Connection", "close")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startMs
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    recordLog(
                        level = EspLogLevel.ERROR,
                        endpoint = "GET $endpointUrl",
                        httpCode = response.code,
                        latencyMs = latencyMs,
                        summary = "/navigate failed with HTTP ${response.code}",
                        details = "Response: ${body.take(200)}"
                    )
                    throw IOException("ESP32 /navigate HTTP ${response.code}")
                }

                val confirmed = if (body.isNotBlank()) {
                    val json = JSONObject(body)
                    NavDirection.fromApi(json.optString("navCommand", direction.dirParam))
                } else {
                    direction
                }

                recordLog(
                    level = EspLogLevel.SUCCESS,
                    endpoint = "GET $endpointUrl",
                    httpCode = response.code,
                    latencyMs = latencyMs,
                    summary = "Confirmed navCommand = ${confirmed.dirParam}",
                    details = "Raw response: ${body.ifBlank { "(empty)" }}"
                )
                confirmed
            }
        }.onFailure { err ->
            val latencyMs = System.currentTimeMillis() - startMs
            recordLog(
                level = EspLogLevel.ERROR,
                endpoint = "GET $endpointUrl",
                latencyMs = latencyMs,
                summary = "/navigate failed: ${err.javaClass.simpleName}",
                details = buildExceptionDiagnosis(endpointUrl, err)
            )
        }
    }

    /**
     * Generates realistic NEO-6M GPS movement in Demo Simulator mode.
     * When [walkAlongRoute] is true, continuously advances the device position along
     * [activeRoutePoints] from source to destination.
     */
    fun nextSimulatedTelemetry(
        activeRoutePoints: List<LatLng>,
        walkAlongRoute: Boolean
    ): BeltTelemetry {
        stepCounter++
        val now = Date()

        val nextPos: LatLng
        val speedKmh: Double

        if (walkAlongRoute && activeRoutePoints.size >= 2) {
            val targetIdx = simulatedRouteWalkIndex.coerceIn(0, activeRoutePoints.lastIndex)
            val nextPoint = activeRoutePoints[targetIdx]
            val dist = GeoUtils.distanceMeters(simulatedLatLng, nextPoint)
            val baseBearing = if (dist > 0.3) {
                GeoUtils.bearingToTarget(simulatedLatLng, nextPoint).first.toDouble()
            } else {
                simulatedHeadingDeg
            }

            // Look ahead to upcoming waypoint to check if a street turn is ahead
            val lookAheadIdx = (targetIdx + 1).coerceAtMost(activeRoutePoints.lastIndex)
            val lookAheadBearing = if (lookAheadIdx > targetIdx) {
                GeoUtils.bearingToTarget(nextPoint, activeRoutePoints[lookAheadIdx]).first
            } else {
                baseBearing.toInt()
            }
            val naturalTurn = kotlin.math.abs(GeoUtils.signedTurnAngle(baseBearing, lookAheadBearing))

            // Simulate realistic patient gait: natural street turns + occasional gentle heading drift
            // so the demo logs clearly showcase Front, Left, and Right motor guidance along the route
            val driftOffset = when {
                targetIdx >= activeRoutePoints.lastIndex -> 0.0
                naturalTurn > 25.0 -> 0.0
                targetIdx == 1 -> 42.0 // Initial orientation correction -> Left Motor
                targetIdx % 6 == 3 -> -38.0 // Slight left drift -> Right Motor steers right
                targetIdx % 6 == 5 -> 38.0 // Slight right drift -> Left Motor steers left
                else -> 0.0 // On course -> Front Motor steers straight
            }
            simulatedHeadingDeg = ((baseBearing + driftOffset) % 360.0 + 360.0) % 360.0

            nextPos = nextPoint
            if (simulatedRouteWalkIndex < activeRoutePoints.lastIndex) {
                simulatedRouteWalkIndex++
                speedKmh = 4.2
            } else {
                speedKmh = 0.0
            }
        } else {
            // Stay anchored at the detected current device location (or arrived destination)
            nextPos = simulatedLatLng
            speedKmh = 0.0
        }

        simulatedLatLng = nextPos
        val speedKnots = speedKmh / 1.852
        val sats = 8 + (stepCounter % 4)
        val hdopValue = 0.85 + ((stepCounter % 5) * 0.05)
        val altitude = 216.0 + 0.4 * sin(stepCounter * 0.15)

        return BeltTelemetry(
            patientName = "CareBelt Device (Live Demo)",
            latitude = simulatedLatLng.latitude,
            longitude = simulatedLatLng.longitude,
            satellites = sats,
            hasValidGpsFix = true,
            urgency = SafetyUrgency.SAFE,
            navCommand = simulatedNavCommand,
            isBeltFastened = true,
            sosActive = false,
            altitudeMeters = altitude,
            gpsTimeUtc = utcTimeFormat.format(now),
            gpsDate = utcDateFormat.format(now),
            speedKmh = speedKmh,
            speedKnots = speedKnots,
            courseDegrees = simulatedHeadingDeg,
            fixStatusCode = "1 (Valid SPS Fix / A)",
            hdop = hdopValue,
            geoidSeparationMeters = -34.8,
            fixType = "3D Fix (Mode 3)",
            batteryPercent = (94 - (stepCounter / 80)).coerceAtLeast(35),
            timestampMillis = now.time
        )
    }

    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().removeSuffix("/")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
