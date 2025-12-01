package com.example.helloworld

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import com.mudita.mmd.components.bottom_sheet.SheetValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.LocationRepository
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    searchViewModel: SearchViewModel? = null,
    mainViewModel: MainViewModel = viewModel()
) {
    val apiKey by mainViewModel.apiKey.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val locationRepository = remember { LocationRepository(context) }
    val location by locationRepository.location.collectAsState()
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val useDeviceLocation by userPreferencesRepository.useDeviceLocation.collectAsState(initial = false)
    val defaultLocation by userPreferencesRepository.defaultLocation.collectAsState(initial = null)
    val bottomSheetState = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = false,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var showLocationBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(useDeviceLocation, defaultLocation) {
        showLocationBottomSheet = !useDeviceLocation && defaultLocation.isNullOrBlank()
    }

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

        if (showLocationBottomSheet) {
            ModalBottomSheetMMD(
                // Outside taps and back press will try to change the sheet state,
                // but confirmValueChange above prevents it from hiding.
                onDismissRequest = { /* no-op to keep it persistent */ },
                sheetState = bottomSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Set a Default Location",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Before searching, you must set a default location or enable device location.",
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )

                    ButtonMMD(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "scrollToLocationSettings",
                                true
                            )
                            showLocationBottomSheet = false
                            navController.navigate("settings")
                        }
                    ) {
                        Text("Go to Settings")
                    }
                }
            }
        }

        Column {
            LandingScreen(
                modifier = Modifier,
                onCategorySelected = { category ->
                    searchViewModel?.resetSearch()
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
        null;
    } else {
        onPermissionGranted()
    }
}