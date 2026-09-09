package com.paperapps.papermaps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperapps.papermaps.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val apiKey: StateFlow<String?> = MutableStateFlow(BuildConfig.HERE_API_KEY.ifEmpty { null })
    
    private val _sortOption = MutableStateFlow(SortOption.DISTANCE)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()


    private val userPreferencesRepository = com.paperapps.papermaps.data.UserPreferencesRepository(application)
    private val backend: PlacesBackend = GooglePlacesApiService(userPreferencesRepository)
    private val locationService = LocationService(application)
    private val geocodingService = GoogleGeocodingService(userPreferencesRepository)

    private val _categoryResults = MutableStateFlow<Map<String, List<Poi>>>(emptyMap())
    val categoryResults: StateFlow<Map<String, List<Poi>>> = _categoryResults.asStateFlow()

    private val _categoryLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val categoryLoading: StateFlow<Map<String, Boolean>> = _categoryLoading.asStateFlow()

    private var cachedLocation: Pair<Double, Double>? = null
    private var locationDeferred: kotlinx.coroutines.Deferred<Pair<Double, Double>>? = null

    init {
        prefetchLocation()

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                userPreferencesRepository.useDeviceLocation,
                userPreferencesRepository.defaultLocation,
                userPreferencesRepository.searchRadius
            ) { _, _, _ -> }.collect {
                invalidateLocation()
            }
        }
    }

    fun setSortOption(option: SortOption) {
        if (_sortOption.value != option) {
            _sortOption.value = option
            _categoryResults.value = emptyMap() // Clear cache to trigger re-fetch
        }
    }

    fun fetchCategory(category: String) {
        if (_categoryLoading.value[category] == true || _categoryResults.value.containsKey(category)) {
            return // Already loading or loaded
        }
        _categoryLoading.value = _categoryLoading.value + (category to true)
        
        viewModelScope.launch {
            try {
                val (lat, lon) = getOrFetchLocation()

                val searchQuery = when (category) {
                    "eat+drink" -> "category:restaurant,cafe,bar"
                    "see+do" -> "category:tourist_attraction,museum,park"
                    "shop" -> "category:shopping_mall,store"
                    "hotels" -> "category:hotel"
                    else -> category
                }
                
                val results = backend.search(searchQuery, lat, lon, _sortOption.value)
                _categoryResults.value = _categoryResults.value + (category to results)
            } catch (e: Exception) {
                _categoryResults.value = _categoryResults.value + (category to emptyList())
            } finally {
                _categoryLoading.value = _categoryLoading.value + (category to false)
            }
        }
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
        _categoryResults.value = emptyMap()
        _categoryLoading.value = emptyMap()
        prefetchLocation()
    }

    private fun prefetchLocation() {
        viewModelScope.launch {
            try {
                getOrFetchLocation()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
