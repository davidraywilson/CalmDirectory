package com.paperapps.papermaps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.paperapps.papermaps.data.LocationRepository
import com.paperapps.papermaps.data.UserPreferencesRepository
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.SheetValue
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.paperapps.paperui.components.AppbarAction
import com.paperapps.paperui.components.ApplicationBar
import com.paperapps.paperui.components.PanoramaHeader
import com.paperapps.paperui.components.PanoramaPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    searchViewModel: SearchViewModel? = null,
    mainViewModel: MainViewModel = viewModel()
) {
    val isLoading by mainViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val locationRepository = remember { LocationRepository(context) }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val geocodingService = remember { GoogleGeocodingService(userPreferencesRepository) }

    val useDeviceLocation by userPreferencesRepository.useDeviceLocation.collectAsState(initial = false)
    val defaultLocation by userPreferencesRepository.defaultLocation.collectAsState(initial = null)
    val quickLocations by userPreferencesRepository.quickLocations.collectAsState(initial = emptyList())

    val bottomSheetState = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = false,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    var showLocationBottomSheet by remember { mutableStateOf(false) }
    var showNamingBottomSheet by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var pendingAddress by remember { mutableStateOf("") }
    var newLocationLabel by remember { mutableStateOf("") }

    val currentSortOption by mainViewModel.sortOption.collectAsState()

    LaunchedEffect(useDeviceLocation, defaultLocation) {
        showLocationBottomSheet = !useDeviceLocation && defaultLocation.isNullOrBlank()
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

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicatorMMD()
        }
    } else {
        locationRepository.startLocationUpdates()

        if (showLocationBottomSheet) {
            ModalBottomSheetMMD(
                onDismissRequest = { },
                sheetState = bottomSheetState
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
                    Text("Set a Default Location", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Before searching, you must set a default location or enable device location.", modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    ButtonMMD(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToLocationSettings", true)
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
            ModalBottomSheetMMD(onDismissRequest = { showNamingBottomSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Name your location", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

        if (showSortBottomSheet) {
            ModalBottomSheetMMD(onDismissRequest = { showSortBottomSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Sort Results", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.padding(8.dp))
                    
                    SortOption.values().forEach { option ->
                        val label = when (option) {
                            SortOption.DISTANCE -> "Distance"
                            SortOption.RATING -> "Rating"
                            SortOption.POPULARITY -> "Popularity"
                            SortOption.NAME_AZ -> "Name (A-Z)"
                            SortOption.NAME_ZA -> "Name (Z-A)"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mainViewModel.setSortOption(option)
                                    showSortBottomSheet = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = currentSortOption == option,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.padding(8.dp))
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        val categories = listOf("favorites", "eat+drink", "see+do", "shop", "hotels")
        val pagerState = rememberPagerState(pageCount = { categories.size })
        val categoryResults by mainViewModel.categoryResults.collectAsState()
        val categoryLoading by mainViewModel.categoryLoading.collectAsState()

        val handlePoiSelection: (Poi) -> Unit = { poi ->
            if (poi.isPlace) {
                val encodedName = URLEncoder.encode(poi.name, StandardCharsets.UTF_8.toString()).replace("/", "%2F")
                val addressString = "${poi.address.street}, ${poi.address.city}, ${poi.address.state} ${poi.address.zip}, ${poi.address.country}"
                val encodedAddress = URLEncoder.encode(addressString, StandardCharsets.UTF_8.toString()).replace("/", "%2F")
                val country = poi.address.country
                val encodedCountry = URLEncoder.encode(country, StandardCharsets.UTF_8.toString()).replace("/", "%2F")
                val rawPhone = poi.phone?.takeIf { it.isNotBlank() } ?: "NA"
                val encodedPhone = URLEncoder.encode(rawPhone, StandardCharsets.UTF_8.toString()).replace("/", "%2F")
                val encodedDescription = URLEncoder.encode(poi.description ?: "NA", StandardCharsets.UTF_8.toString()).replace("/", "%2F")
                val encodedHours = URLEncoder.encode(if (poi.hours.isEmpty()) "NA" else poi.hours.joinToString(","), StandardCharsets.UTF_8.toString()).replace("/", "%2F")

                var route = "details/$encodedName/$encodedAddress/$encodedCountry/$encodedPhone/$encodedDescription/$encodedHours"
                val websiteParam = poi.website?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) } ?: ""
                route += "?poiWebsite=$websiteParam"
                if (poi.lat != null && poi.lng != null) {
                    route += "&lat=${poi.lat}&lng=${poi.lng}"
                } else {
                    route += "&lat=0.0&lng=0.0"
                }
                if (poi.placeId != null) {
                    route += "&placeId=${URLEncoder.encode(poi.placeId, StandardCharsets.UTF_8.toString())}"
                }
                navController.navigate(route)
            } else if (poi.lat != null && poi.lng != null) {
                val encodedName = URLEncoder.encode(poi.name, StandardCharsets.UTF_8.toString())
                val addressString = "${poi.address.street}, ${poi.address.city}, ${poi.address.state} ${poi.address.zip}, ${poi.address.country}"
                val encodedAddress = URLEncoder.encode(addressString, StandardCharsets.UTF_8.toString())
                navController.navigate("map?poiName=$encodedName&poiAddress=$encodedAddress&isPlace=false&lat=${poi.lat}&lng=${poi.lng}")
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PanoramaHeader(
                pagerState = pagerState,
                titles = categories,
                coroutineScope = scope,
                screenTitle = "PaperMaps"
            )

            PanoramaPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val title = categories[page]
                if (page == 0) {
                    LandingFavorites(
                        quickLocations = quickLocations,
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
                } else {
                    LaunchedEffect(title, currentSortOption) {
                        mainViewModel.fetchCategory(title)
                    }
                    val results = categoryResults[title] ?: emptyList()
                    val isCatLoading = categoryLoading[title] ?: false
                    
                    if (isCatLoading && results.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicatorMMD()
                        }
                    } else if (results.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found for $title")
                        }
                    } else {
                        SearchScreen(
                            searchResults = results,
                            onPoiSelected = handlePoiSelection
                        )
                    }
                }
            }

            val actions = mutableListOf<AppbarAction>()
            
            if (pagerState.currentPage > 0) {
                actions.add(
                    AppbarAction(
                        icon = Icons.Filled.Menu,
                        label = "Sort",
                        onClick = { showSortBottomSheet = true }
                    )
                )
            }
            
            if (pagerState.currentPage == 0) {
                actions.add(
                    AppbarAction(
                        icon = Icons.Filled.Add,
                        label = "Add",
                        onClick = { navController.navigate("search?autoFocus=true&saveAs=NEW_QUICK_LOCATION") }
                    )
                )
            }
            actions.add(
                AppbarAction(
                    icon = Icons.Outlined.Search,
                    label = "Search",
                    onClick = {
                        searchViewModel?.resetSearch()
                        navController.navigate("search?autoFocus=true")
                    }
                )
            )
            actions.add(
                AppbarAction(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    onClick = { navController.navigate("settings") }
                )
            )

            ApplicationBar(actions = actions)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationRepository.stopLocationUpdates()
        }
    }
}