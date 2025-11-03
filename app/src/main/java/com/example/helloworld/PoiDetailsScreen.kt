package com.example.helloworld

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PoiDetailsScreen(
    poiName: String,
    poiAddress: String,
    poiPhone: String,
    poiDescription: String,
    poiHours: List<String>,
    poiWebsite: String?,
    navController: NavController
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = formatAddress(poiAddress))
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

fun formatAddress(address: String): String {
    val parts = address.split(", ")
    return when (parts.size) {
        4 -> "${parts[0]}\n${parts[1]}, ${parts[2]}\n${parts[3]}"
        3 -> "${parts[0]}\n${parts[1]}, ${parts[2]}"
        else -> address
    }
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