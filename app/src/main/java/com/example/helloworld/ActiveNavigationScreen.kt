package com.example.helloworld

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Clear
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.calmapps.calmmaps.R
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.UserPreferencesRepository
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.bindgen.Value
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxLaneGuidance
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(MapboxExperimental::class, ExperimentalMaterial3Api::class)
@Composable
fun ActiveNavigationScreen(
    navController: NavController,
    poiLat: Double,
    poiLng: Double
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPipMode = LocalPipMode.current
    val scope = rememberCoroutineScope()
    val locationService = remember { LocationService(context) }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val distanceUnit by userPreferencesRepository.distanceUnit.collectAsState(initial = DistanceUnit.IMPERIAL)
    val mapboxNavigation = remember(context) { NavigationManager.getInstance(context) }

    var isStyleLoaded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val currentIsMuted by rememberUpdatedState(isMuted)
    var routeTime by remember { mutableStateOf("") }
    var routeDistance by remember { mutableStateOf("") }
    var routeEta by remember { mutableStateOf("") }
    var maneuversResult by remember { mutableStateOf<Expected<ManeuverError, List<Maneuver>>?>(null) }

    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    // Keep screen on for the entire duration of active navigation
    DisposableEffect(Unit) {
        context.findActivity()?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            context.findActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun setVolumeControl(streamType: Int) {
        context.findActivity()?.volumeControlStream = streamType
    }

    val speechApi = remember { MapboxSpeechApi(context, Locale.getDefault().toLanguageTag()) }
    val voiceInstructionsPlayer = remember {
        MapboxVoiceInstructionsPlayer(context, Locale.getDefault().toLanguageTag())
    }

    val voiceInstructionsPlayerCallback = remember {
        { announcement: SpeechAnnouncement ->
            speechApi.clean(announcement)
            setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)
        }
    }

    val speechCallback = remember(voiceInstructionsPlayerCallback) {
        { expected: Expected<SpeechError, SpeechValue> ->
            setVolumeControl(AudioManager.STREAM_MUSIC)
            expected.fold(
                { error -> voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback) },
                { value -> voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback) }
            )
        }
    }

    val voiceInstructionsObserver = remember(speechCallback) {
        VoiceInstructionsObserver { voiceInstructions ->
            if (!currentIsMuted) speechApi.generate(voiceInstructions, speechCallback)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                val isInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.findActivity()?.isInPictureInPictureMode == true
                } else false
                if (!isInPip) {
                    mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        }
    }

    val distanceFormatterOptions = remember(distanceUnit) {
        DistanceFormatterOptions.Builder(context)
            .unitType(if (distanceUnit == DistanceUnit.IMPERIAL) UnitType.IMPERIAL else UnitType.METRIC)
            .build()
    }
    val distanceFormatter = remember(distanceFormatterOptions) { MapboxDistanceFormatter(distanceFormatterOptions) }
    val maneuverApi = remember(distanceFormatter) { MapboxManeuverApi(distanceFormatter) }

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
    val routeArrowApi = remember { MapboxRouteArrowApi() }
    val routeArrowView = remember { MapboxRouteArrowView(RouteArrowOptions.Builder(context).build()) }

    val navigationLocationProvider = remember { NavigationLocationProvider() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var navigationCamera by remember { mutableStateOf<NavigationCamera?>(null) }
    var viewportDataSource by remember { mutableStateOf<MapboxNavigationViewportDataSource?>(null) }
    val puckBitmap = rememberLocationPuckBitmap()

    // Start trip session on entry; fire GPS re-fetch in background so the route
    // converges to the user's actual position without blocking the UI.
    LaunchedEffect(Unit) {
        mapboxNavigation.startTripSession()
        context.startService(Intent(context, NavigationOverlayService::class.java))
        launch {
            try {
                val actual = locationService.getCurrentLocation() ?: return@launch
                mapboxNavigation.requestRoutes(
                    RouteOptions.builder()
                        .applyDefaultNavigationOptions()
                        .applyLanguageAndVoiceUnitOptions(context)
                        .profile(NavigationManager.lastTransportMode)
                        .coordinatesList(listOf(
                            Point.fromLngLat(actual.longitude, actual.latitude),
                            Point.fromLngLat(poiLng, poiLat)
                        ))
                        .build(),
                    object : NavigationRouterCallback {
                        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                            if (routes.isNotEmpty()) mapboxNavigation.setNavigationRoutes(routes)
                        }
                        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {}
                        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}
                    }
                )
            } catch (_: Exception) {}
        }
    }

    // Switch camera to following when PIP mode activates
    LaunchedEffect(isPipMode) {
        if (isPipMode) {
            navigationCamera?.requestNavigationCameraToFollowing(
                stateTransitionOptions = NavigationCameraTransitionOptions.Builder().maxDuration(0).build()
            )
        }
    }

    fun stopNavigation() {
        mapboxNavigation.stopTripSession()
        NavigationManager.setNavigationActive(false)
        context.stopService(Intent(context, NavigationOverlayService::class.java))
        speechApi.cancel()
        voiceInstructionsPlayer.clear()
        setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)
        mapView?.getMapboxMap()?.getStyle()?.let { style ->
            routeArrowView.render(style, routeArrowApi.clearArrows())
        }
        // Pop navigation_active then map? to return all the way to POI details
        navController.popBackStack()
        navController.popBackStack()
    }

    BackHandler(enabled = true) { stopNavigation() }

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
                mapView!!.location.apply {
                    enabled = true
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.COURSE
                    locationPuck = LocationPuck2D(
                        bearingImage = ImageHolder.from(puckBitmap),
                        shadowImage = null,
                        scaleExpression = null
                    )
                    setLocationProvider(navigationLocationProvider)
                }
                navigationCamera?.requestNavigationCameraToFollowing(
                    stateTransitionOptions = NavigationCameraTransitionOptions.Builder().maxDuration(0).build()
                )
            }

            val routesObserver = RoutesObserver { result ->
                routeLineApi.setNavigationRoutes(result.navigationRoutes) { drawData ->
                    mapView?.getMapboxMap()?.getStyle()?.let { style ->
                        routeLineView.renderRouteDrawData(style, drawData)
                    }
                }
                if (result.navigationRoutes.isNotEmpty()) {
                    viewportDataSource?.onRouteChanged(result.navigationRoutes[0])
                    viewportDataSource?.evaluate()
                }
            }

            val locationObserver = object : LocationObserver {
                override fun onNewLocationMatcherResult(result: LocationMatcherResult) {
                    val enhanced = result.enhancedLocation
                    navigationLocationProvider.changePosition(enhanced, result.keyPoints)
                    viewportDataSource?.onLocationChanged(enhanced)
                    viewportDataSource?.evaluate()
                }
                override fun onNewRawLocation(rawLocation: Location) {}
            }

            val routeProgressObserver = RouteProgressObserver { routeProgress ->
                viewportDataSource?.onRouteProgressChanged(routeProgress)
                viewportDataSource?.evaluate()

                mapView?.getMapboxMap()?.getStyle()?.let { style ->
                    routeArrowView.renderManeuverUpdate(style, routeArrowApi.addUpcomingManeuverArrow(routeProgress))
                }

                maneuversResult = maneuverApi.getManeuvers(routeProgress)

                val duration = routeProgress.durationRemaining
                val hours = TimeUnit.SECONDS.toHours(duration.toLong())
                val minutes = TimeUnit.SECONDS.toMinutes(duration.toLong()) % 60
                routeTime = buildString {
                    if (hours > 0) append("$hours hr ")
                    append("$minutes min")
                }
                routeDistance = distanceFormatter.formatDistance(routeProgress.distanceRemaining.toDouble()).toString()
                val cal = Calendar.getInstance().also { it.add(Calendar.SECOND, duration.toInt()) }
                routeEta = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            }

            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerLocationObserver(locationObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)

            // Render any routes already set (from the preview screen)
            val currentRoutes = mapboxNavigation.getNavigationRoutes()
            if (currentRoutes.isNotEmpty()) {
                routeLineApi.setNavigationRoutes(currentRoutes) { drawData ->
                    mapView?.getMapboxMap()?.getStyle()?.let { style ->
                        routeLineView.renderRouteDrawData(style, drawData)
                    }
                }
            }

            onDispose {
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                if (!NavigationManager.isNavigationActive.value) {
                    mapboxNavigation.setNavigationRoutes(emptyList())
                }
                routeLineApi.clearRouteLine { }
                speechApi.cancel()
                voiceInstructionsPlayer.shutdown()
                setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)
                routeLineApi.cancel()
                routeLineView.cancel()
                maneuverApi.cancel()
                maneuversResult = null
            }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize()) {

        // PIP mode: only the maneuver view is shown
        if (isPipMode) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(3f),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    factory = { ctx ->
                        val wrappedCtx = ContextThemeWrapper(ctx, R.style.Theme_HelloWorld)
                        val view = LayoutInflater.from(wrappedCtx)
                            .inflate(R.layout.view_maneuver, null, false) as MapboxManeuverView
                        val blackRes = android.R.color.black
                        val whiteStyle = R.style.ManeuverTextAppearance_Overlay
                        view.updateManeuverViewOptions(
                            ManeuverViewOptions.Builder()
                                .maneuverBackgroundColor(blackRes)
                                .subManeuverBackgroundColor(blackRes)
                                .upcomingManeuverBackgroundColor(blackRes)
                                .stepDistanceTextAppearance(whiteStyle)
                                .laneGuidanceTurnIconManeuver(R.style.LaneGuidanceTurnIconStyle)
                                .primaryManeuverOptions(ManeuverPrimaryOptions.Builder().textAppearance(whiteStyle).build())
                                .secondaryManeuverOptions(ManeuverSecondaryOptions.Builder().textAppearance(whiteStyle).build())
                                .subManeuverOptions(ManeuverSubOptions.Builder().textAppearance(whiteStyle).build())
                                .build()
                        )
                        view.post {
                            fun tintWhite(v: View) {
                                if (v is MapboxLaneGuidance) return
                                when (v) {
                                    is ImageView -> v.setColorFilter(android.graphics.Color.WHITE)
                                    is TextView -> v.setTextColor(android.graphics.Color.WHITE)
                                    is ViewGroup -> (0 until v.childCount).forEach { tintWhite(v.getChildAt(it)) }
                                }
                            }
                            tintWhite(view)
                        }
                        view
                    },
                    update = { view ->
                        maneuversResult?.let { result ->
                            view.renderManeuvers(result)
                            view.visibility = if (result.value?.isNotEmpty() == true) View.VISIBLE else View.GONE
                        } ?: run { view.visibility = View.GONE }
                    }
                )
            }
        }

        // Map — kept full-size in PIP so the GL surface stays alive
        val mapModifier = if (isPipMode) {
            Modifier.fillMaxSize().zIndex(1f)
        } else {
            Modifier.fillMaxSize().padding(top = topBarHeight, bottom = bottomBarHeight).zIndex(0f)
        }

        Box(modifier = mapModifier) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                compass = { },
                scaleBar = { },
                logo = {
                    if (!isPipMode) Logo(modifier = Modifier.align(Alignment.BottomStart))
                },
                attribution = {
                    if (!isPipMode) Attribution(modifier = Modifier.align(Alignment.BottomEnd))
                }
            ) {
                MapEffect(Unit) { mapViewInstance ->
                    mapView = mapViewInstance
                    mapViewInstance.getMapboxMap().loadStyleUri(
                        mapViewInstance.context.getString(R.string.mapbox_eink_style_uri)
                    ) { style ->
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
        }

        // Loading overlay — white scrim with MMD spinner until the map style is ready
        if (!isStyleLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicatorMMD()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Starting navigation...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Navigation UI (hidden in PIP mode)
        if (!isPipMode) {
            Column(
                modifier = Modifier.fillMaxSize().zIndex(2f)
            ) {
                // Top bar — maneuver view
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { topBarHeight = with(density) { it.size.height.toDp() } },
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx ->
                                val wrappedCtx = ContextThemeWrapper(ctx, R.style.Theme_HelloWorld)
                                val view = LayoutInflater.from(wrappedCtx)
                                    .inflate(R.layout.view_maneuver, null, false) as MapboxManeuverView
                                val whiteRes = android.R.color.white
                                val blackRes = android.R.color.black
                                val blackStyle = R.style.ManeuverTextAppearance
                                view.updateManeuverViewOptions(
                                    ManeuverViewOptions.Builder()
                                        .maneuverBackgroundColor(whiteRes)
                                        .subManeuverBackgroundColor(blackRes)
                                        .upcomingManeuverBackgroundColor(whiteRes)
                                        .stepDistanceTextAppearance(blackStyle)
                                        .turnIconManeuver(R.style.MapboxCustomManeuverTurnIconStyle)
                                        .laneGuidanceTurnIconManeuver(R.style.LaneGuidanceTurnIconStyle)
                                        .primaryManeuverOptions(ManeuverPrimaryOptions.Builder().textAppearance(blackStyle).build())
                                        .secondaryManeuverOptions(ManeuverSecondaryOptions.Builder().textAppearance(blackStyle).build())
                                        .subManeuverOptions(ManeuverSubOptions.Builder().textAppearance(blackStyle).build())
                                        .build()
                                )
                                view.post { tintManeuverView(view, android.graphics.Color.BLACK) }
                                view
                            },
                            update = { view ->
                                maneuversResult?.let { result ->
                                    view.renderManeuvers(result)
                                    view.visibility = if (result.value?.isNotEmpty() == true) View.VISIBLE else View.GONE
                                    view.post { tintManeuverView(view, android.graphics.Color.BLACK) }
                                } ?: run { view.visibility = View.GONE }
                            }
                        )
                        HorizontalDividerMMD(thickness = 3.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Spacer(modifier = Modifier.fillMaxWidth().weight(1f))

                // Bottom bar — mute, ETA, stop
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { bottomBarHeight = with(density) { it.size.height.toDp() } },
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        HorizontalDividerMMD(thickness = 3.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                modifier = Modifier.size(48.dp),
                                onClick = {
                                    isMuted = !isMuted
                                    if (isMuted) {
                                        speechApi.cancel()
                                        voiceInstructionsPlayer.clear()
                                    }
                                }
                            ) {
                                Icon(
                                    if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(routeTime, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(routeDistance, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("•", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(routeEta, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            IconButton(
                                modifier = Modifier.size(48.dp),
                                onClick = { stopNavigation() }
                            ) {
                                Icon(
                                    Icons.Outlined.Clear,
                                    contentDescription = "End Navigation",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tints all non-LaneGuidance child views of a [MapboxManeuverView] to [color]. */
private fun tintManeuverView(view: View, color: Int) {
    if (view is MapboxLaneGuidance) return
    when (view) {
        is ImageView -> view.setColorFilter(color)
        is TextView -> view.setTextColor(color)
        is ViewGroup -> (0 until view.childCount).forEach { tintManeuverView(view.getChildAt(it), color) }
    }
}

@Composable
fun rememberLocationPuckBitmap(): Bitmap {
    val context = LocalContext.current
    return remember(context) {
        val density = context.resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val outlineWidth = 4 * density
        val radius = (sizePx / 2f) - (outlineWidth / 2)
        canvas.drawCircle(cx, cy, radius, Paint().apply {
            isAntiAlias = true; style = Paint.Style.FILL; color = AndroidColor.WHITE
        })
        canvas.drawCircle(cx, cy, radius, Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; color = AndroidColor.BLACK; strokeWidth = outlineWidth
        })
        val arrowSize = radius * 1.2f
        val path = Path().apply {
            moveTo(cx, cy - arrowSize / 2)
            lineTo(cx + arrowSize / 2.5f, cy + arrowSize / 2)
            lineTo(cx, cy + arrowSize / 4)
            lineTo(cx - arrowSize / 2.5f, cy + arrowSize / 2)
            close()
        }
        canvas.drawPath(path, Paint().apply {
            isAntiAlias = true; style = Paint.Style.FILL; color = AndroidColor.BLACK
        })
        bitmap
    }
}
