package com.example.helloworld

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import com.mudita.mmd.components.bottom_sheet.SheetValue
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.combine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.LocationRepository
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = viewModel()
) {
    val isLoading by mainViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val locationRepository = remember { LocationRepository(context) }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val geocodingService = remember { GoogleGeocodingService() }

    val locationGate by remember(userPreferencesRepository) {
        userPreferencesRepository.useDeviceLocation
            .combine(userPreferencesRepository.defaultLocation) { device, def ->
                if (device || !def.isNullOrBlank()) LocationGate.HasLocation
                else LocationGate.NeedsLocation
            }
    }.collectAsState(initial = LocationGate.Unknown)

    val quickLocations by userPreferencesRepository.quickLocations.collectAsState(initial = emptyList())

    val bottomSheetState = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = false,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    var showLocationBottomSheet by remember { mutableStateOf(false) }
    var showNamingBottomSheet by remember { mutableStateOf(false) }
    var pendingAddress by remember { mutableStateOf("") }
    var newLocationLabel by remember { mutableStateOf("") }

    LaunchedEffect(locationGate) {
        showLocationBottomSheet = locationGate == LocationGate.NeedsLocation
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val returnedAddress by savedStateHandle
        ?.getStateFlow<String?>("selected_address", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(returnedAddress) {
        if (!returnedAddress.isNullOrBlank()) {
            pendingAddress = returnedAddress!!
            showNamingBottomSheet = true
            savedStateHandle?.set("selected_address", null)
        }
    }

    if (isLoading || locationGate == LocationGate.Unknown) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicatorMMD()
        }
    } else {
        locationRepository.startLocationUpdates()

        if (showLocationBottomSheet) {
            ModalBottomSheetMMD(
                onDismissRequest = { },
                sheetState = bottomSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
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

        if (showNamingBottomSheet) {
            ModalBottomSheetMMD(
                onDismissRequest = { showNamingBottomSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Name your location",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    TextFieldMMD(
                        value = newLocationLabel,
                        onValueChange = { newLocationLabel = it },
                        label = { Text("Label (e.g., Gym, Park)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.padding(12.dp))
                    ButtonMMD(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                userPreferencesRepository.addQuickLocation(newLocationLabel, pendingAddress)
                                showNamingBottomSheet = false
                                newLocationLabel = ""
                                pendingAddress = ""
                            }
                        }
                    ) {
                        Text("Save Location")
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LandingScreen(
                modifier = Modifier,
                quickLocations = quickLocations,
                onAddQuickLocation = {
                    navController.navigate("search?autoFocus=true&saveAs=NEW_QUICK_LOCATION")
                },
                onQuickLocationClicked = { quickLoc ->
                    scope.launch {
                        val coords = geocodingService.getCoordinates(quickLoc.address)

                        val lat = coords?.first ?: 0.0
                        val lng = coords?.second ?: 0.0
                        val encodedName = URLEncoder.encode(quickLoc.label, StandardCharsets.UTF_8.toString())
                        val encodedAddress = URLEncoder.encode(quickLoc.address, StandardCharsets.UTF_8.toString())

                        navController.navigate("map?poiName=$encodedName&poiAddress=$encodedAddress&isPlace=false&lat=$lat&lng=$lng")
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationRepository.stopLocationUpdates()
        }
    }
}

private enum class LocationGate { Unknown, NeedsLocation, HasLocation }