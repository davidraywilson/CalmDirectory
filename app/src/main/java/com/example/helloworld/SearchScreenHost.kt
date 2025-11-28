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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun SearchScreenHost(
    navController: NavController,
    query: String,
    geoCategory: String,
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

    LaunchedEffect(query) {
        // `query` comes from a URL parameter (e.g. "Gas+Stations"). Decode it so that
        // backend mappings and UI see a human-readable string like "Gas Stations".
        val decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.toString())
        searchViewModel.onSearchQueryChange(decodedQuery)
    }

    LaunchedEffect(geoCategory) {
        // geoCategory is only meaningful when using the Geoapify backend. It scopes
        // free-text searches to a chosen top-level Geoapify category.
        val decodedCategory = URLDecoder.decode(geoCategory, StandardCharsets.UTF_8.toString())
        searchViewModel.setGeoapifyTopLevelCategory(decodedCategory)
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
                    .encode(poi.phone ?: "NA", StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val encodedDescription = URLEncoder
                    .encode(poi.description ?: "NA", StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val encodedHours = URLEncoder
                    .encode(
                        if (poi.hours.isEmpty()) "NA" else poi.hours.joinToString(","),
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
                if (poi.lat != null && poi.lng != null) {
                    route += if (poi.website != null) "&" else "?"
                    route += "lat=${poi.lat}&lng=${poi.lng}"
                }
                navController.navigate(route)
            }
        )
    }
}
