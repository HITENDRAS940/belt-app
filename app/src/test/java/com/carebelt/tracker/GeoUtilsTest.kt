package com.carebelt.tracker

import com.carebelt.tracker.model.GeoUtils
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
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
    fun `distanceMeters accurately detects safe zone exit`() {
        val home = LatLng(28.6139, 77.2090)
        // ~222 meters north of home
        val North222m = LatLng(28.6159, 77.2090)
        val distance = GeoUtils.distanceMeters(home, North222m)
        assertTrue("Expected ~222m, got $distance", distance in 215.0..230.0)

        val (deg, cardinal) = GeoUtils.bearingToHome(North222m, home)
        assertEquals("S", cardinal)
        assertTrue(deg in 175..185)
    }
}
