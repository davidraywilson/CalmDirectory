package com.example.helloworld

import android.content.Context
import android.util.Log
import com.calmapps.calmmaps.BuildConfig
import com.example.helloworld.data.SortMode
import com.example.helloworld.data.UserPreferencesRepository
import com.example.helloworld.util.TimePeriod
import com.example.helloworld.util.haversineMeters
import com.example.helloworld.util.isOpenThroughout
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import kotlin.math.cos
import com.google.android.libraries.places.api.model.DayOfWeek as PlacesDayOfWeek
import java.time.DayOfWeek as JavaDayOfWeek

private const val MAX_SEARCH_RADIUS_MILES = 20.0
private const val DISTANCE_AT_MAX_RADIUS_MILES = 100.0
private const val METERS_PER_MILE = 1609.34

interface PlacesBackend {
    suspend fun search(query: String, lat: Double, lon: Double): List<Poi>
    suspend fun autocomplete(query: String): List<String>
}

class GooglePlacesApiService(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) : PlacesBackend {

    private var placesClient: PlacesClient? = null
    private var token: AutocompleteSessionToken? = null

    private fun ensureClient() {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        check(apiKey.isNotEmpty()) {
            "GOOGLE_PLACES_API_KEY is empty. Set it in .env and rebuild."
        }
        if (!Places.isInitialized()) {
            Places.initialize(context.applicationContext, apiKey)
        }
        if (placesClient == null) placesClient = Places.createClient(context)
        if (token == null) token = AutocompleteSessionToken.newInstance()
    }

    override suspend fun search(query: String, lat: Double, lon: Double): List<Poi> {
        ensureClient()
        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.OPENING_HOURS,
            Place.Field.TYPES,
            Place.Field.WEBSITE_URI,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.PRICE_LEVEL,
            Place.Field.EDITORIAL_SUMMARY,
        )
        val builder = SearchByTextRequest.builder(query, placeFields)

        val openNow = userPreferencesRepository.openNow.first()
        val openIn1Hour = userPreferencesRepository.openIn1Hour.first()
        val sortMode = userPreferencesRepository.sortMode.first()
        val maxPrice = userPreferencesRepository.maxPriceLevel.first()
        val radiusMiles = userPreferencesRepository.searchRadius.first().toDouble()

        if (openNow) builder.isOpenNow = true
        builder.setRankPreference(
            when (sortMode) {
                SortMode.RELEVANCE -> SearchByTextRequest.RankPreference.RELEVANCE
                SortMode.DISTANCE -> SearchByTextRequest.RankPreference.DISTANCE
                SortMode.RATING -> SearchByTextRequest.RankPreference.RELEVANCE
            }
        )
        if (lat != 0.0 || lon != 0.0) {
            val sliderMaxed = radiusMiles >= MAX_SEARCH_RADIUS_MILES
            // Places SDK 3.5 only accepts RectangularBounds for setLocationRestriction —
            // CircularBounds throws IllegalArgumentException there. We use Rectangular for
            // restriction and Circular for bias.
            val restrictionMiles = when {
                sortMode == SortMode.DISTANCE && sliderMaxed -> DISTANCE_AT_MAX_RADIUS_MILES
                else -> radiusMiles
            }
            val latDeg = restrictionMiles / 69.0
            val lonDeg = restrictionMiles / (cos(Math.toRadians(lat)) * 69.0)
            val rectBounds = RectangularBounds.newInstance(
                LatLng(lat - latDeg, lon - lonDeg),
                LatLng(lat + latDeg, lon + lonDeg),
            )
            when {
                sortMode == SortMode.DISTANCE -> {
                    // DISTANCE rank requires setLocationRestriction. At slider max we
                    // expand to DISTANCE_AT_MAX_RADIUS_MILES so distant matches still
                    // surface — the 20mi cap is a UI ceiling, not user intent.
                    builder.setLocationRestriction(rectBounds)
                }
                sliderMaxed -> {
                    // At slider max for relevance/rating, prefer-not-restrict so the user
                    // isn't walled off from rare distant matches.
                    builder.setLocationBias(
                        CircularBounds.newInstance(LatLng(lat, lon), radiusMiles * METERS_PER_MILE)
                    )
                }
                else -> {
                    builder.setLocationRestriction(rectBounds)
                }
            }
        }

        return try {
            val response = placesClient!!.searchByText(builder.build()).await()
            val now = LocalDateTime.now()
            val target = now.plusHours(1)
            response.places
                .let { if (openIn1Hour) it.filter { p -> passesOpenIn1Hour(p, now, target) } else it }
                .let { if (maxPrice != null) it.filter { p -> (p.priceLevel ?: 0) <= maxPrice } else it }
                .map { place -> place.toPoi(userLat = lat, userLon = lon) }
                .let {
                    when (sortMode) {
                        // Client-side sort guarantees the requested order regardless of
                        // whether the SDK honors RankPreference (it doesn't always —
                        // particularly DISTANCE with rectangular restriction).
                        SortMode.DISTANCE -> it.sortedBy { p -> p.distanceMeters ?: Double.MAX_VALUE }
                        SortMode.RATING -> it.sortedByDescending { p -> p.rating ?: -1.0 }
                        SortMode.RELEVANCE -> it
                    }
                }
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error searching for places", e)
            emptyList()
        }
    }

    override suspend fun autocomplete(query: String): List<String> {
        ensureClient()
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

    private fun passesOpenIn1Hour(place: Place, now: LocalDateTime, target: LocalDateTime): Boolean {
        val periods = place.openingHours?.periods?.map { p ->
            TimePeriod(
                openDay = p.open?.day?.toJava() ?: JavaDayOfWeek.MONDAY,
                openHour = p.open?.time?.hours ?: 0,
                openMinute = p.open?.time?.minutes ?: 0,
                closeDay = p.close?.day?.toJava(),
                closeHour = p.close?.time?.hours ?: 0,
                closeMinute = p.close?.time?.minutes ?: 0,
            )
        }
        return isOpenThroughout(periods, now, target)
    }

    private fun PlacesDayOfWeek.toJava(): JavaDayOfWeek = when (this) {
        PlacesDayOfWeek.MONDAY -> JavaDayOfWeek.MONDAY
        PlacesDayOfWeek.TUESDAY -> JavaDayOfWeek.TUESDAY
        PlacesDayOfWeek.WEDNESDAY -> JavaDayOfWeek.WEDNESDAY
        PlacesDayOfWeek.THURSDAY -> JavaDayOfWeek.THURSDAY
        PlacesDayOfWeek.FRIDAY -> JavaDayOfWeek.FRIDAY
        PlacesDayOfWeek.SATURDAY -> JavaDayOfWeek.SATURDAY
        PlacesDayOfWeek.SUNDAY -> JavaDayOfWeek.SUNDAY
    }

    private fun Place.toPoi(userLat: Double, userLon: Double): Poi {
        val placeLat = latLng?.latitude
        val placeLng = latLng?.longitude
        val distance = if (placeLat != null && placeLng != null && (userLat != 0.0 || userLon != 0.0))
            haversineMeters(userLat, userLon, placeLat, placeLng) else null
        val priceLevelInt = priceLevel
        return Poi(
            name = name ?: "N/A",
            address = parseAddress(address),
            hours = openingHours?.weekdayText ?: emptyList(),
            phone = phoneNumber?.let { formatPhoneNumber(it) },
            description = placeTypes?.joinToString(", ") ?: "N/A",
            website = websiteUri?.toString(),
            lat = placeLat,
            lng = placeLng,
            rating = rating,
            userRatingCount = userRatingsTotal,
            priceLevel = priceLevelInt,
            distanceMeters = distance,
            summary = editorialSummary,
        )
    }

    private fun parseAddress(addressString: String?): Address {
        if (addressString == null) return Address("N/A", "", "", "", "")
        val parts = addressString.split(",").map { it.trim() }
        val street = parts.getOrElse(0) { addressString }
        val city = parts.getOrElse(1) { "" }
        val country = if (parts.size > 2 && parts.last().matches(Regex("[A-Za-z ]+"))) parts.last() else ""
        var state = ""
        var zip = ""
        if (parts.size > 2) {
            val stateZip = if (country.isNotEmpty()) parts[parts.size - 2] else parts.last()
            val tokens = stateZip.split(" ")
            zip = tokens.lastOrNull { it.any(Char::isDigit) } ?: ""
            state = tokens.filter { it != zip }.joinToString(" ")
        }
        return Address(street, city, state, zip, country)
    }

    private fun formatPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("1") ->
                "(${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            digits.length == 10 ->
                "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            else -> phone
        }
    }
}
