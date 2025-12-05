package com.example.helloworld

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
    searchViewModel: SearchViewModel = viewModel()
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(query) {
        val decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.toString())
        val currentQuery = searchQuery
        if (currentQuery.isBlank()) {
            searchViewModel.onSearchQueryChange(decodedQuery)
        }
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
                val country = poi.address.country
                val encodedCountry = URLEncoder
                    .encode(country, StandardCharsets.UTF_8.toString())
                    .replace("/", "%2F")
                val rawPhone = poi.phone?.takeIf { it.isNotBlank() } ?: "NA"
                val encodedPhone = URLEncoder
                    .encode(rawPhone, StandardCharsets.UTF_8.toString())
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
                    "details/$encodedName/$encodedAddress/$encodedCountry/$encodedPhone/$encodedDescription/$encodedHours"
                if (poi.website != null) {
                    val encodedWebsite =
                        URLEncoder.encode(poi.website, StandardCharsets.UTF_8.toString())
                    route += "?poiWebsite=$encodedWebsite"
                }
                if (poi.lat != null && poi.lng != null) {
                    route += if (route.contains("?")) "&" else "?"
                    route += "lat=${poi.lat}&lng=${poi.lng}"
                }
                navController.navigate(route)
            }
        )
    }
}
