package com.carebelt.tracker.data

import com.carebelt.tracker.model.BeltTelemetry
import com.carebelt.tracker.model.VibrationPattern
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

/**
 * Repository responsible for communicating with the ESP32 + NEO-6M smart belt
 * over Wi-Fi HTTP REST endpoints, or generating realistic telemetry in Demo Simulator mode.
 */
class BeltRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
) {
    private var stepCounter: Int = 0
    private var simulateWanderingOutOfZone: Boolean = false
    private var simulatedVibrationUntilMillis: Long = 0L

    fun setSimulateWandering(wanderingOut: Boolean) {
        simulateWanderingOutOfZone = wanderingOut
    }

    fun isSimulatingWandering(): Boolean = simulateWanderingOutOfZone

    /**
     * Fetches the latest NEO-6M GPS telemetry from the ESP32 belt endpoint:
     * `GET http://<esp32-ip>/location`
     *
     * Expected JSON response from ESP32:
     * ```json
     * {
     *   "latitude": 28.6139,
     *   "longitude": 77.2090,
     *   "speedKmh": 2.4,
     *   "satellites": 9,
     *   "hdop": 1.05,
     *   "altitude": 214.5,
     *   "courseDeg": 135.0,
     *   "hasFix": true,
     *   "batteryPercent": 86,
     *   "beltWorn": true,
     *   "sosActive": false,
     *   "vibrating": false
     * }
     * ```
     */
    suspend fun fetchEsp32Telemetry(baseUrl: String): Result<BeltTelemetry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = normalizeBaseUrl(baseUrl)
                val request = Request.Builder()
                    .url("$normalizedBase/location")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("ESP32 HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body?.string()
                        ?: throw IOException("Empty response from ESP32 /location")

                    val json = JSONObject(body)
                    BeltTelemetry(
                        patientName = json.optString("patientName", "Patient #1 (ESP32 Belt)"),
                        latitude = json.getDouble("latitude"),
                        longitude = json.getDouble("longitude"),
                        speedKmh = json.optDouble("speedKmh", 0.0),
                        satellites = json.optInt("satellites", 0),
                        hdop = json.optDouble("hdop", 1.5),
                        altitudeMeters = json.optDouble("altitude", 0.0),
                        courseDegrees = json.optDouble("courseDeg", 0.0),
                        hasValidGpsFix = json.optBoolean("hasFix", true),
                        batteryPercent = json.optInt("batteryPercent", 100),
                        isBeltFastened = json.optBoolean("beltWorn", true),
                        sosActive = json.optBoolean("sosActive", false),
                        isVibrating = json.optBoolean("vibrating", false),
                        timestampMillis = System.currentTimeMillis()
                    )
                }
            }
        }

    /**
     * Triggers the vibration motor on the ESP32 belt:
     * `POST http://<esp32-ip>/vibrate?pattern=<apiCode>&durationMs=<durationMs>`
     */
    suspend fun sendVibrationCommand(
        baseUrl: String,
        pattern: VibrationPattern,
        isSimulator: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isSimulator) {
            simulatedVibrationUntilMillis = System.currentTimeMillis() + pattern.durationMs
            return@withContext Result.success(
                "Simulated '${pattern.title}' vibration (${pattern.durationMs} ms)"
            )
        }

        runCatching {
            val normalizedBase = normalizeBaseUrl(baseUrl)
            val payload = JSONObject().apply {
                put("pattern", pattern.apiCode)
                put("durationMs", pattern.durationMs)
            }.toString()

            val request = Request.Builder()
                .url(
                    "$normalizedBase/vibrate?pattern=${pattern.apiCode}&durationMs=${pattern.durationMs}"
                )
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("ESP32 Vibration HTTP ${response.code}")
                }
                "Sent '${pattern.title}' to ESP32 belt"
            }
        }
    }

    /**
     * Generates realistic NEO-6M GPS movement around [homeLocation] so the user can test
     * Google Maps tracking, breadcrumb trails, and safe-zone alerts immediately.
     */
    fun nextSimulatedTelemetry(homeLocation: LatLng): BeltTelemetry {
        stepCounter++
        val angleRad = stepCounter * 0.16
        // 1 degree latitude is ~111,111 meters.
        // Normal stroll stays ~45m from home; wandering mode drifts ~230m away from home.
        val radiusMeters = if (simulateWanderingOutOfZone) {
            225.0 + 25.0 * sin(stepCounter * 0.08)
        } else {
            45.0 + 18.0 * sin(stepCounter * 0.1)
        }

        val deltaLat = (radiusMeters * cos(angleRad)) / 111_111.0
        val lonScale = cos(Math.toRadians(homeLocation.latitude)).coerceAtLeast(0.2)
        val deltaLon = (radiusMeters * sin(angleRad)) / (111_111.0 * lonScale)

        val now = System.currentTimeMillis()
        val heading = ((Math.toDegrees(angleRad) + 90.0) % 360.0 + 360.0) % 360.0

        return BeltTelemetry(
            patientName = "Patient #1 (Demo Belt)",
            latitude = homeLocation.latitude + deltaLat,
            longitude = homeLocation.longitude + deltaLon,
            speedKmh = if (simulateWanderingOutOfZone) 4.2 else 2.1,
            satellites = 9 + (stepCounter % 3),
            hdop = 0.95,
            altitudeMeters = 216.0,
            courseDegrees = heading,
            hasValidGpsFix = true,
            batteryPercent = (92 - (stepCounter / 60)).coerceAtLeast(25),
            isBeltFastened = true,
            sosActive = false,
            isVibrating = now < simulatedVibrationUntilMillis,
            timestampMillis = now
        )
    }

    private fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().removeSuffix("/")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
