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

    /** True while a foreground-triggered location refresh is in progress. */
    private val _isRefreshingLocation = MutableStateFlow(false)
    val isRefreshingLocation: StateFlow<Boolean> = _isRefreshingLocation.asStateFlow()

    /** Incremented whenever location changes so the UI can re-key LaunchedEffects
     *  and trigger a fresh category fetch. */
    private val _locationVersion = MutableStateFlow(0)
    val locationVersion: StateFlow<Int> = _locationVersion.asStateFlow()

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

    /**
     * Called when the app returns to the foreground. Refreshes the device location
     * and clears the category cache only if the user has enabled device location.
     * Sets [isRefreshingLocation] to true for the duration so the UI can show an
     * indicator in the app bar.
     */
    fun refreshOnForeground() {
        viewModelScope.launch {
            val useDeviceLocation = userPreferencesRepository.useDeviceLocation.first()
            if (!useDeviceLocation) return@launch

            _isRefreshingLocation.value = true
            try {
                // Clear stale cache so a fresh location is fetched below
                cachedLocation = null
                locationDeferred = null
                _categoryResults.value = emptyMap()
                _categoryLoading.value = emptyMap()

                // Fetch a fresh location fix
                val deviceLocation = locationService.getBestLocationOrNull()
                val lat = deviceLocation?.latitude
                val lon = deviceLocation?.longitude

                cachedLocation = if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) {
                    lat to lon
                } else {
                    // Fall back to the user's saved default location
                    val defaultLocation = userPreferencesRepository.defaultLocation.first()
                    if (!defaultLocation.isNullOrBlank()) {
                        geocodingService.getCoordinates(defaultLocation) ?: (0.0 to 0.0)
                    } else {
                        0.0 to 0.0
                    }
                }

                // Bump the version so UI LaunchedEffects re-fire and categories reload
                _locationVersion.value += 1
            } finally {
                _isRefreshingLocation.value = false
            }
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
        // Bump version so any active UI LaunchedEffects re-fire and reload categories
        _locationVersion.value += 1
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
