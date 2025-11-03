package com.example.helloworld

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locationService = LocationService(application)
    private val geocodingService = NominatimGeocodingService()
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val googlePlacesApiService = GooglePlacesApiService(application, userPreferencesRepository)

    private val _currentLocation = MutableStateFlow("Fetching location...")
    val currentLocation: StateFlow<String> = _currentLocation

    private val _locationSuggestions = MutableStateFlow<List<String>>(emptyList())
    val locationSuggestions: StateFlow<List<String>> = _locationSuggestions

    val useDeviceLocation = userPreferencesRepository.useDeviceLocation
    val defaultLocation = userPreferencesRepository.defaultLocation

    init {
        fetchLocation()
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            if (userPreferencesRepository.useDeviceLocation.first()) {
                if (ContextCompat.checkSelfPermission(
                        getApplication(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val location = locationService.getCurrentLocation()
                    if (location != null) {
                        val address = geocodingService.getAddress(location.latitude, location.longitude)
                        _currentLocation.value = address ?: "Address not found"
                    } else {
                        _currentLocation.value = "Location not available"
                    }
                } else {
                    _currentLocation.value = "Location permission not granted"
                }
            } else {
                _currentLocation.value = userPreferencesRepository.defaultLocation.first() ?: "No default location set"
            }
        }
    }

    fun setUseDeviceLocation(useDeviceLocation: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveUseDeviceLocation(useDeviceLocation)
            fetchLocation()
        }
    }

    fun setDefaultLocation(defaultLocation: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveDefaultLocation(defaultLocation)
            if (!userPreferencesRepository.useDeviceLocation.first()) {
                _currentLocation.value = defaultLocation
            }
        }
    }

    fun onDefaultLocationChange(query: String) {
        viewModelScope.launch {
            if (query.isNotEmpty()) {
                _locationSuggestions.value = googlePlacesApiService.getAutocompleteSuggestions(query)
            } else {
                _locationSuggestions.value = emptyList()
            }
        }
    }
}