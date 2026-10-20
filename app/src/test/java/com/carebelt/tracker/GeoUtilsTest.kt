package com.carebelt.tracker

import com.carebelt.tracker.model.BeltTelemetry
import com.carebelt.tracker.model.GeoUtils
import com.carebelt.tracker.model.NavDirection
import com.carebelt.tracker.model.RouteGuidanceState
import com.carebelt.tracker.model.SafetyUrgency
import com.carebelt.tracker.model.toGpsDebugParameters
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun `distanceMeters returns zero for identical coordinates`() {
        val home = LatLng(28.6139, 77.2090)
        val distance = GeoUtils.distanceMeters(home, home)
        assertEquals(0.0, distance, 0.01)
    }

    @Test
    fun `distanceMeters and bearingToTarget compute accurate direction`() {
        val origin = LatLng(28.6139, 77.2090)
        val north222m = LatLng(28.6159, 77.2090)
        val distance = GeoUtils.distanceMeters(origin, north222m)
        assertTrue("Expected ~222m, got $distance", distance in 215.0..230.0)

        val (deg, cardinal) = GeoUtils.bearingToTarget(north222m, origin)
        assertEquals("S", cardinal)
        assertTrue(deg in 175..185)
    }

    @Test
    fun `toGpsDebugParameters returns all 12 NEO-6M GPS parameters`() {
        val sample = BeltTelemetry(
            latitude = 28.6139,
            longitude = 77.2090
        )
        val params = sample.toGpsDebugParameters()
        assertEquals(12, params.size)
        assertEquals("Latitude", params[0].name)
        assertEquals("Longitude", params[1].name)
        assertEquals("Altitude", params[2].name)
        assertEquals("GPS time", params[3].name)
        assertEquals("GPS date", params[4].name)
        assertEquals("Speed over ground", params[5].name)
        assertEquals("Course over ground", params[6].name)
        assertEquals("Satellites used", params[7].name)
        assertEquals("Fix status", params[8].name)
        assertEquals("HDOP", params[9].name)
        assertEquals("Geoid separation", params[10].name)
        assertEquals("Fix type", params[11].name)
    }

    @Test
    fun `computeRecommendedNavDirection steers left right or forward accurately`() {
        assertEquals(
            NavDirection.FORWARD,
            GeoUtils.computeRecommendedNavDirection(0.0, 10)
        )
        assertEquals(
            NavDirection.LEFT,
            GeoUtils.computeRecommendedNavDirection(0.0, 270)
        )
        assertEquals(
            NavDirection.RIGHT,
            GeoUtils.computeRecommendedNavDirection(0.0, 90)
        )
        assertEquals(SafetyUrgency.CRITICAL, SafetyUrgency.fromApi("CRITICAL"))
        assertEquals(NavDirection.LEFT, NavDirection.fromApi("left"))
    }

    @Test
    fun `evaluateRouteProgress guides along route and stops upon arrival`() {
        val start = LatLng(28.6139, 77.2090)
        val dest = LatLng(28.6159, 77.2090) // ~222m North
        val route = GeoUtils.densifyRoute(listOf(start, dest), maxStepMeters = 25.0)

        // Wearer is facing East (90 deg), but route goes North (0 deg) -> should command LEFT
        val progress = GeoUtils.evaluateRouteProgress(
            currentLocation = start,
            currentCourseDeg = 90.0,
            previousState = RouteGuidanceState(
                destination = dest,
                routePoints = route
            )
        )
        assertFalse(progress.hasArrived)
        assertEquals(NavDirection.LEFT, progress.recommendedDirection)
        assertTrue(progress.totalDistanceRemainingMeters > 200.0)

        // Wearer reaches destination -> should mark hasArrived = true and command NONE
        val arrived = GeoUtils.evaluateRouteProgress(
            currentLocation = dest,
            currentCourseDeg = 0.0,
            previousState = progress
        )
        assertTrue(arrived.hasArrived)
        assertEquals(NavDirection.NONE, arrived.recommendedDirection)
    }

    @Test
    fun `simulated walk from detected source to destination activates motors and reaches destination`() {
        val repository = com.carebelt.tracker.data.BeltRepository()
        val detectedSource = LatLng(28.6139, 77.2090)
        val destination = LatLng(28.6165, 77.2115)

        repository.setSimulatedDeviceLocation(detectedSource)
        repository.resetSimulatedRouteProgress()

        val fallbackStreet = GeoUtils.buildFallbackStreetRoute(detectedSource, destination)
        val route = GeoUtils.densifyRouteAdaptive(fallbackStreet, targetSteps = 16)
        assertTrue(route.size >= 8)

        var guidance = RouteGuidanceState(
            sourceLocation = detectedSource,
            destination = destination,
            routePoints = route
        )
        val observedMotors = mutableSetOf<NavDirection>()

        for (step in 0..route.size) {
            val telemetry = repository.nextSimulatedTelemetry(
                activeRoutePoints = route,
                walkAlongRoute = true
            )
            guidance = GeoUtils.evaluateRouteProgress(
                currentLocation = telemetry.latLng,
                currentCourseDeg = telemetry.courseDegrees,
                previousState = guidance
            )
            observedMotors.add(guidance.recommendedDirection)
            if (guidance.hasArrived) break
        }

        assertTrue("Device should arrive at destination", guidance.hasArrived)
        assertEquals(NavDirection.NONE, guidance.recommendedDirection)
        assertTrue(
            "Should vibrate directional motors along route",
            observedMotors.contains(NavDirection.FORWARD) &&
                (observedMotors.contains(NavDirection.LEFT) || observedMotors.contains(NavDirection.RIGHT))
        )
    }
}
