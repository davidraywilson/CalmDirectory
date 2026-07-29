package com.example.helloworld

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Clear
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel(),
    scrollToLocationSettings: Boolean = false
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val offlineManager = remember { OfflineMapManager(context) }
    val currentLocation by viewModel.currentLocation.collectAsState()
    val useDeviceLocation by viewModel.useDeviceLocation.collectAsState(initial = true)
    val defaultLocation by viewModel.defaultLocation.collectAsState(initial = "")
    var newDefaultLocation by remember(defaultLocation) { mutableStateOf(defaultLocation ?: "") }
    var isEditingLocation by remember(defaultLocation) { mutableStateOf(defaultLocation.isNullOrEmpty()) }
    val locationSuggestions by viewModel.locationSuggestions.collectAsState()

    val distanceUnit by userPreferencesRepository.distanceUnit.collectAsState(initial = DistanceUnit.IMPERIAL)
    val quickLocations by userPreferencesRepository.quickLocations.collectAsState(initial = emptyList())

    var offlineRegions by remember { mutableStateOf<List<OfflineRegionItem>>(emptyList()) }
    var refreshRegionsTrigger by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostStateMMD() }
    val locationSettingsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val suggestionsBringIntoViewRequester = remember { BringIntoViewRequester() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Search", "Maps")

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

    LaunchedEffect(selectedTabIndex, refreshRegionsTrigger) {
        if (selectedTabIndex == 1) {
            offlineManager.getOfflineRegions().collect { regions ->
                offlineRegions = regions
            }
        }
    }
    LaunchedEffect(scrollToLocationSettings) {
        if (scrollToLocationSettings) {
            selectedTabIndex = 0
            locationSettingsBringIntoViewRequester.bringIntoView()
            navController.previousBackStackEntry?.savedStateHandle?.set("scrollToLocationSettings", false)
        }
    }

    LaunchedEffect(isEditingLocation, locationSuggestions) {
        if (isEditingLocation && locationSuggestions.isNotEmpty()) {
            suggestionsBringIntoViewRequester.bringIntoView()
        }
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val selectedLabel by savedStateHandle?.getStateFlow<String?>("selected_label", null)?.collectAsState() ?: remember { mutableStateOf(null) }
    val selectedAddress by savedStateHandle?.getStateFlow<String?>("selected_address", null)?.collectAsState() ?: remember { mutableStateOf(null) }

    var showQuickLocationNameDialog by remember { mutableStateOf(false) }
    var quickLocationName by remember { mutableStateOf("") }
    var pendingQuickLocationAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedAddress) {
        if (selectedAddress != null && selectedLabel != null) {
            selectedTabIndex = 1
            quickLocationName = ""
            pendingQuickLocationAddress = selectedAddress!!
            showQuickLocationNameDialog = true
            savedStateHandle?.remove<String>("selected_label")
            savedStateHandle?.remove<String>("selected_address")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRowMMD(
                selectedTabIndex = selectedTabIndex
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        item {
                            Spacer(modifier = Modifier.padding(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(text = "Use device location", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

                        item { Spacer(modifier = Modifier.padding(8.dp)) }

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
                                            onSearch = { },
                                            expanded = true,
                                            onExpandedChange = { },
                                            placeholder = { Text("Default Location") }
                                        )

                                        if (visibleSuggestions.isNotEmpty()) {
                                            Spacer(modifier = Modifier.padding(top = 8.dp))
                                            Card(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(suggestionsBringIntoViewRequester)) {
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
                                        Text(text = "Default Location:", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.padding(4.dp))
                                        Text(text = defaultLocation ?: "", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.padding(8.dp))
                                        ButtonMMD(onClick = { isEditingLocation = true }) {
                                            Text("Edit")
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    Text(text = "Current Location:", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text(text = currentLocation, fontSize = 14.sp)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(48.dp)) }
                    }
                }

                1 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text(text = "Distance Units", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.padding(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                userPreferencesRepository.saveDistanceUnit(DistanceUnit.IMPERIAL)
                                            }
                                        }
                                ) {
                                    RadioButtonMMD(
                                        selected = distanceUnit == DistanceUnit.IMPERIAL,
                                        onClick = null,
                                        modifier = Modifier.semantics { contentDescription = "Imperial" }
                                    )
                                    Text(text = "Imperial (mi, ft)", modifier = Modifier.padding(start = 8.dp))
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                userPreferencesRepository.saveDistanceUnit(DistanceUnit.METRIC)
                                            }
                                        }
                                ) {
                                    RadioButtonMMD(
                                        selected = distanceUnit == DistanceUnit.METRIC,
                                        onClick = null,
                                        modifier = Modifier.semantics { contentDescription = "Metric" }
                                    )
                                    Text(text = "Metric (km, m)", modifier = Modifier.padding(start = 8.dp))
                                }

                                Spacer(modifier = Modifier.padding(16.dp))
                                HorizontalDividerMMD(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.padding(16.dp))
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text(text = "Quick Locations", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                if (quickLocations.isEmpty()) {
                                    Text("No quick locations set", fontSize = 14.sp)
                                } else {
                                    quickLocations.forEach { location ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(location.label, fontWeight = FontWeight.SemiBold)
                                                Text(location.address, fontSize = 14.sp)
                                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    userPreferencesRepository.removeQuickLocation(location.id)
                                    snackbarHostState.showSnackbar("Location removed")
                                }
                            }) {
                                                Icon(Icons.Sharp.Clear, "Remove Location")
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                ButtonMMD(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { navController.navigate("search?autoFocus=true&saveAs=NEW_QUICK_LOCATION") }
                                ) {
                                    Text("Add Quick Location")
                                }

                                Spacer(modifier = Modifier.padding(16.dp))
                                HorizontalDividerMMD(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.padding(16.dp))
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text(text = "Offline Maps", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                if (offlineRegions.isEmpty()) {
                                    Text("No offline regions downloaded", fontSize = 14.sp)
                                } else {
                                    offlineRegions.forEach { region ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(region.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    if(region.status == RegionStatus.DOWNLOADING) "Downloading..." else "Available",
                                                    fontSize = 12.sp,
                                                    color = if(region.status == RegionStatus.DOWNLOADING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = {
                                                offlineManager.deleteRegion(region.id) { success ->
                                                    if (success) {
                                                        refreshRegionsTrigger++
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Region deleted")
                                                        }
                                                    }
                                                }
                                            }) {
                                                Icon(Icons.Sharp.Delete, "Delete Region")
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                ButtonMMD(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { navController.navigate("offline_selector") }
                                ) {
                                    Text("Download New Offline Map")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        item { Spacer(modifier = Modifier.height(48.dp)) }
                    }
                }
            }
        }

        SnackbarHostMMD(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showQuickLocationNameDialog) {
        ModalBottomSheetMMD(onDismissRequest = { showQuickLocationNameDialog = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Name this Quick Location", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                TextFieldMMD(
                    value = quickLocationName,
                    onValueChange = { quickLocationName = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(pendingQuickLocationAddress ?: "", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                ButtonMMD(
                    onClick = {
                        showQuickLocationNameDialog = false
                        coroutineScope.launch {
                            userPreferencesRepository.addQuickLocation(
                                label = quickLocationName.ifBlank { pendingQuickLocationAddress ?: "" },
                                address = pendingQuickLocationAddress ?: ""
                            )
                            pendingQuickLocationAddress = null
                            snackbarHostState.showSnackbar("Quick location added")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
