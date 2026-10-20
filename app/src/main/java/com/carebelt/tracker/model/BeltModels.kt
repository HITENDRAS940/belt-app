package com.carebelt.tracker.model

import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ultrasonic obstacle safety urgency level reported by `GET /data` ("urgency").
 */
enum class SafetyUrgency(val apiValue: String, val label: String) {
    SAFE("SAFE", "SAFE"),
    LOW("LOW", "LOW"),
    MEDIUM("MEDIUM", "MEDIUM"),
    HIGH("HIGH", "HIGH"),
    CRITICAL("CRITICAL", "CRITICAL");

    companion object {
        fun fromApi(value: String?): SafetyUrgency {
            return entries.firstOrNull {
                it.apiValue.equals(value?.trim(), ignoreCase = true)
            } ?: SAFE
        }
    }
}

/**
 * Tactile steering direction sent to `/navigate?dir=...` and reported in `GET /data` ("navCommand").
 */
enum class NavDirection(
    val dirParam: String,
    val title: String,
    val motorName: String,
    val motorPinLabel: String,
    val motorDescription: String
) {
    LEFT(
        dirParam = "left",
        title = "Turn Left",
        motorName = "LEFT MOTOR",
        motorPinLabel = "GPIO 25",
        motorDescription = "Left Motor (GPIO 25) VIBRATING"
    ),
    FORWARD(
        dirParam = "forward",
        title = "Head Straight",
        motorName = "FRONT MOTOR",
        motorPinLabel = "GPIO 32",
        motorDescription = "Front Motor (GPIO 32) VIBRATING"
    ),
    RIGHT(
        dirParam = "right",
        title = "Turn Right",
        motorName = "RIGHT MOTOR",
        motorPinLabel = "GPIO 33",
        motorDescription = "Right Motor (GPIO 33) VIBRATING"
    ),
    NONE(
        dirParam = "none",
        title = "Stop Guidance",
        motorName = "ALL MOTORS OFF",
        motorPinLabel = "IDLE",
        motorDescription = "All Motors OFF — Guidance stopped"
    );

    companion object {
        fun fromApi(value: String?): NavDirection {
            return entries.firstOrNull {
                it.dirParam.equals(value?.trim(), ignoreCase = true)
            } ?: NONE
        }
    }
}

/**
 * Represents a real-time log entry in the bottom panel showing which vibration motor
 * is currently vibrating and the tracked device coordinates along the route.
 */
data class MotorVibrationLogEntry(
    val id: Long,
    val timestamp: String,
    val activeMotor: NavDirection,
    val motorStatusTitle: String,
    val guidanceInstruction: String,
    val coordinatesText: String,
    val remainingDistanceMeters: Int? = null,
    val stepProgressText: String? = null,
    val isArrivalEvent: Boolean = false,
    val isSystemEvent: Boolean = false
)

/**
 * Represents the active route from the belt wearer's location to the destination pin set on the map,
 * along with real-time tactile guidance state.
 */
data class RouteGuidanceState(
    val sourceLocation: LatLng? = null,
    val destination: LatLng? = null,
    val routePoints: List<LatLng> = emptyList(),
    val isFetchingRoute: Boolean = false,
    val isAutoGuidanceEnabled: Boolean = true,
    val hasArrived: Boolean = false,
    val nextWaypointIndex: Int = 0,
    val nextWaypoint: LatLng? = null,
    val distanceToNextWaypointMeters: Double = 0.0,
    val totalDistanceRemainingMeters: Double = 0.0,
    val estimatedWalkMinutes: Int = 0,
    val bearingToTargetDeg: Int = 0,
    val bearingToTargetCardinal: String = "N",
    val turnAngleDegrees: Double = 0.0,
    val recommendedDirection: NavDirection = NavDirection.NONE,
    val guidanceHeadline: String = "Tap anywhere on the map to set a destination",
    val guidanceSubtext: String = "Current device location is detected • Select destination to start route & motor logs"
)

/**
 * Severity level for ESP32 connection diagnostic logs.
 */
enum class EspLogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

/**
 * Represents a structured diagnostic log entry for debugging ESP32 HTTP connectivity.
 */
data class EspConnectionLogEntry(
    val id: Long,
    val timestamp: String,
    val level: EspLogLevel,
    val endpoint: String,
    val httpCode: Int? = null,
    val latencyMs: Long? = null,
    val summary: String,
    val details: String
)

/**
 * Live telemetry payload received from `GET /data` on the ESP32 + NEO-6M smart belt,
 * including both the core JSON keys and the 12 NEO-6M GPS diagnostic parameters.
 */
data class BeltTelemetry(
    val patientName: String = "Patient #1 (CareBelt)",
    // Core `GET /data` JSON fields:
    // 1. Latitude (Degrees)
    val latitude: Double,
    // 2. Longitude (Degrees)
    val longitude: Double,
    // 8. Satellites used (Count)
    val satellites: Int = 4,
    // 9. Fix status boolean (`hasFix` from `GET /data`)
    val hasValidGpsFix: Boolean = true,
    // Ultrasonic safety urgency (`SAFE`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)
    val urgency: SafetyUrgency = SafetyUrgency.SAFE,
    // Active navigation cue (`none`, `left`, `right`, `forward`)
    val navCommand: NavDirection = NavDirection.NONE,
    // Clasp sensor (`true` if buckled LOW, `false` if unbuckled)
    val isBeltFastened: Boolean = true,
    // SOS button (`true` if emergency button pressed LOW)
    val sosActive: Boolean = false,

    // Extended 12-Parameter NEO-6M GPS Debug fields (reported by ESP32 or derived from NMEA/fix):
    // 3. Altitude (Meters above mean sea level)
    val altitudeMeters: Double = 216.0,
    // 4. GPS Time (UTC)
    val gpsTimeUtc: String = "09:12:00 UTC",
    // 5. GPS Date (YYYY-MM-DD)
    val gpsDate: String = "2026-10-19",
    // 6. Speed over ground (km/h and knots)
    val speedKmh: Double = 0.0,
    val speedKnots: Double = 0.0,
    // 7. Course over ground (Degrees)
    val courseDegrees: Double = 0.0,
    // 9. Fix status code description
    val fixStatusCode: String = "1 (Valid Fix / hasFix=true)",
    // 10. HDOP (Dimensionless)
    val hdop: Double = 1.10,
    // 11. Geoid separation (Meters)
    val geoidSeparationMeters: Double = -34.8,
    // 12. Fix type (2D or 3D fix)
    val fixType: String = "3D Fix (Mode 3)",

    val batteryPercent: Int = 88,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val latLng: LatLng
        get() = LatLng(latitude, longitude)

    val isVibrating: Boolean
        get() = navCommand != NavDirection.NONE ||
            urgency == SafetyUrgency.HIGH ||
            urgency == SafetyUrgency.CRITICAL
}

/**
 * Represents a single row in the 12-item NEO-6M GPS Debug Page.
 */
data class GpsDebugParameter(
    val number: Int,
    val name: String,
    val liveValue: String,
    val unit: String,
    val whatItTellsYou: String,
    val applicationInCareBelt: String,
    val isHealthy: Boolean = true
)

/**
 * Builds the 12 structured NEO-6M debug parameters from the current [BeltTelemetry].
 */
fun BeltTelemetry.toGpsDebugParameters(): List<GpsDebugParameter> = listOf(
    GpsDebugParameter(
        number = 1,
        name = "Latitude",
        liveValue = String.format(Locale.US, "%.6f°", latitude),
        unit = "Degrees",
        whatItTellsYou = "North–south geographic position",
        applicationInCareBelt = "Locate the patient on Google Maps",
        isHealthy = hasValidGpsFix
    ),
    GpsDebugParameter(
        number = 2,
        name = "Longitude",
        liveValue = String.format(Locale.US, "%.6f°", longitude),
        unit = "Degrees",
        whatItTellsYou = "East–west geographic position",
        applicationInCareBelt = "Locate the patient on Google Maps",
        isHealthy = hasValidGpsFix
    ),
    GpsDebugParameter(
        number = 3,
        name = "Altitude",
        liveValue = String.format(Locale.US, "%.1f m", altitudeMeters),
        unit = "Meters",
        whatItTellsYou = "Height above mean sea level",
        applicationInCareBelt = "Approximate elevation",
        isHealthy = hasValidGpsFix
    ),
    GpsDebugParameter(
        number = 4,
        name = "GPS time",
        liveValue = gpsTimeUtc,
        unit = "UTC",
        whatItTellsYou = "Current time reported by GPS",
        applicationInCareBelt = "Timestamp location readings",
        isHealthy = true
    ),
    GpsDebugParameter(
        number = 5,
        name = "GPS date",
        liveValue = gpsDate,
        unit = "Date",
        whatItTellsYou = "Current date reported by GPS",
        applicationInCareBelt = "Record location history",
        isHealthy = true
    ),
    GpsDebugParameter(
        number = 6,
        name = "Speed over ground",
        liveValue = String.format(
            Locale.US,
            "%.2f km/h (%.2f kn)",
            speedKmh,
            speedKnots
        ),
        unit = "Knots or km/h",
        whatItTellsYou = "How fast the device is moving",
        applicationInCareBelt = "Detect whether the patient is walking or moving quickly",
        isHealthy = speedKmh < 12.0
    ),
    GpsDebugParameter(
        number = 7,
        name = "Course over ground",
        liveValue = String.format(
            Locale.US,
            "%.1f° (%s)",
            courseDegrees,
            GeoUtils.degreesToCardinal(courseDegrees)
        ),
        unit = "Degrees",
        whatItTellsYou = "Direction of horizontal movement",
        applicationInCareBelt = "Estimate the direction the patient is travelling",
        isHealthy = true
    ),
    GpsDebugParameter(
        number = 8,
        name = "Satellites used",
        liveValue = "$satellites satellites",
        unit = "Count",
        whatItTellsYou = "Number of satellites contributing to the position fix",
        applicationInCareBelt = "Assess whether the GPS has enough satellite data",
        isHealthy = satellites >= 4
    ),
    GpsDebugParameter(
        number = 9,
        name = "Fix status",
        liveValue = fixStatusCode,
        unit = "Status code",
        whatItTellsYou = "Whether a valid position fix is available",
        applicationInCareBelt = "Avoid displaying invalid coordinates",
        isHealthy = hasValidGpsFix
    ),
    GpsDebugParameter(
        number = 10,
        name = "HDOP",
        liveValue = String.format(
            Locale.US,
            "%.2f (%s)",
            hdop,
            when {
                hdop <= 1.0 -> "Ideal"
                hdop <= 2.0 -> "Excellent"
                hdop <= 5.0 -> "Good"
                else -> "Moderate/Poor"
            }
        ),
        unit = "Dimensionless",
        whatItTellsYou = "Relative quality of horizontal satellite geometry",
        applicationInCareBelt = "Help assess location reliability",
        isHealthy = hdop <= 2.5
    ),
    GpsDebugParameter(
        number = 11,
        name = "Geoid separation",
        liveValue = String.format(Locale.US, "%.1f m", geoidSeparationMeters),
        unit = "Meters",
        whatItTellsYou = "Difference between the geoid and reference ellipsoid",
        applicationInCareBelt = "Interpret altitude measurements",
        isHealthy = true
    ),
    GpsDebugParameter(
        number = 12,
        name = "Fix type",
        liveValue = fixType,
        unit = "Status",
        whatItTellsYou = "Whether the receiver has a 2D or 3D fix",
        applicationInCareBelt = "Understand the type of position solution",
        isHealthy = hasValidGpsFix && (fixType.contains("3D") || fixType.contains("2D"))
    )
)

/**
 * Source from which the Kotlin app fetches belt GPS telemetry.
 */
enum class ConnectionMode(val label: String) {
    DEMO_SIMULATOR("Demo Simulator"),
    ESP32_HTTP("Live ESP32 (GET /data)")
}

/**
 * Geospatial and route-guidance utilities for CareBelt.
 */
object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val ARRIVAL_THRESHOLD_METERS = 15.0
    private const val WAYPOINT_ADVANCE_METERS = 12.0
    private val CARDINAL_DIRECTIONS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

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
    fun bearingToTarget(from: LatLng, to: LatLng): Pair<Int, String> {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        val deg = brng.roundToInt() % 360
        return deg to degreesToCardinal(deg.toDouble())
    }

    fun bearingToHome(from: LatLng, to: LatLng): Pair<Int, String> = bearingToTarget(from, to)

    fun degreesToCardinal(degrees: Double): String {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return CARDINAL_DIRECTIONS[((normalized + 22.5) / 45.0).toInt() % 8]
    }

    /**
     * Computes signed relative turn angle in (-180, 180] from [currentCourseDeg] to [targetBearingDeg].
     * Negative = Turn Left, Positive = Turn Right, Near 0 = Head Straight.
     */
    fun signedTurnAngle(currentCourseDeg: Double, targetBearingDeg: Int): Double {
        return ((targetBearingDeg - currentCourseDeg + 540.0) % 360.0) - 180.0
    }

    /**
     * Given the patient's current movement heading [currentCourseDeg] and the target
     * [targetBearingDeg], computes which tactile motor (`left`, `right`, or `forward`)
     * should pulse to steer the wearer along the route.
     */
    fun computeRecommendedNavDirection(
        currentCourseDeg: Double,
        targetBearingDeg: Int
    ): NavDirection {
        val diff = signedTurnAngle(currentCourseDeg, targetBearingDeg)
        return when {
            diff in -30.0..30.0 -> NavDirection.FORWARD
            diff < -30.0 -> NavDirection.LEFT
            else -> NavDirection.RIGHT
        }
    }

    /**
     * Evaluates the wearer's current position along [routePoints] toward [destination]
     * and returns an updated [RouteGuidanceState] with the next waypoint, remaining distance,
     * and the exact `/navigate?dir=left|right|forward|none` instruction.
     */
    fun evaluateRouteProgress(
        currentLocation: LatLng,
        currentCourseDeg: Double,
        previousState: RouteGuidanceState
    ): RouteGuidanceState {
        val destination = previousState.destination ?: return previousState
        val route = previousState.routePoints.ifEmpty { listOf(currentLocation, destination) }

        val distToDestination = distanceMeters(currentLocation, destination)
        if (distToDestination <= ARRIVAL_THRESHOLD_METERS) {
            val (bearingDeg, cardinal) = bearingToTarget(currentLocation, destination)
            return previousState.copy(
                hasArrived = true,
                nextWaypointIndex = route.lastIndex,
                nextWaypoint = destination,
                distanceToNextWaypointMeters = distToDestination,
                totalDistanceRemainingMeters = 0.0,
                estimatedWalkMinutes = 0,
                bearingToTargetDeg = bearingDeg,
                bearingToTargetCardinal = cardinal,
                turnAngleDegrees = 0.0,
                recommendedDirection = NavDirection.NONE,
                guidanceHeadline = "Arrived at Destination!",
                guidanceSubtext = "Belt guidance stopped (/navigate?dir=none)"
            )
        }

        // Find the closest waypoint at or ahead of our previous index, then pick the first waypoint
        // that is at least WAYPOINT_ADVANCE_METERS ahead so we always steer forward along the route
        var bestIdx = previousState.nextWaypointIndex.coerceIn(0, route.lastIndex)
        var minDistance = Double.MAX_VALUE
        for (i in 0..route.lastIndex) {
            val d = distanceMeters(currentLocation, route[i])
            if (d < minDistance) {
                minDistance = d
                bestIdx = i
            }
        }
        while (bestIdx < route.lastIndex &&
            distanceMeters(currentLocation, route[bestIdx]) < WAYPOINT_ADVANCE_METERS
        ) {
            bestIdx++
        }

        val targetWaypoint = route[bestIdx]
        val distToWaypoint = distanceMeters(currentLocation, targetWaypoint)

        // Sum remaining distance from currentLocation -> targetWaypoint -> remaining route segments
        var remainingMeters = distToWaypoint
        for (i in bestIdx until route.lastIndex) {
            remainingMeters += distanceMeters(route[i], route[i + 1])
        }

        val (bearingDeg, cardinal) = bearingToTarget(currentLocation, targetWaypoint)
        val turnAngle = signedTurnAngle(currentCourseDeg, bearingDeg)
        val recommendedDir = computeRecommendedNavDirection(currentCourseDeg, bearingDeg)
        val walkMinutes = (remainingMeters / 70.0).roundToInt().coerceAtLeast(1)

        val actionVerb = when (recommendedDir) {
            NavDirection.FORWARD -> "Head Straight ($cardinal, $bearingDeg°)"
            NavDirection.LEFT -> "Turn Left (${kotlin.math.abs(turnAngle.roundToInt())}° left → $cardinal)"
            NavDirection.RIGHT -> "Turn Right (${kotlin.math.abs(turnAngle.roundToInt())}° right → $cardinal)"
            NavDirection.NONE -> "Hold Position"
        }

        return previousState.copy(
            hasArrived = false,
            nextWaypointIndex = bestIdx,
            nextWaypoint = targetWaypoint,
            distanceToNextWaypointMeters = distToWaypoint,
            totalDistanceRemainingMeters = remainingMeters,
            estimatedWalkMinutes = walkMinutes,
            bearingToTargetDeg = bearingDeg,
            bearingToTargetCardinal = cardinal,
            turnAngleDegrees = turnAngle,
            recommendedDirection = recommendedDir,
            guidanceHeadline = "$actionVerb • ${distToWaypoint.roundToInt()}m to next point",
            guidanceSubtext = "Total remaining: ${remainingMeters.roundToInt()}m (~$walkMinutes min) • Belt cue: ?dir=${recommendedDir.dirParam}"
        )
    }

    /**
     * Decodes a Google Maps / OSRM encoded polyline string into a list of [LatLng] coordinates.
     */
    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    /**
     * Generates intermediate waypoints along a path so route guidance has smooth ~20m steps.
     */
    fun densifyRoute(points: List<LatLng>, maxStepMeters: Double = 20.0): List<LatLng> {
        if (points.size < 2) return points
        val result = mutableListOf<LatLng>()
        for (i in 0 until points.lastIndex) {
            val start = points[i]
            val end = points[i + 1]
            result.add(start)
            val dist = distanceMeters(start, end)
            if (dist > maxStepMeters) {
                val segments = (dist / maxStepMeters).toInt()
                for (s in 1 until segments) {
                    val fraction = s.toDouble() / segments.toDouble()
                    val lat = start.latitude + (end.latitude - start.latitude) * fraction
                    val lng = start.longitude + (end.longitude - start.longitude) * fraction
                    result.add(LatLng(lat, lng))
                }
            }
        }
        result.add(points.last())
        return result
    }

    /**
     * Builds a realistic street-grid path with two gentle turns between [origin] and [destination]
     * when online routing APIs are unreachable, so the demo shows Left, Forward, and Right motor cues.
     */
    fun buildFallbackStreetRoute(origin: LatLng, destination: LatLng): List<LatLng> {
        val totalDist = distanceMeters(origin, destination)
        if (totalDist < 25.0) return listOf(origin, destination)

        val dLat = destination.latitude - origin.latitude
        val dLng = destination.longitude - origin.longitude
        // Orthogonal offset to simulate walking along a street block with corners
        val perpLat = -dLng * 0.16
        val perpLng = dLat * 0.16

        val corner1 = LatLng(
            origin.latitude + dLat * 0.35 + perpLat,
            origin.longitude + dLng * 0.35 + perpLng
        )
        val corner2 = LatLng(
            origin.latitude + dLat * 0.72 - perpLat * 0.5,
            origin.longitude + dLng * 0.72 - perpLng * 0.5
        )
        return listOf(origin, corner1, corner2, destination)
    }

    /**
     * Densifies a route into an adaptive number of waypoints (around 15–28 steps) so continuous
     * demo tracking reaches the destination smoothly in ~20-35 seconds regardless of distance.
     */
    fun densifyRouteAdaptive(points: List<LatLng>, targetSteps: Int = 22): List<LatLng> {
        if (points.size < 2) return points
        var totalMeters = 0.0
        for (i in 0 until points.lastIndex) {
            totalMeters += distanceMeters(points[i], points[i + 1])
        }
        val adaptiveStepMeters = (totalMeters / targetSteps.coerceAtLeast(6))
            .coerceIn(8.0, 120.0)
        return densifyRoute(points, maxStepMeters = adaptiveStepMeters)
    }
}
