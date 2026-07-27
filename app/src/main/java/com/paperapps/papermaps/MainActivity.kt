package com.paperapps.papermaps

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.paperapps.paperui.theme.PaperUITheme
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

val LocalPipMode = compositionLocalOf { false }
val LocalSharedMapViewModel = compositionLocalOf<SharedMapViewModel> { error("No SharedMapViewModel provided") }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var isPipMode by mutableStateOf(false)
    private var pendingDeepLinkRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        pendingDeepLinkRoute = handleIntent(intent)

        setContent {
            PaperUITheme {
                CompositionLocalProvider(LocalPipMode provides isPipMode) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val searchViewModel: SearchViewModel = viewModel()
                    val sharedMapViewModel: SharedMapViewModel = viewModel()
                    val focusRequester = remember { FocusRequester() }
                    val currentRoute = navBackStackEntry?.destination?.route
                    val isFullScreenMapRoute = currentRoute?.startsWith("map?") == true ||
                        currentRoute?.startsWith("navigation_active?") == true

                    LaunchedEffect(pendingDeepLinkRoute) {
                        pendingDeepLinkRoute?.let { route ->
                            navController.navigate(route) {
                                // Clear the entire back stack so the user presses
                                // Back and exits the app, rather than looping back
                                // through main or search screens.
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                            pendingDeepLinkRoute = null
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CompositionLocalProvider(LocalSharedMapViewModel provides sharedMapViewModel) {
                            NavHost(
                                navController = navController,
                                    startDestination = "main",
                                    modifier = Modifier.fillMaxSize(),
                                    enterTransition = { fadeIn(animationSpec = snap()) },
                                    exitTransition = { fadeOut(animationSpec = snap()) },
                                    popEnterTransition = { fadeIn(animationSpec = snap()) },
                                    popExitTransition = { fadeOut(animationSpec = snap()) }
                                ) {
                                    composable("main") { MainScreen(navController, searchViewModel = searchViewModel) }
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

                                NavigationScreen(navController, poiName, poiAddress, isPlace, poiLat, poiLng)
                            }

                            composable(
                                "details/{poiName}/{poiAddress}/{poiCountry}/{poiPhone}/{poiDescription}/{poiHours}?poiWebsite={poiWebsite}&lat={lat}&lng={lng}&placeId={placeId}",
                                arguments = listOf(
                                    navArgument("poiWebsite") { type = NavType.StringType; nullable = true },
                                    navArgument("lat") { type = NavType.FloatType },
                                    navArgument("lng") { type = NavType.FloatType },
                                    navArgument("placeId") { type = NavType.StringType; nullable = true }
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
                                val placeId = backStackEntry.arguments?.getString("placeId")

                                PoiDetailsScreen(poiName, poiAddress, poiCountry, poiPhone, poiDescription, poiHours, poiWebsite, poiLat, poiLng, placeId, navController)
                            }
                        }
                        } // end CompositionLocalProvider
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkRoute = handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme == "geo") {
            val ssp = uri.schemeSpecificPart
            if (ssp.contains("?q=")) {
                val query = ssp.substringAfter("?q=").substringBefore("&")
                return "search?query=${Uri.encode(query)}&autoFocus=false&autoOpen=false"
            } else {
                val parts = ssp.substringBefore("?").split(",")
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return "map?poiName=Location&poiAddress=Dropped+Pin&isPlace=false&lat=$lat&lng=$lng"
                    }
                }
            }
        } else if (uri.host?.contains("maps.google.com") == true || uri.host?.contains("goo.gl") == true) {
            val query = uri.getQueryParameter("q")
            if (!query.isNullOrEmpty()) {
                val parts = query.split(",")
                if (parts.size == 2 && parts[0].toDoubleOrNull() != null && parts[1].toDoubleOrNull() != null) {
                    val lat = parts[0].toDouble()
                    val lng = parts[1].toDouble()
                    return "map?poiName=Location&poiAddress=Dropped+Pin&isPlace=false&lat=$lat&lng=$lng"
                }
                return "search?query=${Uri.encode(query)}&autoFocus=false&autoOpen=false"
            }
        }
        return null
    }
}

// DirectoryTopAppBar removed for PaperUI

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PaperUITheme {
        MainScreen(rememberNavController())
    }
}