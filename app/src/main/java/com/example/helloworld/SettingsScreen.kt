package com.example.helloworld

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.MapApp
import com.example.helloworld.data.SearchProvider
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.slider.SliderMMD
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel()
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
    val searchProvider by userPreferencesRepository.searchProvider.collectAsState(initial = SearchProvider.GEOAPIFY)
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetMMDState()

    val searchRadius by userPreferencesRepository.searchRadius.collectAsState(initial = 10)
    var sliderValue by remember(searchRadius) { mutableStateOf(searchRadius.toFloat()) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    fun maskApiKey(key: String): String {
        if (key.length <= 4) {
            return "x".repeat(key.length)
        }
        return "x".repeat(key.length - 4) + key.takeLast(4)
    }

    if (openBottomSheet) {
        ModalBottomSheetMMD(
            onDismissRequest = { openBottomSheet = false },
            sheetState = bottomSheetState,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Select Map App", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(8.dp))
                MapApp.values().forEach { app ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = app.name
                                    .replace("_", " ")
                                    .lowercase()
                                    .replaceFirstChar { it.uppercase() }
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = mapApp == app,
                                onClick = null
                            )
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                userPreferencesRepository.saveMapApp(app)
                                bottomSheetState.hide()
                            }.invokeOnCompletion {
                                if (!bottomSheetState.isVisible) {
                                    openBottomSheet = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(text = "Data Provider", fontSize = 16.sp)
                Spacer(modifier = Modifier.padding(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    RadioButton(
                        selected = searchProvider == SearchProvider.GOOGLE_PLACES,
                        onClick = {
                            coroutineScope.launch {
                                userPreferencesRepository.saveSearchProvider(SearchProvider.GOOGLE_PLACES)
                                snackbarHostState.showSnackbar("Using Google Places backend")
                            }
                        }
                    )
                    Text(text = "Google Places", modifier = Modifier.padding(start = 8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    RadioButton(
                        selected = searchProvider == SearchProvider.GEOAPIFY,
                        onClick = {
                            coroutineScope.launch {
                                userPreferencesRepository.saveSearchProvider(SearchProvider.GEOAPIFY)
                                snackbarHostState.showSnackbar("Using Geoapify backend")
                            }
                        }
                    )
                    Text(text = "Geoapify", modifier = Modifier.padding(start = 8.dp))
                }

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
                        .padding(vertical = 8.dp)
                )
            }
        }

        item {
            HorizontalDividerMMD(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        item {
            Spacer(modifier = Modifier.padding(16.dp))
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Use device location",
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                SwitchMMD(
                    checked = useDeviceLocation,
                    onCheckedChange = { viewModel.setUseDeviceLocation(it) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.padding(8.dp))
        }

        if (!useDeviceLocation) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {

                    if (isEditingLocation) {
                        TextFieldMMD(
                            value = newDefaultLocation,
                            onValueChange = {
                                newDefaultLocation = it
                                viewModel.onDefaultLocationChange(it)
                            },
                            label = { Text("Default Location") }
                        )
                        locationSuggestions.forEach { suggestion ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        newDefaultLocation = suggestion
                                        viewModel.onDefaultLocationChange("")
                                    }
                            ) {
                                Text(
                                    text = suggestion,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.padding(8.dp))

                        ButtonMMD(onClick = {
                            viewModel.setDefaultLocation(newDefaultLocation)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Default location saved")
                            }
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
                Text(
                    text = "Current Location:",
                    fontSize = 16.sp,
                )
            }
            item {
                Spacer(modifier = Modifier.padding(4.dp))
            }
            item {
                Text(
                    text = currentLocation,
                    fontSize = 14.sp,
                )
            }
        }



        // THIS MIGHT NOT BE NECESSARY RIGHT NOW BUT LEAVING //

//        item { Spacer(modifier = Modifier.padding(16.dp)) }
//        item {
//            HorizontalDividerMMD(
//                thickness = 1.dp,
//                color = MaterialTheme.colorScheme.outlineVariant
//            )
//        }
//        item { Spacer(modifier = Modifier.padding(16.dp)) }
//
//        item {
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                modifier = Modifier.clickable { openBottomSheet = true }) {
//                Text(
//                    text = "Map App",
//                    fontSize = 16.sp,
//                )
//                Spacer(modifier = Modifier.weight(1f))
//                Text(
//                    text = mapApp.name
//                        .replace("_", " ")
//                        .lowercase()
//                        .replaceFirstChar { it.uppercase() },
//                    fontSize = 14.sp,
//                )
//            }
//        }
    }
}
