package com.paperapps.papermaps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavController
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.SheetValue
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.paperapps.paperui.components.AppbarAction
import com.paperapps.paperui.components.ApplicationBar
import com.paperapps.paperui.components.PanoramaHeader
import com.paperapps.paperui.components.PanoramaPager
import com.paperapps.paperui.components.PaperLazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.paperapps.papermaps.data.UserPreferencesRepository
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiDetailsScreen(
    poiName: String,
    poiAddress: String,
    poiCountry: String,
    poiPhone: String,
    poiDescription: String,
    poiHours: List<String>,
    poiWebsite: String?,
    poiLat: Double?,
    poiLng: Double?,
    placeId: String?,
    navController: NavController
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostStateMMD() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var mapAction by remember { mutableStateOf<AppbarAction?>(null) }

    val decodedName = remember(poiName) { URLDecoder.decode(poiName, StandardCharsets.UTF_8.toString()) }
    var phone by remember { mutableStateOf(poiPhone) }
    var hours by remember { mutableStateOf(poiHours) }
    val currentBackStackEntry = navController.currentBackStackEntry

    val userPrefRepo = remember { UserPreferencesRepository(context) }
    val placesService = remember { GooglePlacesApiService(userPrefRepo) }
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    var isLoadingReviews by remember { mutableStateOf(false) }

    var selectedReview by remember { mutableStateOf<Review?>(null) }
    val reviewBottomSheetState = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = false,
        confirmValueChange = { true }
    )

    LaunchedEffect(placeId) {
        if (placeId != null) {
            isLoadingReviews = true
            reviews = placesService.getReviews(placeId)
            isLoadingReviews = false
        }
    }

    LaunchedEffect(phone) {
        currentBackStackEntry?.savedStateHandle?.set("effectivePoiPhone", phone)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanoramaHeader(
                pagerState = pagerState,
                titles = listOf("details", "reviews"),
                coroutineScope = scope,
                screenTitle = decodedName
            )

            PanoramaPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        PaperLazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 16.dp)
                        ) {
                            item {
                                val formattedAddress = formatAddress(URLDecoder.decode(poiAddress, StandardCharsets.UTF_8.toString()))
                                Text(text = formattedAddress, modifier = Modifier.fillMaxWidth())
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val decodedAddr = URLDecoder.decode(poiAddress, StandardCharsets.UTF_8.toString())
                                        val clip = ClipData.newPlainText("address", formatAddress(decodedAddr))
                                        clipboard.setPrimaryClip(clip)

                                        scope.launch {
                                            snackbarHostState.showSnackbar("Address Copied to Clipboard")
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "Address"
                                        )
                                    }

                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("coordinates", formatCoordinates(poiLat, poiLng))
                                        clipboard.setPrimaryClip(clip)

                                        scope.launch {
                                            snackbarHostState.showSnackbar("Coordinates Copied to Clipboard")
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.GpsNotFixed,
                                            contentDescription = "GPS Coordinates"
                                        )
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            item {
                                Text(text = formatPhoneNumberForCountry(phone, URLDecoder.decode(poiCountry, StandardCharsets.UTF_8.toString())))
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            items(hours.size) { index ->
                                val hour = hours[index]
                                Text(text = formatHours(URLDecoder.decode(hour, StandardCharsets.UTF_8.toString())))

                                if (index != hours.lastIndex) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        if (isLoadingReviews) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Loading reviews...")
                            }
                        } else if (reviews.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No reviews available.")
                            }
                        } else {
                            PaperLazyColumn(modifier = Modifier.fillMaxSize().padding(end = 16.dp)) {
                                items(reviews.size) { index ->
                                    val review = reviews[index]
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 24.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = review.authorName, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(text = review.relativeTime, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                        }
                                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                            for (i in 1..5) {
                                                Icon(
                                                    imageVector = if (i <= review.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    contentDescription = null,
                                                    modifier = Modifier.height(16.dp),
                                                    tint = androidx.compose.ui.graphics.Color(0xFFFFC107)
                                                )
                                            }
                                        }
                                        Text(
                                            text = review.text,
                                            maxLines = 3,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable {
                                                selectedReview = review
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
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
            if (pagerState.currentPage == 0) {
                if (poiWebsite != null && poiWebsite.isNotBlank() && poiWebsite != "NA") {
                    actions.add(
                        AppbarAction(
                            icon = Icons.Outlined.Language,
                            label = "Website",
                            onClick = {
                                val decodedWebsite = URLDecoder.decode(poiWebsite, StandardCharsets.UTF_8.toString())
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(decodedWebsite)))
                            }
                        )
                    )
                }

                val decodedPhone = URLDecoder.decode(phone, StandardCharsets.UTF_8.toString())
                val decodedCountry = URLDecoder.decode(poiCountry, StandardCharsets.UTF_8.toString())
                val dialNumber = formatPhoneNumberForDial(decodedPhone, decodedCountry)
                if (dialNumber.any { it.isDigit() }) {
                    actions.add(
                        AppbarAction(
                            icon = Icons.Outlined.Phone,
                            label = "Call",
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber")))
                            }
                        )
                    )
                }
            } 
            
            if (poiLat != null && poiLng != null) {
                actions.add(
                    AppbarAction(
                        icon = Icons.Outlined.Map,
                        label = "Map",
                        onClick = {
                            val encodedName = URLEncoder.encode(decodedName, StandardCharsets.UTF_8.toString())
                            val decodedAddress = URLDecoder.decode(poiAddress, StandardCharsets.UTF_8.toString())
                            val encodedAddress = URLEncoder.encode(decodedAddress, StandardCharsets.UTF_8.toString())
                            navController.navigate("map?poiName=$encodedName&poiAddress=$encodedAddress&isPlace=true&lat=$poiLat&lng=$poiLng")
                        }
                    )
                )
            }
            
            ApplicationBar(actions = actions)
        }

        if (selectedReview != null) {
            ModalBottomSheetMMD(
                onDismissRequest = { selectedReview = null },
                sheetState = reviewBottomSheetState
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = selectedReview!!.authorName, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp)
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= selectedReview!!.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.height(20.dp),
                                tint = androidx.compose.ui.graphics.Color(0xFFFFC107)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = selectedReview!!.relativeTime, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                    Text(text = selectedReview!!.text, modifier = Modifier.padding(top = 8.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        SnackbarHostMMD(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }
}

fun formatAddress(address: String): String {
    val parts = address.split(", ")
    return when (parts.size) {
        4 -> "${parts[0]}\n${parts[1]}, ${parts[2]}\n${parts[3]}"
        3 -> "${parts[0]}\n${parts[1]}, ${parts[2]}"
        else -> address
    }
}

fun formatCoordinates(lat: Double?, lng: Double?, precision: Int = 6): String {
    return if (lat == null || lng == null) ""
    else "%.${precision}f, %.${precision}f".format(lat, lng)
}

fun formatHour(time: String): String {
    return try {
        val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val date = inputFormat.parse(time.trim())
        outputFormat.format(date)
    } catch (e: ParseException) {
        time // Return original time if parsing fails
    }
}

fun formatHours(hours: String): String {
    val timePattern = Regex("""(\d{2}:\d{2})""")
    val matches = timePattern.findAll(hours).toList()

    if (matches.size == 2) {
        val (startMatch, endMatch) = matches
        val start = formatHour(startMatch.value)
        val end = formatHour(endMatch.value)

        val prefix = hours.substringBefore(startMatch.value)
        return "$prefix$start - $end"
    }

    return hours
}