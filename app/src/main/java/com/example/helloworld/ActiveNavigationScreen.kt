package com.example.helloworld

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.calmapps.directory.BuildConfig
import com.example.helloworld.data.LocationRepository
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.NavigationRouterCallback
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import java.util.Locale

/**
 * Full-screen active navigation screen powered by Mapbox.
 *
 * Displays a Mapbox map with turn-by-turn navigation to [destLat]/[destLng].
 * The bottom bar shows the current navigation instruction and a **mute button**
 * that silences Mapbox voice guidance without stopping the navigation session.
 *
 * Prerequisites:
 *  - Set `MAPBOX_TOKEN=pk.xxx...` in `local.properties` (public access token).
 *  - Set `MAPBOX_DOWNLOADS_TOKEN=sk.xxx...` in `~/.gradle/gradle.properties`
 *    (secret download token required to fetch Mapbox SDK artifacts).
 *  - [MapboxNavigationApp] is initialised in [MyApplication.onCreate].
 */
@Composable
fun ActiveNavigationScreen(
    destLat: Double,
    destLng: Double,
    destName: String,
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Whether Mapbox voice guidance is currently muted. */
    var isMuted by remember { mutableStateOf(false) }

    // rememberUpdatedState lets the VoiceInstructionsObserver lambda always
    // read the latest isMuted value without the observer being recreated.
    val currentIsMuted = rememberUpdatedState(isMuted)

    /** Text of the most recent turn-by-turn banner instruction. */
    var currentInstruction by remember { mutableStateOf("") }

    /** Guard so we request a route only once per screen visit. */
    var routeRequested by remember { mutableStateOf(false) }

    // -------------------------------------------------------------------------
    // Mapbox Navigation lifecycle
    // -------------------------------------------------------------------------

    // Attaching the lifecycle owner lets MapboxNavigationApp know when to
    // create and destroy the underlying MapboxNavigation instance.
    DisposableEffect(lifecycleOwner) {
        MapboxNavigationApp.attach(lifecycleOwner)
        onDispose {
            MapboxNavigationApp.detach(lifecycleOwner)
        }
    }

    val mapboxNavigation = remember {
        runCatching { MapboxNavigationProvider.retrieve() }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // Location & route request
    // -------------------------------------------------------------------------

    val locationRepository = remember { LocationRepository(context) }
    val location by locationRepository.location.collectAsState()

    // Start location tracking and the navigation trip session.
    DisposableEffect(mapboxNavigation) {
        locationRepository.startLocationUpdates()
        mapboxNavigation?.startTripSession()
        onDispose {
            locationRepository.stopLocationUpdates()
            mapboxNavigation?.stopTripSession()
        }
    }

    // Request a Mapbox route as soon as we have the first GPS fix.
    // The routeRequested guard prevents duplicate calls on recomposition.
    val nav = mapboxNavigation
    val loc = location
    if (nav != null && loc != null && !routeRequested) {
        routeRequested = true
        val origin = Point.fromLngLat(loc.longitude, loc.latitude)
        val destination = Point.fromLngLat(destLng, destLat)

        nav.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(listOf(origin, destination))
                .build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    nav.setNavigationRoutes(routes)
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) { /* Navigation continues in free-drive without a route */ }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: RouterOrigin
                ) { }
            }
        )
    }

    // -------------------------------------------------------------------------
    // Banner instruction observer
    // -------------------------------------------------------------------------

    DisposableEffect(mapboxNavigation) {
        val bannerObserver = BannerInstructionsObserver { bannerInstructions ->
            currentInstruction = bannerInstructions.primary().text() ?: ""
        }
        mapboxNavigation?.registerBannerInstructionsObserver(bannerObserver)
        onDispose {
            mapboxNavigation?.unregisterBannerInstructionsObserver(bannerObserver)
        }
    }

    // -------------------------------------------------------------------------
    // Voice instructions observer
    // -------------------------------------------------------------------------

    // When isMuted is true the observer exits early so no speech is generated
    // or played. rememberUpdatedState ensures we always read the latest value.
    DisposableEffect(mapboxNavigation) {
        val speechApi = MapboxSpeechApi(
            context,
            BuildConfig.MAPBOX_TOKEN,
            Locale.getDefault().language
        )
        val voicePlayer = MapboxVoiceInstructionsPlayer(
            context,
            Locale.getDefault().language
        )

        val voiceObserver = VoiceInstructionsObserver { voiceInstructions ->
            if (currentIsMuted.value) return@VoiceInstructionsObserver

            speechApi.generate(voiceInstructions) { expected ->
                expected.onValue { announcement ->
                    voicePlayer.play(announcement) { /* no-op on finish */ }
                }
            }
        }

        mapboxNavigation?.registerVoiceInstructionsObserver(voiceObserver)

        onDispose {
            mapboxNavigation?.unregisterVoiceInstructionsObserver(voiceObserver)
            speechApi.cancel()
            voicePlayer.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Map viewport — initially centred on the destination
    // -------------------------------------------------------------------------

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(destLng, destLat))
            zoom(13.0)
        }
    }

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    Box(modifier = Modifier.fillMaxSize()) {

        // Full-screen Mapbox map
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState
        )

        // Bottom navigation bar overlaid on the map
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            HorizontalDividerMMD(
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / stop navigation
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Stop navigation"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Current turn instruction, or destination name while route loads
                Text(
                    text = if (currentInstruction.isNotEmpty()) currentInstruction else destName,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                // ----------------------------------------------------------
                // Mute button — the primary feature of this screen
                // ----------------------------------------------------------
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        // Also inform the SDK's built-in audio guidance so
                        // any SDK-level announcements are suppressed too.
                        if (isMuted) {
                            mapboxNavigation?.audioGuidance?.mute()
                        } else {
                            mapboxNavigation?.audioGuidance?.unmute()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Outlined.VolumeOff
                                      else         Icons.Outlined.VolumeUp,
                        contentDescription = if (isMuted) "Unmute voice guidance"
                                             else         "Mute voice guidance"
                    )
                }
            }
        }
    }
}
