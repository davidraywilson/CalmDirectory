package com.example.helloworld

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val backend: PlacesBackend = GooglePlacesApiService(application, userPreferencesRepository)
    private val locationService = LocationService(application)
    private val geocodingService = GoogleGeocodingService()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Poi>>(emptyList())
    val searchResults: StateFlow<List<Poi>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null
    private var cachedLocation: Pair<Double, Double>? = null
    private var locationDeferred: Deferred<Pair<Double, Double>>? = null

    init {
        prefetchLocation()

        viewModelScope.launch {
            userPreferencesRepository.useDeviceLocation
                .combine(userPreferencesRepository.defaultLocation) { _, _ -> }
                .collect {
                    invalidateLocation()
                }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.searchRadius,
                userPreferencesRepository.openNow,
                userPreferencesRepository.openIn1Hour,
                userPreferencesRepository.sortMode,
                userPreferencesRepository.maxPriceLevel,
            ) { _, _, _, _, _ -> Unit }
                .drop(1)
                .collect {
                    val q = _searchQuery.value
                    if (q.isNotBlank()) onSearchQueryChange(q)
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isNotBlank()) {
            _isLoading.value = true
            searchJob = viewModelScope.launch {
                delay(300L)
                try {
                    val locationStart = System.currentTimeMillis()
                    val (lat, lon) = getOrFetchLocation()
                    val locationDuration = System.currentTimeMillis() - locationStart
                    Log.d(
                        "SearchViewModel",
                        "query='${query.trim()}', lat=$lat, lon=$lon, locationTimeMs=$locationDuration"
                    )
                    val searchStart = System.currentTimeMillis()
                    val results = backend.search(query, lat, lon)
                    val searchDuration = System.currentTimeMillis() - searchStart
                    Log.d("SearchViewModel", "Search returned ${results.size} results in ${searchDuration} ms")
                    _searchResults.value = results
                } catch (e: SecurityException) {
                    Log.e("SearchViewModel", "Location permission not granted", e)
                    _searchResults.value = emptyList()
                } catch (e: CancellationException) {
                    Log.d("SearchViewModel", "Search cancelled", e)
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "Search failed", e)
                    _searchResults.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun resetSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isLoading.value = false
    }

    private suspend fun getOrFetchLocation(): Pair<Double, Double> {
        cachedLocation?.let { return it }

        val existing = locationDeferred
        if (existing != null && existing.isActive) {
            return existing.await()
        }

        val deferred = viewModelScope.async {
            val useDeviceLocation = userPreferencesRepository.useDeviceLocation.first()
            val location: Pair<Double, Double> = if (useDeviceLocation) {
                val deviceLocation = locationService.getBestLocationOrNull()
                val lat = deviceLocation?.latitude
                val lon = deviceLocation?.longitude

                if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) {
                    lat to lon
                } else {
                    val defaultLocation = userPreferencesRepository.defaultLocation.first()
                    if (!defaultLocation.isNullOrBlank()) {
                        geocodingService.getCoordinates(defaultLocation) ?: (0.0 to 0.0)
                    } else {
                        0.0 to 0.0
                    }
                }
            } else {
                val defaultLocation = userPreferencesRepository.defaultLocation.first()
                if (!defaultLocation.isNullOrBlank()) {
                    geocodingService.getCoordinates(defaultLocation) ?: (0.0 to 0.0)
                } else {
                    0.0 to 0.0
                }
            }
            cachedLocation = location
            location
        }
        locationDeferred = deferred
        return deferred.await()
    }

    private fun invalidateLocation() {
        cachedLocation = null
        locationDeferred = null
        prefetchLocation()
    }

    private fun prefetchLocation() {
        viewModelScope.launch {
            try {
                getOrFetchLocation()
            } catch (e: SecurityException) {
                Log.e("SearchViewModel", "Location permission not granted during prefetch", e)
            }
        }
    }

}