package com.example.helloworld

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.LocationRepository
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = viewModel()
) {
    val apiKey by mainViewModel.apiKey.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val locationRepository = remember { LocationRepository(context) }
    val location by locationRepository.location.collectAsState()

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicatorMMD()
        }
    } else if (apiKey.isNullOrEmpty()) {
        NoApiKeyScreen(
            onSettingsClicked = { navController.navigate("settings") }
        )
    } else {
        LocationPermissionRequest(
            onPermissionGranted = {
                locationRepository.startLocationUpdates()
            }
        )
        Column {
            LandingScreen(
                modifier = Modifier,
                onCategorySelected = { category ->
                    val encodedCategory =
                        URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
                    navController.navigate("search?query=$encodedCategory&autoFocus=false")
                }
            )
            location?.let {
                Text("Latitude: ${it.latitude}")
                Text("Longitude: ${it.longitude}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationRepository.stopLocationUpdates()
        }
    }
}

@Composable
fun LocationPermissionRequest(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val permission = Manifest.permission.ACCESS_FINE_LOCATION
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            permissionGranted = true
            onPermissionGranted()
        }
    }

    if (!permissionGranted) {
        Button(onClick = { launcher.launch(permission) }) {
            Text("Request Location Permission")
        }
    } else {
        onPermissionGranted()
    }
}