package com.example.helloworld.data

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import com.example.helloworld.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationRepository(context: Context) : LocationListener {

    private val locationService = LocationService(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    fun startLocationUpdates() {
        locationService.startLocationUpdates(this)
    }

    fun stopLocationUpdates() {
        locationService.stopLocationUpdates()
    }

    override fun onLocationChanged(location: Location) {
        _location.value = location
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
