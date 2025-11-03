package com.example.helloworld

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val placesApiService = GooglePlacesApiService(application, userPreferencesRepository)
    private val locationService = LocationService(application)
    private val nominatimGeocodingService = NominatimGeocodingService()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Poi>>(emptyList())
    val searchResults: StateFlow<List<Poi>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            _isLoading.value = true
            viewModelScope.launch {
                try {
                    val useDeviceLocation = userPreferencesRepository.useDeviceLocation.first()
                    var lat = 0.0
                    var lon = 0.0

                    if (useDeviceLocation) {
                        val location = locationService.getCurrentLocation()
                        lat = location?.latitude ?: 0.0
                        lon = location?.longitude ?: 0.0
                    } else {
                        val defaultLocation = userPreferencesRepository.defaultLocation.first()
                        if (!defaultLocation.isNullOrBlank()) {
                            val coordinates = nominatimGeocodingService.getCoordinates(defaultLocation)
                            lat = coordinates?.first ?: 0.0
                            lon = coordinates?.second ?: 0.0
                        }
                    }

                    val results = placesApiService.search(query, lat, lon)
                    _searchResults.value = results
                } catch (e: SecurityException) {
                    Log.e("SearchViewModel", "Location permission not granted", e)
                    // Handle the case where permission is not granted, e.g., show a message to the user
                    _searchResults.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
        }
    }
}