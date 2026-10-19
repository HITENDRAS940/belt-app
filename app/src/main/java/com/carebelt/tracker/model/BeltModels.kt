package com.carebelt.tracker.model

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live telemetry payload received from the ESP32 + NEO-6M smart belt.
 */
data class BeltTelemetry(
    val patientName: String = "Patient #1 (CareBelt)",
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double = 0.0,
    val satellites: Int = 8,
    val hdop: Double = 1.1,
    val altitudeMeters: Double = 215.0,
    val courseDegrees: Double = 0.0,
    val hasValidGpsFix: Boolean = true,
    val batteryPercent: Int = 88,
    val isBeltFastened: Boolean = true,
    val sosActive: Boolean = false,
    val isVibrating: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val latLng: LatLng
        get() = LatLng(latitude, longitude)
}

/**
 * Source from which the Kotlin app fetches belt GPS telemetry.
 */
enum class ConnectionMode(val label: String) {
    DEMO_SIMULATOR("Demo Simulator"),
    ESP32_HTTP("Live ESP32 (Wi-Fi HTTP)")
}

/**
 * Haptic vibration patterns supported by the ESP32 vibration motor driver.
 */
enum class VibrationPattern(
    val apiCode: String,
    val title: String,
    val subtitle: String,
    val durationMs: Int
) {
    GENTLE_CHECKIN(
        apiCode = "gentle_pulse",
        title = "Gentle Check-In",
        subtitle = "Soft reassuring double-pulse",
        durationMs = 1200
    ),
    RETURN_HOME_CUE(
        apiCode = "return_home",
        title = "Home Cue",
        subtitle = "Rhythmic pulse guiding back to safe zone",
        durationMs = 2000
    ),
    BOUNDARY_ALERT(
        apiCode = "boundary_alert",
        title = "Boundary Alert",
        subtitle = "3-pulse warning at safe-zone edge",
        durationMs = 2500
    ),
    FIND_BELT(
        apiCode = "find_belt",
        title = "Find Belt",
        subtitle = "Long pulse to locate belt or wearer",
        durationMs = 3500
    )
}

/**
 * Geofence Safe Zone configuration around the patient's home or care facility.
 */
data class SafeZoneConfig(
    val homeLocation: LatLng = LatLng(28.6139, 77.2090),
    val radiusMeters: Double = 150.0,
    val isEnabled: Boolean = true,
    val autoVibrateOnExit: Boolean = true
)

/**
 * Geospatial utilities for geofencing and directional home guidance.
 */
object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Computes great-circle distance in meters between two coordinates using the Haversine formula.
     */
    fun distanceMeters(from: LatLng, to: LatLng): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)

        val a = sin(dLat / 2).pow(2.0) +
            sin(dLon / 2).pow(2.0) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Computes compass bearing (0..360 deg) from [from] to [to] and a cardinal direction string.
     */
    fun bearingToHome(from: LatLng, to: LatLng): Pair<Int, String> {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        val deg = brng.roundToInt() % 360
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val cardinal = directions[((deg + 22.5) / 45.0).toInt() % 8]
        return deg to cardinal
    }
}
