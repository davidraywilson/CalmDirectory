package com.example.helloworld

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.SearchProvider
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locationService = LocationService(application)
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val googleGeocodingService = GoogleGeocodingService(userPreferencesRepository)
    private val geoapifyGeocodingService = GeoapifyGeocodingService(userPreferencesRepository)
    private val hereGeocodingService = HereGeocodingService(userPreferencesRepository)
    private val googleBackend: PlacesBackend = GooglePlacesApiService(application, userPreferencesRepository)
    private val geoapifyBackend: PlacesBackend = GeoapifyPlacesApiService(userPreferencesRepository)
    private val hereBackend: PlacesBackend = HerePlacesApiService(userPreferencesRepository)
    @Volatile
    private var currentBackend: PlacesBackend = googleBackend

    private val _currentLocation = MutableStateFlow("Fetching location...")
    val currentLocation: StateFlow<String> = _currentLocation

    private val _locationSuggestions = MutableStateFlow<List<String>>(emptyList())
    val locationSuggestions: StateFlow<List<String>> = _locationSuggestions

    private var autocompleteJob: Job? = null

    val useDeviceLocation = userPreferencesRepository.useDeviceLocation
    val defaultLocation = userPreferencesRepository.defaultLocation

    init {
        fetchLocation()

        viewModelScope.launch {
            userPreferencesRepository.searchProvider.collect { provider ->
                currentBackend = when (provider) {
                    SearchProvider.GOOGLE_PLACES -> googleBackend
                    SearchProvider.GEOAPIFY -> geoapifyBackend
                    SearchProvider.HERE -> hereBackend
                }
            }
        }
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            if (userPreferencesRepository.useDeviceLocation.first()) {
                _currentLocation.value = "Fetching location..."

                if (ContextCompat.checkSelfPermission(
                        getApplication(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val location = locationService.getBestLocationOrNull()
                    if (location != null) {
                        val provider = userPreferencesRepository.searchProvider.first()
                        val address = when (provider) {
                            SearchProvider.GOOGLE_PLACES ->
                                googleGeocodingService.getAddress(location.latitude, location.longitude)
                            SearchProvider.GEOAPIFY ->
                                geoapifyGeocodingService.getAddress(location.latitude, location.longitude)
                            SearchProvider.HERE ->
                                hereGeocodingService.getAddress(location.latitude, location.longitude)
                        }
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
        autocompleteJob?.cancel()
        if (query.isEmpty()) {
            _locationSuggestions.value = emptyList()
            return
        }
        autocompleteJob = viewModelScope.launch {
            delay(300L)
            _locationSuggestions.value = currentBackend.autocomplete(query)
        }
    }
}