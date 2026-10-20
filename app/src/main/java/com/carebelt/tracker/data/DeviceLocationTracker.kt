package com.carebelt.tracker.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng

/**
 * Snapshot of the Android device's own GPS / Network location fix.
 */
data class DeviceLocationFix(
    val latLng: LatLng,
    val altitudeMeters: Double,
    val speedKmh: Double,
    val bearingDegrees: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timestampMillis: Long
)

/**
 * Detects and continuously tracks the current location of the Android device
 * using [LocationManager] across GPS, Network, and Fused providers.
 */
class DeviceLocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeListener: LocationListener? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Immediately retrieves the best last-known location of the device across all enabled providers.
     */
    @SuppressLint("MissingPermission")
    fun getBestLastKnownLocation(): DeviceLocationFix? {
        if (!hasLocationPermission()) return null
        val manager = locationManager ?: return null

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        var bestLocation: Location? = null
        for (provider in providers) {
            val candidate = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                ?: continue
            if (bestLocation == null || isBetterLocation(candidate, bestLocation)) {
                bestLocation = candidate
            }
        }

        return bestLocation?.toDeviceLocationFix()
    }

    /**
     * Starts continuous location tracking on the Android device and invokes [onLocationChanged]
     * whenever the device's location updates.
     */
    @SuppressLint("MissingPermission")
    fun startContinuousTracking(
        minTimeMs: Long = 1_000L,
        minDistanceMeters: Float = 0.0f,
        onLocationChanged: (DeviceLocationFix) -> Unit
    ): Boolean {
        if (!hasLocationPermission()) return false
        val manager = locationManager ?: return false

        stopContinuousTracking()

        // Emit immediate last-known location if available so the map jumps right away
        getBestLastKnownLocation()?.let(onLocationChanged)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationChanged(location.toDeviceLocationFix())
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        activeListener = listener

        val providersToRegister = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }

        var registeredAny = false
        for (provider in providersToRegister) {
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    minTimeMs,
                    minDistanceMeters,
                    listener,
                    Looper.getMainLooper()
                )
                registeredAny = true
            }
        }

        return registeredAny
    }

    @SuppressLint("MissingPermission")
    fun stopContinuousTracking() {
        val listener = activeListener ?: return
        runCatching {
            locationManager?.removeUpdates(listener)
        }
        activeListener = null
    }

    private fun isBetterLocation(candidate: Location, currentBest: Location): Boolean {
        val timeDelta = candidate.time - currentBest.time
        val isSignificantlyNewer = timeDelta > 60_000L
        val isSignificantlyOlder = timeDelta < -60_000L
        val isNewer = timeDelta > 0L

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val accuracyDelta = (candidate.accuracy - currentBest.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 100

        return when {
            isMoreAccurate -> true
            isNewer && !isLessAccurate -> true
            isNewer && !isSignificantlyLessAccurate -> true
            else -> false
        }
    }

    private fun Location.toDeviceLocationFix(): DeviceLocationFix {
        val speedMps = if (hasSpeed()) speed.toDouble() else 0.0
        val bearingDeg = if (hasBearing()) bearing.toDouble() else 0.0
        val alt = if (hasAltitude()) altitude else 216.0
        val acc = if (hasAccuracy()) accuracy else 10.0f
        return DeviceLocationFix(
            latLng = LatLng(latitude, longitude),
            altitudeMeters = alt,
            speedKmh = speedMps * 3.6,
            bearingDegrees = bearingDeg,
            accuracyMeters = acc,
            provider = provider ?: "gps",
            timestampMillis = if (time > 0L) time else System.currentTimeMillis()
        )
    }
}
