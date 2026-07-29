package com.example.helloworld

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.sharp.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.helloworld.ui.theme.CalmMapsTheme
import java.net.URLEncoder
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

val LocalPipMode = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var isPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CalmMapsTheme {
                CompositionLocalProvider(LocalPipMode provides isPipMode) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val searchViewModel: SearchViewModel = viewModel()
                    val focusRequester = remember { FocusRequester() }
                    val currentRoute = navBackStackEntry?.destination?.route
                    val isFullScreenMapRoute = currentRoute?.startsWith("map?") == true ||
                        currentRoute?.startsWith("navigation_active?") == true

                    Scaffold(
                        topBar = {
                            if (!isFullScreenMapRoute && !isPipMode && currentRoute != "offline_selector" &&
                                currentRoute?.startsWith("navigation_active?") != true) {
                                DirectoryTopAppBar(
                                    navController = navController,
                                    navBackStackEntry = navBackStackEntry,
                                    searchViewModel = searchViewModel,
                                    focusRequester = focusRequester
                                )
                            }
                        }
                    ) { paddingValues ->
                        val navModifier = if (isFullScreenMapRoute || currentRoute == "offline_selector") {
                            Modifier
                        } else {
                            Modifier.padding(paddingValues)
                        }

                        NavHost(
                            navController = navController,
                            startDestination = "main",
                            modifier = navModifier,
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None },
                        ) {
                            composable("main") { MainScreen(navController) }
                            composable("settings") {
                                val scrollToLocationSettings =
                                    navController.previousBackStackEntry?.savedStateHandle?.get<Boolean>(
                                        "scrollToLocationSettings"
                                    ) == true
                                SettingsScreen(
                                    navController = navController,
                                    scrollToLocationSettings = scrollToLocationSettings
                                )
                            }

                            composable("offline_selector") {
                                OfflineRegionSelectorScreen(navController)
                            }

                            composable(
                                "search?query={query}&autoFocus={autoFocus}&saveAs={saveAs}&autoOpen={autoOpen}",
                                arguments = listOf(
                                    navArgument("query") { defaultValue = ""; type = NavType.StringType },
                                    navArgument("autoFocus") { defaultValue = false; type = NavType.BoolType },
                                    navArgument("saveAs") {
                                        defaultValue = null;
                                        type = NavType.StringType;
                                        nullable = true
                                    },
                                    navArgument("autoOpen") { defaultValue = false; type = NavType.BoolType }
                                )
                            ) { backStackEntry ->
                                val query = backStackEntry.arguments?.getString("query") ?: ""
                                val autoFocus = backStackEntry.arguments?.getBoolean("autoFocus") ?: false
                                val saveAs = backStackEntry.arguments?.getString("saveAs")
                                var wasFocused by rememberSaveable { mutableStateOf(false) }
                                val autoOpen = backStackEntry.arguments?.getBoolean("autoOpen") ?: false

                                LaunchedEffect(autoFocus) {
                                    if (autoFocus && !wasFocused) {
                                        delay(100)
                                        try {
                                            focusRequester.requestFocus()
                                            wasFocused = true
                                        } catch (e: Exception) {
                                            wasFocused = false
                                        }
                                    }
                                }

                                SearchScreenHost(
                                    navController = navController,
                                    query = query,
                                    saveAs = saveAs,
                                    autoOpen = autoOpen,
                                    searchViewModel = searchViewModel
                                )
                            }

                            composable(
                                "navigation_active?lat={lat}&lng={lng}",
                                arguments = listOf(
                                    navArgument("lat") { type = NavType.FloatType },
                                    navArgument("lng") { type = NavType.FloatType }
                                )
                            ) { backStackEntry ->
                                val poiLat: Double = backStackEntry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
                                val poiLng: Double = backStackEntry.arguments?.getFloat("lng")?.toDouble() ?: 0.0
                                ActiveNavigationScreen(navController, poiLat, poiLng)
                            }

                            composable(
                                "map?poiName={poiName}&poiAddress={poiAddress}&isPlace={isPlace}&lat={lat}&lng={lng}",
                                arguments = listOf(
                                    navArgument("poiName") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("poiAddress") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("isPlace") { type = NavType.BoolType; defaultValue = true },
                                    navArgument("lat") { type = NavType.FloatType },
                                    navArgument("lng") { type = NavType.FloatType }
                                )
                            ) { backStackEntry ->
                                val encodedName = backStackEntry.arguments?.getString("poiName") ?: ""
                                val poiName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())
                                val encodedAddress = backStackEntry.arguments?.getString("poiAddress") ?: ""
                                val poiAddress = URLDecoder.decode(encodedAddress, StandardCharsets.UTF_8.toString())
                                val isPlace = backStackEntry.arguments?.getBoolean("isPlace") ?: true
                                val poiLat: Double = backStackEntry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
                                val poiLng: Double = backStackEntry.arguments?.getFloat("lng")?.toDouble() ?: 0.0

                                var navReady by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { navReady = true }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (navReady) {
                                        NavigationScreen(navController, poiName, poiAddress, isPlace, poiLat, poiLng)
                                    } else {
                                        CircularProgressIndicatorMMD()
                                    }
                                }
                            }

                            composable(
                                "details/{poiName}/{poiAddress}/{poiCountry}/{poiPhone}/{poiDescription}/{poiHours}?poiWebsite={poiWebsite}&lat={lat}&lng={lng}&poiSummary={poiSummary}",
                                arguments = listOf(
                                    navArgument("poiWebsite") { type = NavType.StringType; nullable = true },
                                    navArgument("lat") { type = NavType.FloatType },
                                    navArgument("lng") { type = NavType.FloatType },
                                    navArgument("poiSummary") { type = NavType.StringType; nullable = true; defaultValue = null }
                                )
                            ) { backStackEntry ->
                                val poiName = URLDecoder.decode(
                                    backStackEntry.arguments?.getString("poiName")?.replace("%2F", "/"),
                                    StandardCharsets.UTF_8.toString()
                                )
                                val poiAddress = URLDecoder.decode(
                                    backStackEntry.arguments?.getString("poiAddress")?.replace("%2F", "/"),
                                    StandardCharsets.UTF_8.toString()
                                )
                                val poiCountry = URLDecoder.decode(
                                    backStackEntry.arguments?.getString("poiCountry")?.replace("%2F", "/"),
                                    StandardCharsets.UTF_8.toString()
                                )
                                val rawPoiPhone = backStackEntry.arguments?.getString("poiPhone")
                                    ?.replace("%2F", "/")
                                    ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
                                    ?: ""
                                val poiPhone = if (rawPoiPhone == "NA" || rawPoiPhone == "N/A") "" else rawPoiPhone
                                val poiDescription = URLDecoder.decode(
                                    backStackEntry.arguments?.getString("poiDescription")?.replace("%2F", "/"),
                                    StandardCharsets.UTF_8.toString()
                                )
                                val poiHoursString = URLDecoder.decode(
                                    backStackEntry.arguments?.getString("poiHours")?.replace("%2F", "/"),
                                    StandardCharsets.UTF_8.toString()
                                )
                                val poiHours = if (poiHoursString == "NA" || poiHoursString == "N/A") emptyList() else poiHoursString.split(",")
                                val poiLat: Double? = backStackEntry.arguments?.getFloat("lat")?.toDouble()
                                val poiLng: Double? = backStackEntry.arguments?.getFloat("lng")?.toDouble()
                                val poiWebsite = backStackEntry.arguments?.getString("poiWebsite")
                                val poiSummary = backStackEntry.arguments?.getString("poiSummary")?.let {
                                    URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
                                }

                                PoiDetailsScreen(poiName, poiAddress, poiCountry, poiPhone, poiDescription, poiHours, poiWebsite, poiLat, poiLng, poiSummary, navController)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (NavigationManager.isNavigationActive.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val aspectRatio = Rational(16, 9)
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) NavigationManager.setAppInForeground(false)
    }

    override fun onResume() {
        super.onResume()
        NavigationManager.setAppInForeground(true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryTopAppBar(
    navController: NavHostController,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
    searchViewModel: SearchViewModel,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val route = navBackStackEntry?.destination?.route

    Column {
        TopAppBarMMD(
            title = {
                if (route?.startsWith("search") == true) {
                    CompositionLocalProvider(
                        LocalTextStyle provides TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        SearchBarDefaultsMMD.InputField(
                            query = searchQuery,
                            onQueryChange = { searchViewModel.onSearchQueryChange(it) },
                            onSearch = { },
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
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }
                } else {
                    when (route) {
                        "main" -> Text("CalmMaps", fontWeight = FontWeight.Bold)
                        "settings" -> Text("Settings", fontWeight = FontWeight.Bold)
                        else -> {
                            if (route?.startsWith("details") == true) {
                                val poiName = navBackStackEntry?.arguments?.getString("poiName")
                                Text(
                                    text = URLDecoder.decode(poiName ?: "", StandardCharsets.UTF_8.toString()),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                if (route != "main") {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                if (route == "main") {
                    IconButton(onClick = {
                        searchViewModel.resetSearch()
                        navController.navigate("search?autoFocus=true")
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                } else if (route?.startsWith("details") == true) {
                    val website = navBackStackEntry?.arguments?.getString("poiWebsite")
                    val lat = navBackStackEntry?.arguments?.getFloat("lat")
                    val lng = navBackStackEntry?.arguments?.getFloat("lng")
                    val country = navBackStackEntry?.arguments?.getString("poiCountry")
                        ?.replace("%2F", "/")
                        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
                        ?: ""
                    val rawPhone = navBackStackEntry?.arguments?.getString("poiPhone")
                        ?.replace("%2F", "/")
                        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
                        ?: ""
                    val phone = if (rawPhone == "NA" || rawPhone == "N/A") "" else rawPhone

                    if (website != null) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website)))
                        }) {
                            Icon(Icons.Outlined.Language, contentDescription = "Website", modifier = Modifier.size(28.dp))
                        }
                    }

                    if (lat != null && lng != null) {
                        IconButton(onClick = {
                            val name = navBackStackEntry.arguments?.getString("poiName") ?: ""
                            val addr = navBackStackEntry.arguments?.getString("poiAddress") ?: ""
                            val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                            val encodedAddr = URLEncoder.encode(addr, StandardCharsets.UTF_8.toString())
                            navController.navigate("map?poiName=$encodedName&poiAddress=$encodedAddr&isPlace=true&lat=$lat&lng=$lng")
                        }) {
                            Icon(Icons.Outlined.Map, contentDescription = "Map", modifier = Modifier.size(28.dp))
                        }
                    }

                    val dialNumber = formatPhoneNumberForDial(phone, country)
                    if (dialNumber.any { it.isDigit() }) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber")))
                        }) {
                            Icon(Icons.Outlined.Phone, contentDescription = "Call", modifier = Modifier.size(28.dp))
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
    CalmMapsTheme {
        MainScreen(rememberNavController())
    }
}