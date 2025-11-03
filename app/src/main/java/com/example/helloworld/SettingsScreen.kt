package com.example.helloworld

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val apiKey by userPreferencesRepository.apiKey.collectAsState(initial = "")
    var newApiKey by remember(apiKey) { mutableStateOf(apiKey ?: "") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostStateMMD() }
    var isEditing by remember(apiKey) { mutableStateOf(apiKey.isNullOrEmpty()) }
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

    fun maskApiKey(key: String): String {
        if (key.length <= 4) {
            return "x".repeat(key.length)
        }
        return "x".repeat(key.length - 4) + key.takeLast(4)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        if (isEditing) {
            TextFieldMMD(
                value = newApiKey,
                onValueChange = { newApiKey = it },
                label = { Text("API Key") }
            )

            Spacer(modifier = Modifier.padding(8.dp))

            ButtonMMD(onClick = {
                coroutineScope.launch {
                    userPreferencesRepository.saveApiKey(newApiKey)
                    snackbarHostState.showSnackbar("API Key saved successfully")
                    isEditing = false
                }
            }) {
                Text("Save")
            }
        } else {
            Text(
                text = "API Key:",
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.padding(4.dp))

            Text(
                text = maskApiKey(apiKey ?: ""),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.padding(8.dp))

            ButtonMMD(onClick = { isEditing = true }) {
                Text("Edit")
            }
        }

        Spacer(modifier = Modifier.padding(16.dp))
        HorizontalDividerMMD(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.padding(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
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

        Spacer(modifier = Modifier.padding(8.dp))

        if (!useDeviceLocation) {
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
            Spacer(modifier = Modifier.padding(16.dp))
        } else {
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