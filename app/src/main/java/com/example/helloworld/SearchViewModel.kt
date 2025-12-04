package com.example.helloworld

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.SearchProvider
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val googleBackend: PlacesBackend = GooglePlacesApiService(application, userPreferencesRepository)
    private val geoapifyBackend: PlacesBackend = GeoapifyPlacesApiService(userPreferencesRepository)
    private val hereBackend: PlacesBackend = HerePlacesApiService(userPreferencesRepository)
    @Volatile
    private var currentBackend: PlacesBackend = googleBackend
    private val locationService = LocationService(application)
    private val googleGeocodingService = GoogleGeocodingService(userPreferencesRepository)
    private val geoapifyGeocodingService = GeoapifyGeocodingService(userPreferencesRepository)
    private val hereGeocodingService = HereGeocodingService(userPreferencesRepository)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Poi>>(emptyList())
    val searchResults: StateFlow<List<Poi>> = _searchResults

    // Only used when Geoapify is the active backend to scope free-text searches
    // to a chosen top-level Geoapify category.
    private val _geoapifyTopLevelCategory = MutableStateFlow<String?>(null)

    fun setGeoapifyTopLevelCategory(category: String?) {
        _geoapifyTopLevelCategory.value = category?.takeIf { it.isNotBlank() }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null
    private var cachedLocation: Pair<Double, Double>? = null
    private var locationDeferred: Deferred<Pair<Double, Double>>? = null

    init {
        // Warm up location cache once when the ViewModel is created.
        prefetchLocation()

        // Observe preference changes to automatically invalidate the location cache.
        viewModelScope.launch {
            // Any change in either location-related preference will trigger a cache invalidation.
            userPreferencesRepository.useDeviceLocation
                .combine(userPreferencesRepository.defaultLocation) { _, _ -> }
                .collect {
                    invalidateLocation()
                }
        }

        // Observe search provider changes to switch backends dynamically.
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isNotBlank()) {
            _isLoading.value = true
            searchJob = viewModelScope.launch {
                delay(300L) // Debounce for 300 milliseconds
                try {
                    val locationStart = System.currentTimeMillis()
                    val (lat, lon) = getOrFetchLocation()
                    val locationDuration = System.currentTimeMillis() - locationStart
                    Log.d(
                        "SearchViewModel",
                        "query='${query.trim()}', lat=$lat, lon=$lon, backend=${currentBackend.javaClass.simpleName}, locationTimeMs=$locationDuration"
                    )
                    val searchStart = System.currentTimeMillis()
                    val backend = currentBackend
                    val results = when (backend) {
                        is GeoapifyPlacesApiService -> {
                            backend.setTopLevelCategory(_geoapifyTopLevelCategory.value)
                            backend.search(query, lat, lon)
                        }
                        else -> backend.search(query, lat, lon)
                    }
                    val searchDuration = System.currentTimeMillis() - searchStart
                    Log.d("SearchViewModel", "Search returned ${results.size} results in ${searchDuration} ms")
                    _searchResults.value = results
                } catch (e: SecurityException) {
                    Log.e("SearchViewModel", "Location permission not granted", e)
                    _searchResults.value = emptyList()
                } catch (e: CancellationException) {
                    // Expected when a newer query cancels the previous one
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
                    // Fallback to default location if configured.
                    val defaultLocation = userPreferencesRepository.defaultLocation.first()
                    if (!defaultLocation.isNullOrBlank()) {
                        val provider = userPreferencesRepository.searchProvider.first()
                        val coords = when (provider) {
                            SearchProvider.GOOGLE_PLACES ->
                                googleGeocodingService.getCoordinates(defaultLocation)
                            SearchProvider.GEOAPIFY ->
                                geoapifyGeocodingService.getCoordinates(defaultLocation)
                            SearchProvider.HERE ->
                                hereGeocodingService.getCoordinates(defaultLocation)
                        }
                        coords ?: (0.0 to 0.0)
                    } else {
                        0.0 to 0.0
                    }
                }
            } else {
                val defaultLocation = userPreferencesRepository.defaultLocation.first()
                if (!defaultLocation.isNullOrBlank()) {
                    val provider = userPreferencesRepository.searchProvider.first()
                    val coords = when (provider) {
                        SearchProvider.GOOGLE_PLACES ->
                            googleGeocodingService.getCoordinates(defaultLocation)
                        SearchProvider.GEOAPIFY ->
                            geoapifyGeocodingService.getCoordinates(defaultLocation)
                        SearchProvider.HERE ->
                            hereGeocodingService.getCoordinates(defaultLocation)
                    }
                    coords ?: (0.0 to 0.0)
                } else {
                    0.0 to 0.0
                }
            }
            if (location.first == 0.0 && location.second == 0.0) {
                Log.w(
                    "SearchViewModel",
                    "Using fallback location (0,0); no valid device or default location available"
                )
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