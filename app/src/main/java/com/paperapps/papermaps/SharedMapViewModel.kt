package com.paperapps.papermaps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.navigation.base.route.NavigationRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharedMapState(
    val isMapVisible: Boolean = false,
    val cameraOptions: CameraOptions? = null,
    val markers: List<Point> = emptyList(),
    val navigationRoutes: List<NavigationRoute> = emptyList(),
    val paddingEnd: Double = 0.0,
    val paddingBottom: Double = 0.0
)

class SharedMapViewModel(application: Application) : AndroidViewModel(application) {
    private val _mapState = MutableStateFlow(SharedMapState())
    val mapState: StateFlow<SharedMapState> = _mapState.asStateFlow()

    fun showMap() {
        _mapState.value = _mapState.value.copy(isMapVisible = true)
    }

    fun hideMap() {
        _mapState.value = _mapState.value.copy(isMapVisible = false)
    }

    fun setCamera(cameraOptions: CameraOptions) {
        _mapState.value = _mapState.value.copy(cameraOptions = cameraOptions)
    }

    fun setMarkers(markers: List<Point>) {
        _mapState.value = _mapState.value.copy(markers = markers)
    }

    fun setRoutes(routes: List<NavigationRoute>) {
        _mapState.value = _mapState.value.copy(navigationRoutes = routes)
    }

    fun setPadding(end: Double, bottom: Double) {
        _mapState.value = _mapState.value.copy(paddingEnd = end, paddingBottom = bottom)
    }
}
