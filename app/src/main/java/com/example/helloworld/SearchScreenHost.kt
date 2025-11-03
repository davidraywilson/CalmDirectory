package com.example.helloworld

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun SearchScreenHost(
    navController: NavController,
    query: String,
    searchViewModel: SearchViewModel = viewModel()
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Re-trigger the search.
                searchViewModel.onSearchQueryChange(searchQuery)
            } else {
                // Handle the case where permission is denied.
            }
        }

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (query.isNotBlank() && searchQuery != query) {
        searchViewModel.onSearchQueryChange(query)
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicatorMMD()
        }
    } else {
        SearchScreen(
            searchResults = searchResults,
            onPoiSelected = { poi ->
                val encodedName = URLEncoder
                    .encode(poi.name, StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val addressString =
                    "${poi.address.street}, ${poi.address.city}, ${poi.address.state} ${poi.address.zip}, ${poi.address.country}"
                val encodedAddress = URLEncoder
                    .encode(addressString, StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val encodedPhone = URLEncoder
                    .encode(poi.phone ?: "N/A", StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val encodedDescription = URLEncoder
                    .encode(poi.description ?: "N/A", StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val encodedHours = URLEncoder
                    .encode(
                        if (poi.hours.isEmpty()) "N/A" else poi.hours.joinToString(","),
                        StandardCharsets.UTF_8.toString()
                    )
                    .replace("/", "%2F")
                var route =
                    "details/$encodedName/$encodedAddress/$encodedPhone/$encodedDescription/$encodedHours"
                if (poi.website != null) {
                    val encodedWebsite =
                        URLEncoder.encode(poi.website, StandardCharsets.UTF_8.toString())
                    route += "?poiWebsite=$encodedWebsite"
                }
                navController.navigate(route)
            }
        )
    }
}