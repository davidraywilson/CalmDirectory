package com.example.helloworld

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.LinearProgressIndicatorMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineRegionSelectorScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineManager = remember { OfflineMapManager(context) }

    var showNameDialog by remember { mutableStateOf(false) }
    var regionName by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    var mapboxMapInstance by remember { mutableStateOf<com.mapbox.maps.MapboxMap?>(null) }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                title = { Text("Select Offline Region", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                scaleBar = {},
                compass = {},
            ) {
                com.mapbox.maps.extension.compose.MapEffect(Unit) { mapView ->
                    mapboxMapInstance = mapView.getMapboxMap()
                    mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = size.width * 0.8f
                val boxHeight = size.height * 0.6f
                val left = (size.width - boxWidth) / 2
                val top = (size.height - boxHeight) / 2

                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, top)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, top + boxHeight),
                    size = Size(size.width, size.height - (top + boxHeight))
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, top),
                    size = Size(left, boxHeight)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(left + boxWidth, top),
                    size = Size(size.width - (left + boxWidth), boxHeight)
                )

                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isDownloading) {
                    Text("Downloading... $downloadProgress%", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicatorMMD(progress = { downloadProgress / 100f })
                } else {
                    ButtonMMD(
                        onClick = { showNameDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("Download Area")
                    }
                }
            }
        }

        if (showNameDialog) {
            ModalBottomSheetMMD(onDismissRequest = { showNameDialog = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Name this Offline Map", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    TextFieldMMD(
                        value = regionName,
                        onValueChange = { regionName = it },
                        label = { Text("Region Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ButtonMMD(
                        onClick = {
                            showNameDialog = false
                            isDownloading = true

                            mapboxMapInstance?.let { map ->
                                val cameraState = map.cameraState
                                val center = cameraState.center

                                val span = 0.05 // rough degrees approx
                                val points = listOf(
                                    Point.fromLngLat(center.longitude() - span, center.latitude() - span),
                                    Point.fromLngLat(center.longitude() + span, center.latitude() - span),
                                    Point.fromLngLat(center.longitude() + span, center.latitude() + span),
                                    Point.fromLngLat(center.longitude() - span, center.latitude() + span),
                                    Point.fromLngLat(center.longitude() - span, center.latitude() - span)
                                )
                                val geometry = Polygon.fromLngLats(listOf(points))

                                offlineManager.downloadRegion(
                                    regionName = regionName.ifBlank { "Offline Region" },
                                    geometry = geometry,
                                    onProgress = { progress ->
                                        downloadProgress = progress
                                    },
                                    onCompletion = { result ->
                                        scope.launch {
                                            isDownloading = false
                                            result.fold(
                                                onSuccess = {
                                                    Toast.makeText(context, "Download Complete", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                },
                                                onFailure = {
                                                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Download")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}