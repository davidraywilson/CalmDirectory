package com.example.helloworld

import android.content.Context
import android.util.Log
import com.example.helloworld.data.UserPreferencesRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlin.math.cos

class GooglePlacesApiService(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var placesClient: PlacesClient? = null
    private var token: AutocompleteSessionToken? = null

    private suspend fun initializePlacesClient(context: Context) {
        val apiKey = userPreferencesRepository.apiKey.first()
        if (apiKey.isNullOrEmpty()) {
            throw IllegalStateException("API key not found. Please set it in the settings.")
        }
        if (!Places.isInitialized()) {
            Places.initialize(context.applicationContext, apiKey)
        }
        placesClient = Places.createClient(context)
        token = AutocompleteSessionToken.newInstance()
    }

    suspend fun search(query: String, lat: Double, lon: Double): List<Poi> {
        if (placesClient == null) {
            initializePlacesClient(context)
        }
        // Define the fields to return for each place.
        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.OPENING_HOURS,
            Place.Field.TYPES,
            Place.Field.WEBSITE_URI,
            Place.Field.LAT_LNG
        )

        val requestBuilder = SearchByTextRequest.builder(query, placeFields)

        // Only add location restriction if a valid location is provided
        if (lat != 0.0 || lon != 0.0) {
            val miles = 10.0
            val latDegrees = miles / 69.0
            val lonDegrees = miles / (cos(Math.toRadians(lat)) * 69.0)
            val southwest = LatLng(lat - latDegrees, lon - lonDegrees)
            val northeast = LatLng(lat + latDegrees, lon + lonDegrees)
            val locationBias = RectangularBounds.newInstance(southwest, northeast)
            requestBuilder.setLocationBias(locationBias)
        }

        val request = requestBuilder.build()

        return try {
            val response = placesClient!!.searchByText(request).await()
            response.places.map { place ->
                Poi(
                    name = place.name ?: "N/A",
                    address = parseAddress(place.address),
                    hours = place.openingHours?.weekdayText ?: emptyList(),
                    phone = place.phoneNumber?.let { formatPhoneNumber(it) } ?: "N/A",
                    description = place.placeTypes?.joinToString(", ") ?: "N/A",
                    website = place.websiteUri?.toString(),
                    lat = place.latLng?.latitude,
                    lng = place.latLng?.longitude
                )
            }
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error searching for places", e)
            emptyList()
        }
    }

    suspend fun getAutocompleteSuggestions(query: String): List<String> {
        if (placesClient == null) {
            initializePlacesClient(context)
        }

        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(token)
            .setQuery(query)
            .build()

        return try {
            val response = placesClient!!.findAutocompletePredictions(request).await()
            response.autocompletePredictions.map { it.getFullText(null).toString() }
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error getting autocomplete suggestions", e)
            emptyList()
        }
    }

    private fun parseAddress(addressString: String?): Address {
        if (addressString == null) {
            return Address("N/A", "", "", "", "")
        }
        val parts = addressString.split(",").map { it.trim() }
        val street = parts.getOrElse(0) { addressString }
        val city = parts.getOrElse(1) { "" }
        val country = if (parts.size > 2 && parts.last().matches(Regex("[A-Za-z ]+"))) parts.last() else ""

        var state = ""
        var zip = ""

        if (parts.size > 2) {
            val stateZipPart = if (country.isNotEmpty()) parts[parts.size - 2] else parts.last()
            val stateZipParts = stateZipPart.split(" ")
            zip = stateZipParts.lastOrNull { it.any(Char::isDigit) } ?: ""
            state = stateZipParts.filter { it != zip }.joinToString(" ")
        }

        return Address(street, city, state, zip, country)
    }

    private fun formatPhoneNumber(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        if (digitsOnly.length == 11 && digitsOnly.startsWith("1")) {
            val areaCode = digitsOnly.substring(1, 4)
            val firstPart = digitsOnly.substring(4, 7)
            val secondPart = digitsOnly.substring(7)
            return "($areaCode) $firstPart-$secondPart"
        } else if (digitsOnly.length == 10) {
            val areaCode = digitsOnly.substring(0, 3)
            val firstPart = digitsOnly.substring(3, 6)
            val secondPart = digitsOnly.substring(6)
            return "($areaCode) $firstPart-$secondPart"
        }
        return phone // or return some default formatting
    }
}
