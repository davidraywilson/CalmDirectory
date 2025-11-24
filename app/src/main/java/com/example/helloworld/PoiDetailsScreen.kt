package com.example.helloworld

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiDetailsScreen(
    poiName: String,
    poiAddress: String,
    poiPhone: String,
    poiDescription: String,
    poiHours: List<String>,
    poiWebsite: String?,
    poiLat: Double?,
    poiLng: Double?,
    navController: NavController
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostStateMMD() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = {
            SnackbarHostMMD(
                hostState = snackbarHostState,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val formattedAddress = formatAddress(poiAddress)
                Text(text = formattedAddress)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp), // space between buttons
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- Address Icon Block ---
                IconButton(onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("address", formatAddress(poiAddress))
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
                // --- End Address Block ---

                // --- Coordinates Icon Block ---
                IconButton(onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
                // --- End Coordinates Block ---
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = formatPhoneNumber(poiPhone))

            Spacer(modifier = Modifier.height(24.dp))

            poiHours.forEach { hour ->
                Text(text = formatHours(hour))

                if (hour != poiHours.last()) {
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
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

fun formatPhoneNumber(phoneNumber: String): String {
    val sanitizedPhoneNumber = phoneNumber.trim()
    val regex = Regex("""(\d{3})(\d{3})(\d{4})""")
    return sanitizedPhoneNumber.replace(regex, "($1) $2-$3")
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
