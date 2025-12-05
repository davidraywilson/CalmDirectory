package com.example.helloworld

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.MapApp
import com.example.helloworld.data.SearchProvider
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.slider.SliderMMD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel(),
    scrollToLocationSettings: Boolean = false
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val googleApiKey by userPreferencesRepository.googleApiKey.collectAsState(initial = "")
    var newGoogleApiKey by remember(googleApiKey) { mutableStateOf(googleApiKey ?: "") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostStateMMD() }
    var isEditingGoogleKey by remember(googleApiKey) { mutableStateOf(googleApiKey.isNullOrEmpty()) }
    val currentLocation by viewModel.currentLocation.collectAsState()
    val useDeviceLocation by viewModel.useDeviceLocation.collectAsState(initial = true)
    val defaultLocation by viewModel.defaultLocation.collectAsState(initial = "")
    var newDefaultLocation by remember(defaultLocation) { mutableStateOf(defaultLocation ?: "") }
    var isEditingLocation by remember(defaultLocation) {
        mutableStateOf(
            defaultLocation.isNullOrEmpty()
        )
    }
    val locationSuggestions by viewModel.locationSuggestions.collectAsState()
    val mapApp by userPreferencesRepository.mapApp.collectAsState(initial = MapApp.DEFAULT)
    var searchProvider by remember { mutableStateOf<SearchProvider?>(null) }
    val locationSettingsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val suggestionsBringIntoViewRequester = remember { BringIntoViewRequester() }

    val requestLocationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineGranted || coarseGranted) {
                viewModel.setUseDeviceLocation(true)
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Location permission denied. Using default location instead.")
                }
            }
        }

    LaunchedEffect(Unit) {
        searchProvider = userPreferencesRepository.searchProvider.first()
    }

    LaunchedEffect(scrollToLocationSettings) {
        if (scrollToLocationSettings) {
            locationSettingsBringIntoViewRequester.bringIntoView()
            navController.previousBackStackEntry?.savedStateHandle?.set("scrollToLocationSettings", false)
        }
    }

    val searchRadius by userPreferencesRepository.searchRadius.collectAsState(initial = 10)
    var sliderValue by remember(searchRadius) { mutableStateOf(searchRadius.toFloat()) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    fun maskApiKey(key: String): String {
        if (key.length <= 4) {
            return "x".repeat(key.length)
        }
        return "x".repeat(key.length - 4) + key.takeLast(4)
    }

    LaunchedEffect(isEditingLocation, locationSuggestions) {
        if (isEditingLocation && locationSuggestions.isNotEmpty()) {
            // Ensure the suggestions box is fully visible above the keyboard
            suggestionsBringIntoViewRequester.bringIntoView()
        }
    }

    LazyColumnMMD(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(vertical = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(text = "Data Provider", fontSize = 16.sp)
                Spacer(modifier = Modifier.padding(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            searchProvider = SearchProvider.GOOGLE_PLACES
                            coroutineScope.launch {
                                userPreferencesRepository.saveSearchProvider(SearchProvider.GOOGLE_PLACES)
                                snackbarHostState.showSnackbar("Using Google Places backend")
                            }
                        }
                ) {
                    RadioButtonMMD(
                        selected = searchProvider == SearchProvider.GOOGLE_PLACES,
                        onClick = null,
                        modifier = Modifier.semantics{ contentDescription = "Google Places" }
                    )

                    Text(
                        text = "Google Places",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable {
                            searchProvider = SearchProvider.HERE
                            coroutineScope.launch {
                                userPreferencesRepository.saveSearchProvider(SearchProvider.HERE)
                                snackbarHostState.showSnackbar("Using HERE backend")
                            }
                        }
                ) {
                    RadioButtonMMD(
                        selected = searchProvider == SearchProvider.HERE,
                        onClick = null,
                        modifier = Modifier.semantics{ contentDescription = "HERE Maps" }
                    )
                    Text(text = "HERE Maps", modifier = Modifier.padding(start = 8.dp))
                }

                if (searchProvider == SearchProvider.GOOGLE_PLACES) {
                    Spacer(modifier = Modifier.padding(16.dp))

                    Text(
                        text = "Google Places API Key:",
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.padding(4.dp))

                    if (isEditingGoogleKey) {
                        TextFieldMMD(
                            value = newGoogleApiKey,
                            onValueChange = { newGoogleApiKey = it },
                            label = { Text("Google Places API Key") }
                        )

                        Spacer(modifier = Modifier.padding(8.dp))

                        ButtonMMD(onClick = {
                            coroutineScope.launch {
                                userPreferencesRepository.saveGoogleApiKey(newGoogleApiKey.trim())
                                snackbarHostState.showSnackbar("Google Places API Key saved successfully")
                                isEditingGoogleKey = false
                            }
                        }) {
                            Text("Save")
                        }
                    } else {
                        Text(
                            text = maskApiKey(googleApiKey ?: ""),
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.padding(8.dp))

                        ButtonMMD(onClick = { isEditingGoogleKey = true }) {
                            Text("Edit")
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(16.dp))

                HorizontalDividerMMD(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        item {
            Spacer(modifier = Modifier.padding(16.dp))
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(text = "Search Radius: ${sliderValue.toInt()} miles")

                SliderMMD(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        coroutineScope.launch {
                            userPreferencesRepository.saveSearchRadius(sliderValue.toInt())
                            snackbarHostState.showSnackbar("Search radius updated")
                        }
                    },
                    valueRange = 1f..20f,
                    steps = 18, // 1-mile increments (20 - 1 - 1)
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 0.dp)
                )
            }
        }

        item {
            HorizontalDividerMMD(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.padding(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Use device location",
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                SwitchMMD(
                    checked = useDeviceLocation,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.setUseDeviceLocation(true)
                            } else {
                                requestLocationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        } else {
                            viewModel.setUseDeviceLocation(false)
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.padding(8.dp))
        }

        if (!useDeviceLocation) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .bringIntoViewRequester(locationSettingsBringIntoViewRequester)
                ) {
                    if (isEditingLocation) {
                        val visibleSuggestions = locationSuggestions.take(4)

                        SearchBarDefaultsMMD.InputField(
                            query = newDefaultLocation,
                            onQueryChange = {
                                newDefaultLocation = it
                                viewModel.onDefaultLocationChange(it)
                            },
                            onSearch = { /* we save only when user taps Save */ },
                            expanded = true,
                            onExpandedChange = { /* no-op */ },
                            placeholder = { Text("Default Location") },
                            modifier = Modifier.onFocusChanged { state ->
                                if (state.isFocused) {
                                    coroutineScope.launch {
                                        locationSettingsBringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                        )

                        if (visibleSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.padding(top = 8.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(suggestionsBringIntoViewRequester)
                            ) {
                                Column {
                                    visibleSuggestions.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    newDefaultLocation = suggestion
                                                    viewModel.onDefaultLocationChange("")
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(text = suggestion)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.padding(8.dp))

                        ButtonMMD(onClick = {
                            viewModel.setDefaultLocation(newDefaultLocation)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Default location saved")
                            }
                            viewModel.onDefaultLocationChange("")
                            isEditingLocation = false
                        }) {
                            Text("Save")
                        }
                    } else {
                        Text(
                            text = "Default Location:",
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.padding(4.dp))

                        Text(
                            text = defaultLocation ?: "",
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.padding(8.dp))

                        ButtonMMD(onClick = { isEditingLocation = true }) {
                            Text("Edit")
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.padding(16.dp))
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Current Location:",
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = currentLocation,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
