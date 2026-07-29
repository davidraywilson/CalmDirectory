package com.example.helloworld

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.UserPreferencesRepository
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun SearchScreenHost(
    navController: NavController,
    query: String,
    saveAs: String? = null,
    autoOpen: Boolean = false,
    searchViewModel: SearchViewModel = viewModel()
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val showRating by userPreferencesRepository.showRating.collectAsState(initial = true)
    val distanceUnit by userPreferencesRepository.distanceUnit.collectAsState(initial = DistanceUnit.IMPERIAL)

    val handlePoiSelection: (Poi) -> Unit = { poi ->
        if (saveAs == "NEW_QUICK_LOCATION") {
            val addressString = "${poi.address.street}, ${poi.address.city}, ${poi.address.state} ${poi.address.zip}, ${poi.address.country}"
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_label", poi.name)
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", addressString)
            navController.popBackStack()
        } else {
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
                poi.website?.let { route += "?poiWebsite=${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}" }
                if (poi.lat != null && poi.lng != null) {
                    route += if (route.contains("?")) "&" else "?"
                    route += "lat=${poi.lat}&lng=${poi.lng}"
                }
                poi.summary?.takeIf { it.isNotBlank() }?.let {
                    route += if (route.contains("?")) "&" else "?"
                    route += "poiSummary=${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}"
                }
                navController.navigate(route)
            } else if (poi.lat != null && poi.lng != null) {
                val encodedName = URLEncoder.encode(poi.name, StandardCharsets.UTF_8.toString())
                val addressString = "${poi.address.street}, ${poi.address.city}, ${poi.address.state} ${poi.address.zip}, ${poi.address.country}"
                val encodedAddress = URLEncoder.encode(addressString, StandardCharsets.UTF_8.toString())
                navController.navigate("map?poiName=$encodedName&poiAddress=$encodedAddress&isPlace=false&lat=${poi.lat}&lng=${poi.lng}")
            }
        }
    }

    LaunchedEffect(query) {
        val decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.toString())
        if (searchQuery.isBlank()) {
            searchViewModel.onSearchQueryChange(decodedQuery)
        }
    }

    LaunchedEffect(searchResults, isLoading) {
        if (autoOpen && !isLoading && searchResults.isNotEmpty()) {
            handlePoiSelection(searchResults.first())
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicatorMMD()
        }
    } else {
        SearchScreen(
            searchResults = searchResults,
            showRating = showRating,
            distanceUnit = distanceUnit,
            onPoiSelected = handlePoiSelection
        )
    }
}