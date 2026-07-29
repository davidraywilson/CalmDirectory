package com.example.helloworld

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.calmapps.calmmaps.R
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.UserPreferencesRepository
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class ScreenState {
    POI_OVERVIEW,
    ROUTE_PREVIEW
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(MapboxExperimental::class, ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    navController: NavController,
    poiName: String,
    poiAddress: String,
    isPlace: Boolean,
    poiLat: Double,
    poiLng: Double
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationService = remember { LocationService(context) }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val distanceUnit by userPreferencesRepository.distanceUnit.collectAsState(initial = DistanceUnit.IMPERIAL)
    val useDeviceLocation by userPreferencesRepository.useDeviceLocation.collectAsState(initial = true)
    val defaultLocation by userPreferencesRepository.defaultLocation.collectAsState(initial = null)
    val geocodingService = remember { GoogleGeocodingService() }

    var currentUserLocation by remember { mutableStateOf<android.location.Location?>(null) }
    LaunchedEffect(useDeviceLocation, defaultLocation) {
        currentUserLocation = if (useDeviceLocation) {
            locationService.getBestLocationOrNull()
        } else {
            val loc = defaultLocation
            if (loc != null) {
                val coords = geocodingService.getCoordinates(loc)
                coords?.let { (lat, lng) ->
                    android.location.Location("default").apply {
                        latitude = lat
                        longitude = lng
                    }
                }
            } else null
        }
    }

    val mapboxNavigation = remember(context) { NavigationManager.getInstance(context) }

    var screenState by remember { mutableStateOf(ScreenState.POI_OVERVIEW) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var originLabel by remember { mutableStateOf("Locating...") }
    var transportMode by remember { mutableStateOf(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC) }
    var routeDurationSeconds by remember { mutableStateOf(0.0) }
    // Session-only GPS override: true when the user taps "Use current location"
    // in the route preview. Does not change the global preference.
    var useActualLocationForSession by remember { mutableStateOf(false) }

    val markerBitmap = rememberMarkerBitmap()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    val routeLineColorResources = remember {
        RouteLineColorResources.Builder()
            .routeDefaultColor(AndroidColor.BLACK)
            .routeLineTraveledColor(AndroidColor.WHITE)
            .routeLowCongestionColor(AndroidColor.BLACK)
            .routeModerateCongestionColor(AndroidColor.BLACK)
            .routeHeavyCongestionColor(AndroidColor.BLACK)
            .routeSevereCongestionColor(AndroidColor.BLACK)
            .routeUnknownCongestionColor(AndroidColor.BLACK)
            .routeCasingColor(AndroidColor.BLACK)
            .alternativeRouteDefaultColor(AndroidColor.DKGRAY)
            .alternativeRouteCasingColor(AndroidColor.DKGRAY)
            .alternativeRouteLowCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteModerateCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteHeavyCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteSevereCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteUnknownCongestionColor(AndroidColor.DKGRAY)
            .inActiveRouteLegsColor(AndroidColor.DKGRAY)
            .build()
    }
    val routeLineApi = remember { MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build()) }
    val routeLineView = remember {
        MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(context)
                .routeLineColorResources(routeLineColorResources)
                .routeLineBelowLayerId("waterway-label")
                .build()
        )
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var navigationCamera by remember { mutableStateOf<NavigationCamera?>(null) }
    var viewportDataSource by remember { mutableStateOf<MapboxNavigationViewportDataSource?>(null) }
    var isStyleLoaded by remember { mutableStateOf(false) }

    fun fetchRoute() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val effectiveUseDevice = useDeviceLocation || useActualLocationForSession
                val originPoint: Point? = if (effectiveUseDevice) {
                    val bestLocation = locationService.getCurrentLocation()
                    if (bestLocation != null) {
                        originLabel = "Current location"
                        Point.fromLngLat(bestLocation.longitude, bestLocation.latitude)
                    } else {
                        originLabel = "Location unavailable"
                        null
                    }
                } else {
                    val loc = defaultLocation
                    if (loc != null) {
                        val coords = geocodingService.getCoordinates(loc)
                        if (coords != null) {
                            originLabel = loc
                            Point.fromLngLat(coords.second, coords.first)
                        } else {
                            originLabel = "Location unavailable"
                            null
                        }
                    } else {
                        errorMessage = "No default location set. Please configure one in Settings."
                        isLoading = false
                        return@launch
                    }
                }

                if (originPoint == null) {
                    errorMessage = "Could not determine location."
                    isLoading = false
                    return@launch
                }

                mapboxNavigation.requestRoutes(
                    RouteOptions.builder()
                        .applyDefaultNavigationOptions()
                        .applyLanguageAndVoiceUnitOptions(context)
                        .profile(transportMode)
                        .coordinatesList(listOf(originPoint, Point.fromLngLat(poiLng, poiLat)))
                        .build(),
                    object : NavigationRouterCallback {
                        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                            if (routes.isNotEmpty()) mapboxNavigation.setNavigationRoutes(routes)
                            else errorMessage = "No route found."
                            isLoading = false
                        }
                        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                            errorMessage = "Failed to fetch route."
                            isLoading = false
                        }
                        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                            isLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error finding location: ${e.message}"
                isLoading = false
            }
        }
    }

    // Re-fetch when location preference changes while a route is already previewed
    LaunchedEffect(useDeviceLocation, defaultLocation) {
        if (screenState == ScreenState.ROUTE_PREVIEW) fetchRoute()
    }

    fun resetCameraToPoi() {
        mapView?.getMapboxMap()?.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(poiLng, poiLat))
                .zoom(15.0)
                .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0))
                .build()
        )
    }

    fun clearRouteAndReturnToOverview() {
        mapboxNavigation.setNavigationRoutes(emptyList())
        routeLineApi.clearRouteLine { value ->
            mapView?.getMapboxMap()?.getStyle()?.let { style ->
                routeLineView.renderClearRouteLineValue(style, value)
            }
        }
        useActualLocationForSession = false
        screenState = ScreenState.POI_OVERVIEW
        resetCameraToPoi()
    }

    fun launchNavigation() {
        NavigationManager.lastTransportMode = transportMode
        NavigationManager.setNavigationActive(true)
        navController.navigate("navigation_active?lat=${poiLat.toFloat()}&lng=${poiLng.toFloat()}")
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) launchNavigation()
        else errorMessage = "Location permission is required for turn-by-turn navigation."
    }

    fun requestNavigationStart() {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) launchNavigation()
        else locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    BackHandler(enabled = true) {
        when (screenState) {
            ScreenState.ROUTE_PREVIEW -> clearRouteAndReturnToOverview()
            ScreenState.POI_OVERVIEW -> navController.popBackStack()
        }
    }

    if (mapView != null && isStyleLoaded) {
        DisposableEffect(mapboxNavigation, mapView!!, isStyleLoaded) {
            val mapboxMap = mapView!!.getMapboxMap()

            if (viewportDataSource == null || navigationCamera == null) {
                viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap).apply {
                    val d = context.resources.displayMetrics.density.toDouble()
                    followingPadding = EdgeInsets(120.0 * d, 40.0 * d, 120.0 * d, 40.0 * d)
                    overviewPadding = EdgeInsets(100.0 * d, 100.0 * d, 100.0 * d, 100.0 * d)
                }
                navigationCamera = NavigationCamera(mapboxMap, mapView!!.camera, viewportDataSource!!)
                mapboxMap.setCamera(
                    CameraOptions.Builder().center(Point.fromLngLat(poiLng, poiLat)).zoom(15.0).build()
                )
            }

            val routesObserver = RoutesObserver { result ->
                routeLineApi.setNavigationRoutes(result.navigationRoutes) { drawData ->
                    mapView?.getMapboxMap()?.getStyle()?.let { style ->
                        routeLineView.renderRouteDrawData(style, drawData)
                    }
                }
                if (result.navigationRoutes.isNotEmpty()) {
                    routeDurationSeconds = result.navigationRoutes[0].directionsRoute.duration()
                    viewportDataSource?.onRouteChanged(result.navigationRoutes[0])
                    viewportDataSource?.evaluate()
                    if (screenState == ScreenState.POI_OVERVIEW) {
                        screenState = ScreenState.ROUTE_PREVIEW
                    }
                    navigationCamera?.requestNavigationCameraToOverview()
                } else {
                    routeDurationSeconds = 0.0
                }
            }

            mapboxNavigation.registerRoutesObserver(routesObserver)

            onDispose {
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                if (!NavigationManager.isNavigationActive.value) {
                    mapboxNavigation.setNavigationRoutes(emptyList())
                }
                routeLineApi.clearRouteLine { }
                routeLineApi.cancel()
                routeLineView.cancel()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarHeight, bottom = bottomBarHeight)
                .zIndex(0f)
        ) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                compass = { },
                scaleBar = { },
                logo = { Logo(modifier = Modifier.align(Alignment.BottomStart)) },
                attribution = { Attribution(modifier = Modifier.align(Alignment.BottomEnd)) }
            ) {
                MapEffect(Unit) { mapViewInstance ->
                    mapView = mapViewInstance
                    mapViewInstance.getMapboxMap().apply {
                        setCamera(CameraOptions.Builder().center(Point.fromLngLat(poiLng, poiLat)).zoom(15.0).build())
                        loadStyleUri(mapViewInstance.context.getString(R.string.mapbox_eink_style_uri)) { style ->
                            isStyleLoaded = true
                            style.styleLayers.forEach { layer ->
                                when (layer.type) {
                                    "background" -> style.setStyleLayerProperty(layer.id, "background-color", Value.valueOf("#FFFFFF"))
                                    "line" -> if (layer.id.startsWith("road")) {
                                        val color = if (layer.id.contains("motorway") || layer.id.contains("trunk")) "#222222" else "#777777"
                                        style.setStyleLayerProperty(layer.id, "line-color", Value.valueOf(color))
                                    }
                                    "symbol" -> if (layer.id.contains("label")) style.setStyleLayerProperty(layer.id, "text-color", Value.valueOf("#000000"))
                                }
                            }
                        }
                    }
                }
                if (markerBitmap != null) {
                    PointAnnotation(point = Point.fromLngLat(poiLng, poiLat)) {
                        iconImage = IconImage(markerBitmap)
                        iconAnchor = IconAnchor.BOTTOM
                        iconSize = 1.0
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
        ) {
            // Top bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { topBarHeight = with(density) { it.size.height.toDp() } },
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        IconButton(onClick = {
                            when (screenState) {
                                ScreenState.POI_OVERVIEW -> navController.popBackStack()
                                ScreenState.ROUTE_PREVIEW -> clearRouteAndReturnToOverview()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (screenState == ScreenState.ROUTE_PREVIEW || isLoading) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.RadioButtonUnchecked, "Origin", tint = MaterialTheme.colorScheme.onSurface)
                                    VerticalDottedLine(modifier = Modifier.width(2.dp).height(32.dp))
                                    Icon(Icons.Outlined.Place, "Destination", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(originLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    if (!useDeviceLocation && !useActualLocationForSession) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .clickable {
                                                    useActualLocationForSession = true
                                                    fetchRoute()
                                                }
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.MyLocation,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Use current location",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    HorizontalDividerMMD(
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                    Text(poiName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 16.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (isPlace) {
                                    Text(poiName, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(poiAddress, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    currentUserLocation?.let { loc ->
                                        val results = FloatArray(1)
                                        android.location.Location.distanceBetween(loc.latitude, loc.longitude, poiLat, poiLng, results)
                                        val d = results[0]
                                        val label = if (distanceUnit == DistanceUnit.IMPERIAL) {
                                            val mi = d * 0.000621371
                                            if (mi >= 0.1) "%.1f mi".format(mi) else "%.0f ft".format(d * 3.28084)
                                        } else {
                                            if (d >= 1000) "%.1f km".format(d / 1000) else "%.0f m".format(d)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                } else {
                                    Text(poiName, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    currentUserLocation?.let { loc ->
                                        val results = FloatArray(1)
                                        android.location.Location.distanceBetween(loc.latitude, loc.longitude, poiLat, poiLng, results)
                                        val d = results[0]
                                        val label = if (distanceUnit == DistanceUnit.IMPERIAL) {
                                            val mi = d * 0.000621371
                                            if (mi >= 0.1) "%.1f mi".format(mi) else "%.0f ft".format(d * 3.28084)
                                        } else {
                                            if (d >= 1000) "%.1f km".format(d / 1000) else "%.0f m".format(d)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDividerMMD(
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.fillMaxWidth().weight(1f))

            // Bottom bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { bottomBarHeight = with(density) { it.size.height.toDp() } },
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDividerMMD(thickness = 3.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    if (screenState == ScreenState.ROUTE_PREVIEW) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                val isDriving = transportMode == DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
                                IconButton(
                                    onClick = { transportMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC; fetchRoute() },
                                    modifier = Modifier.size(40.dp)
                                        .background(if (isDriving) MaterialTheme.colorScheme.onSurface else ComposeColor.Transparent, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.DirectionsCar, "Driving",
                                        tint = if (isDriving) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp))
                                }
                                val isCycling = transportMode == DirectionsCriteria.PROFILE_CYCLING
                                IconButton(
                                    onClick = { transportMode = DirectionsCriteria.PROFILE_CYCLING; fetchRoute() },
                                    modifier = Modifier.size(40.dp)
                                        .background(if (isCycling) MaterialTheme.colorScheme.onSurface else ComposeColor.Transparent, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.DirectionsBike, "Cycling",
                                        tint = if (isCycling) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp))
                                }
                                val isWalking = transportMode == DirectionsCriteria.PROFILE_WALKING
                                IconButton(
                                    onClick = { transportMode = DirectionsCriteria.PROFILE_WALKING; fetchRoute() },
                                    modifier = Modifier.size(40.dp)
                                        .background(if (isWalking) MaterialTheme.colorScheme.onSurface else ComposeColor.Transparent, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.DirectionsWalk, "Walking",
                                        tint = if (isWalking) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp))
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (routeDurationSeconds > 0 && !isLoading) {
                                val totalSec = routeDurationSeconds.toLong()
                                val hours = TimeUnit.SECONDS.toHours(totalSec)
                                val minutes = TimeUnit.SECONDS.toMinutes(totalSec) % 60
                                val durationLabel = buildString {
                                    if (hours > 0) append("$hours hr ")
                                    append("$minutes min")
                                }
                                val cal = Calendar.getInstance().also { it.add(Calendar.SECOND, totalSec.toInt()) }
                                val etaLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(durationLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(etaLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        val canNavigate = useDeviceLocation || useActualLocationForSession
                        OutlinedButtonMMD(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                when (screenState) {
                                    ScreenState.POI_OVERVIEW -> fetchRoute()
                                    ScreenState.ROUTE_PREVIEW -> if (canNavigate) requestNavigationStart()
                                }
                            },
                            enabled = !isLoading && errorMessage == null &&
                                (screenState == ScreenState.POI_OVERVIEW || canNavigate),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Outlined.LocationSearching else Icons.Outlined.Directions,
                                    contentDescription = "Directions",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp).padding(end = 8.dp)
                                )

                                Column {
                                    Text(
                                        text = if (isLoading) {
                                            if (canNavigate) "Getting your location..." else "Getting directions..."
                                        } else {
                                            when (screenState) {
                                                ScreenState.POI_OVERVIEW -> "Get Directions"
                                                ScreenState.ROUTE_PREVIEW -> if (canNavigate) "Start Navigation" else "Preview only"
                                            }
                                        },
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (screenState == ScreenState.ROUTE_PREVIEW && !canNavigate) {
                                        Text(
                                            text = "Current location required",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }

        if (!isStyleLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicatorMMD()
            }
        }
    }
}

@Composable
private fun VerticalDottedLine(
    modifier: Modifier = Modifier,
    color: ComposeColor = MaterialTheme.colorScheme.outlineVariant
) {
    Canvas(modifier = modifier) {
        val dotRadius = 2.dp.toPx()
        val spacing = 4.dp.toPx()
        val centerX = size.width / 2f
        val dotDiameter = dotRadius * 2
        var y = dotDiameter
        val maxY = size.height - dotDiameter
        while (y <= maxY) {
            drawCircle(color = color, radius = dotRadius, center = Offset(centerX, y))
            y += dotDiameter + spacing
        }
    }
}

@Composable
fun rememberMarkerBitmap(): Bitmap? {
    val context = LocalContext.current
    return remember(context) {
        val width = 64
        val height = 76
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = AndroidColor.WHITE }
        val strokePaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 4f
            color = AndroidColor.BLACK; strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = AndroidColor.BLACK }
        val cx = width / 2f
        val cy = width / 2f - 4f
        val radius = 24f
        val path = Path()
        path.arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 150f, 240f)
        path.lineTo(cx, height - 4f)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(cx, cy, 8f, dotPaint)
        bitmap
    }
}
