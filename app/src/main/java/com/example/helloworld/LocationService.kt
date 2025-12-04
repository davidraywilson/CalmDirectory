package com.example.helloworld

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationService(private val context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var locationListener: LocationListener? = null

    /**
     * Returns the best available location using a two-step strategy:
     * 1. Use the newest last-known location from GPS or NETWORK providers, if present.
     * 2. Fallback to actively requesting a fresh location fix.
     */
    suspend fun getBestLocationOrNull(): Location? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).mapNotNull { provider ->
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.getLastKnownLocation(provider)
            } else {
                null
            }
        }

        val newestLastKnown = candidates.maxByOrNull { it.time }
        if (newestLastKnown != null) {
            return newestLastKnown
        }

        return getCurrentLocation()
    }

    suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        // Prefer GPS provider for highest accuracy
        // but gracefully fall back to NETWORK provider when GPS is disabled or unavailable.
        val provider: String = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context)
            ) { location: Location? ->
                continuation.resume(location)
            }
        }
    }

    fun startLocationUpdates(listener: LocationListener) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationListener = listener
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L, // 5 seconds
                10f,   // 10 meters
                listener
            )
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L, // 5 seconds
                10f,   // 10 meters
                listener
            )
        }
    }

    fun stopLocationUpdates() {
        locationListener?.let {
            locationManager.removeUpdates(it)
        }
        locationListener = null
    }
}