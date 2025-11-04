package com.example.helloworld

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.sharp.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.helloworld.data.MapApp
import com.example.helloworld.data.UserPreferencesRepository
import com.example.helloworld.ui.theme.CalmDirectoryTheme
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmDirectoryTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val searchViewModel: SearchViewModel = viewModel()
                val mainViewModel: MainViewModel = viewModel()
                val apiKey by mainViewModel.apiKey.collectAsState()
                val focusRequester = remember { FocusRequester() }

                Scaffold(
                    topBar = {
                        DirectoryTopAppBar(
                            navController = navController,
                            navBackStackEntry = navBackStackEntry,
                            searchViewModel = searchViewModel,
                            apiKey = apiKey,
                            focusRequester = focusRequester
                        )
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(paddingValues),
                    ) {
                        composable("main") { MainScreen(navController) }
                        composable("settings") { SettingsScreen(navController, searchViewModel = searchViewModel) }
                        composable(
                            "search?query={query}&autoFocus={autoFocus}",
                            arguments = listOf(
                                navArgument("query") {
                                    defaultValue = ""
                                    type = NavType.StringType
                                },
                                navArgument("autoFocus") {
                                    defaultValue = false
                                    type = NavType.BoolType
                                }
                            )
                        ) { backStackEntry ->
                            val query = backStackEntry.arguments?.getString("query") ?: ""
                            val autoFocus =
                                backStackEntry.arguments?.getBoolean("autoFocus") ?: false
                            var wasFocused by rememberSaveable { mutableStateOf(false) }

                            LaunchedEffect(autoFocus, wasFocused) {
                                if (autoFocus && !wasFocused) {
                                    focusRequester.requestFocus()
                                    wasFocused = true
                                 }
                            }
                            SearchScreenHost(
                                navController = navController,
                                query = query,
                                searchViewModel = searchViewModel
                            )
                        }
                        composable(
                            "details/{poiName}/{poiAddress}/{poiPhone}/{poiDescription}/{poiHours}?poiWebsite={poiWebsite}&lat={lat}&lng={lng}",
                            arguments = listOf(
                                navArgument("poiWebsite") {
                                    type = NavType.StringType
                                    nullable = true
                                },
                                navArgument("lat") {
                                    type = NavType.FloatType
                                },
                                navArgument("lng") {
                                    type = NavType.FloatType
                                }
                            )
                        ) { backStackEntry ->
                            val poiName = URLDecoder.decode(
                                backStackEntry.arguments?.getString("poiName")?.replace("%2F", "/"),
                                StandardCharsets.UTF_8.toString()
                            )
                            val poiAddress = URLDecoder.decode(
                                backStackEntry.arguments?.getString("poiAddress")
                                    ?.replace("%2F", "/"),
                                StandardCharsets.UTF_8.toString()
                            )
                            val poiPhone = URLDecoder.decode(
                                backStackEntry.arguments?.getString("poiPhone")?.replace("%2F", "/"),
                                StandardCharsets.UTF_8.toString()
                            )
                            val poiDescription = URLDecoder.decode(
                                backStackEntry.arguments?.getString("poiDescription")
                                    ?.replace("%2F", "/"),
                                StandardCharsets.UTF_8.toString()
                            )
                            val poiHoursString = URLDecoder.decode(
                                backStackEntry.arguments?.getString("poiHours")?.replace("%2F", "/"),
                                StandardCharsets.UTF_8.toString()
                            )
                            val poiHours =
                                if (poiHoursString == "N/A") emptyList() else poiHoursString.split(
                                    ","
                                )
                            val poiWebsite = backStackEntry.arguments?.getString("poiWebsite")
                            PoiDetailsScreen(
                                poiName = poiName,
                                poiAddress = poiAddress,
                                poiPhone = poiPhone,
                                poiDescription = poiDescription,
                                poiHours = poiHours,
                                poiWebsite = poiWebsite,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryTopAppBar(
    navController: NavHostController,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
    searchViewModel: SearchViewModel,
    apiKey: String?,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val mapApp by userPreferencesRepository.mapApp.collectAsState(initial = MapApp.DEFAULT)

    Column {
        TopAppBarMMD(
            title = {
                when (navBackStackEntry?.destination?.route) {
                    "main" -> Text("Directory")
                    "settings" -> Text("Settings")
                    "search?query={query}&autoFocus={autoFocus}" -> {
                        SearchBarDefaultsMMD.InputField(
                            query = searchQuery,
                            onQueryChange = { searchViewModel.onSearchQueryChange(it) },
                            onSearch = { /* Handled by LaunchedEffect */ },
                            expanded = true,
                            onExpandedChange = { },
                            placeholder = { Text("Search for a place") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchViewModel.onSearchQueryChange("") }) {
                                        Icon(
                                            Icons.Sharp.Clear,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }
                    "details/{poiName}/{poiAddress}/{poiPhone}/{poiDescription}/{poiHours}?poiWebsite={poiWebsite}&lat={lat}&lng={lng}" -> {
                        val poiName = navBackStackEntry.arguments?.getString("poiName")
                        Text(
                            text = URLDecoder.decode(poiName, StandardCharsets.UTF_8.toString()),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                when (navBackStackEntry?.destination?.route) {
                    "settings", "search?query={query}&autoFocus={autoFocus}",
                    "details/{poiName}/{poiAddress}/{poiPhone}/{poiDescription}/{poiHours}?poiWebsite={poiWebsite}&lat={lat}&lng={lng}" -> {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            },
            actions = {
                when (navBackStackEntry?.destination?.route) {
                    "main" -> {
                        if (!apiKey.isNullOrEmpty()) {
                            IconButton(onClick = { navController.navigate("search?autoFocus=true") }) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { navController.navigate("settings") }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    }

                    "details/{poiName}/{poiAddress}/{poiPhone}/{poiDescription}/{poiHours}?poiWebsite={poiWebsite}&lat={lat}&lng={lng}" -> {
                        val poiWebsite = navBackStackEntry.arguments?.getString("poiWebsite")
                        val poiAddress = navBackStackEntry.arguments?.getString("poiAddress")
                        val poiPhone = navBackStackEntry.arguments?.getString("poiPhone")
                        val lat = navBackStackEntry.arguments?.getFloat("lat")
                        val lng = navBackStackEntry.arguments?.getFloat("lng")
                        if (poiWebsite != null) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poiWebsite))
                                context.startActivity(intent)
                            }) {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = "Website",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            val decodedAddress = URLDecoder.decode(
                                poiAddress,
                                StandardCharsets.UTF_8.toString()
                            )
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("address", decodedAddress)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT)
                                .show()

                            val (uri, packageName, webUri) = when (mapApp) {
                                MapApp.GOOGLE_MAPS -> Triple(
                                    "google.navigation:q=$lat,$lng",
                                    "com.google.android.apps.maps",
                                    "https://maps.google.com/maps?q=$decodedAddress"
                                )
                                MapApp.TOMTOM -> Triple(
                                    "geo:0,0?q=$lat,$lng($decodedAddress)",
                                    "com.tomtom.gplay.navapp",
                                    "https://mydrive.tomtom.com/en_gb/#mode=search&search=$decodedAddress"
                                )
                                MapApp.HERE_WEGO -> Triple(
                                    "geo:0,0?q=$lat,$lng($decodedAddress)",
                                    "com.here.app.maps",
                                    "https://wego.here.com/search/$decodedAddress"
                                )
                                else -> Triple(
                                    "geo:$lat,$lng?q=$decodedAddress",
                                    null,
                                    "https://maps.google.com/maps?q=$decodedAddress"
                                )
                            }

                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            if (packageName != null) {
                                intent.setPackage(packageName)
                            }

                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
                                context.startActivity(webIntent)
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Map,
                                contentDescription = "Map",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$poiPhone"))
                            context.startActivity(intent)
                        }) {
                            Icon(
                                Icons.Outlined.Phone,
                                contentDescription = "Call",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        )
        HorizontalDividerMMD(
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CalmDirectoryTheme {
        MainScreen(rememberNavController())
    }
}
