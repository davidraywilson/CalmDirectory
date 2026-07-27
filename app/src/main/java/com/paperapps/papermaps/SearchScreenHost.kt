package com.paperapps.papermaps

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.sharp.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.paperapps.paperui.components.AppbarAction
import com.paperapps.paperui.components.ApplicationBar
import com.paperapps.paperui.components.PanoramaHeader
import com.paperapps.paperui.components.PanoramaPager
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
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
    val decodedQuery = remember(query) { URLDecoder.decode(query, StandardCharsets.UTF_8.toString()) }
    val displayTitle = if (decodedQuery.isNotBlank()) decodedQuery else "Search"

    var isSearchFocused by remember { mutableStateOf(autoOpen) }
    val focusRequester = remember { FocusRequester() }

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
        if (searchQuery.isBlank()) {
            searchViewModel.onSearchQueryChange(decodedQuery)
        }
    }

    var autoOpenConsumed by remember { mutableStateOf(false) }
    LaunchedEffect(searchResults, isLoading) {
        if (autoOpen && !autoOpenConsumed && !isLoading && searchResults.isNotEmpty()) {
            autoOpenConsumed = true
            handlePoiSelection(searchResults.first())
        }
    }

    LaunchedEffect(isSearchFocused) {
        if (isSearchFocused) {
            focusRequester.requestFocus()
        }
    }


    val pages = listOf("Results")
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearchFocused || decodedQuery.isBlank()) {
            SearchBarDefaultsMMD.InputField(
                query = searchQuery,
                onQueryChange = { searchViewModel.onSearchQueryChange(it) },
                onSearch = { isSearchFocused = false },
                expanded = true,
                onExpandedChange = { },
                placeholder = { Text("Search for a place") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchViewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Sharp.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12)
                    )
                    .focusRequester(focusRequester)
            )
        }

        PanoramaHeader(
            pagerState = pagerState,
            titles = pages,
            coroutineScope = rememberCoroutineScope(),
            screenTitle = displayTitle
        )

        PanoramaPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { _ ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicatorMMD()
                }
            } else {
                SearchScreen(
                    searchResults = searchResults,
                    modifier = Modifier.fillMaxSize(),
                    onPoiSelected = handlePoiSelection
                )
            }
        }

        val actions = mutableListOf<AppbarAction>()
        actions.add(
            AppbarAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back",
                onClick = { navController.popBackStack() }
            )
        )
        actions.add(
            AppbarAction(
                icon = Icons.Outlined.Search,
                label = "Search",
                onClick = { isSearchFocused = true }
            )
        )
        ApplicationBar(actions = actions)
    }
}