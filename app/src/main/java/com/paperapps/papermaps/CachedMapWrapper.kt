package com.paperapps.papermaps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.bindgen.Value

fun Style.applyEinkTheme() {
    this.styleLayers.forEach { layer ->
        when (layer.type) {
            "background" -> this.setStyleLayerProperty(layer.id, "background-color", Value.valueOf("#FFFFFF"))
            "line" -> if (layer.id.startsWith("road")) {
                val color = if (layer.id.contains("motorway") || layer.id.contains("trunk")) "#222222" else "#777777"
                this.setStyleLayerProperty(layer.id, "line-color", Value.valueOf(color))
            }
            "symbol" -> if (layer.id.contains("label")) this.setStyleLayerProperty(layer.id, "text-color", Value.valueOf("#000000"))
        }
    }
}


/**
 * Creates a fresh [MapView] on every composition entry and destroys it on dispose.
 *
 * Mapbox Maps SDK v11 auto-manages its own lifecycle through [ViewTreeLifecycleOwner],
 * so re-parenting a long-lived MapView between Compose nodes (the previous "cached"
 * approach) conflicts with the SDK's internal lifecycle observer and produces a blank
 * map on the second visit. The simplest correct solution is to create a new MapView
 * each time the screen is entered and let Mapbox handle everything itself.
 *
 * The [onMapReady] callback fires with the newly created [MapView] once it has been
 * placed into the composition, so callers can attach annotation managers, set the
 * camera, etc.
 */
@Composable
fun CachedMapWrapper(
    modifier: Modifier = Modifier,
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current

    // Create a new MapView for this composition entry.
    val mapView = remember {
        MapView(
            context = context,
            mapInitOptions = MapInitOptions(
                context = context,
                textureView = true
            )
        )
    }

    // Notify caller that the MapView is ready so markers, camera, etc. can be applied.
    // DisposableEffect(Unit) runs once after the first composition frame.
    DisposableEffect(Unit) {
        onMapReady(mapView)
        onDispose {
            // onDestroy() releases all Mapbox GL resources for this MapView instance.
            // Mapbox v11 handles onStart/onStop automatically via ViewTreeLifecycleOwner,
            // so we only need to explicitly call onDestroy here to clean up fully.
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}
