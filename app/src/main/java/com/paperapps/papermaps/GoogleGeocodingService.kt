package com.paperapps.papermaps

import android.util.Log
import com.paperapps.papermaps.BuildConfig
import com.paperapps.papermaps.data.UserPreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleGeocodingService(
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    private fun getApiKey(): String? {
        val key = BuildConfig.GOOGLE_PLACES_API_KEY
        if (key.isEmpty()) {
            Log.e("GoogleGeocodingService", "GOOGLE API key not configured")
            return null
        }
        return key
    }

    suspend fun getCoordinates(address: String): Pair<Double, Double>? {
        val apiKey = getApiKey() ?: return null

        return try {
            val response: GoogleGeocodeResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("key", apiKey)
                parameter("address", address)
            }.body()

            val location = response.results.firstOrNull()?.geometry?.location ?: return null
            location.lat to location.lng
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting coordinates", e)
            null
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        val apiKey = getApiKey() ?: return null

        return try {
            val response: GoogleGeocodeResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("key", apiKey)
                parameter("latlng", "$lat,$lon")
            }.body()

            val addressStr = response.results.firstOrNull()?.formatted_address
            addressStr?.let { normalizeStreetInAddress(it) }
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting address", e)
            null
        }
    }

    suspend fun searchAddresses(query: String, limit: Int = 5): List<Poi> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val response: GoogleGeocodeResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("key", apiKey)
                parameter("address", query)
            }.body()

            response.results.take(limit).mapNotNull { result ->
                val location = result.geometry?.location ?: return@mapNotNull null
                val lat = location.lat
                val lng = location.lng
                
                var streetNumber = ""
                var route = ""
                var city = ""
                var state = ""
                var zip = ""
                var country = "USA"

                result.address_components?.forEach { component ->
                    when {
                        component.types.contains("street_number") -> streetNumber = component.long_name
                        component.types.contains("route") -> route = component.long_name
                        component.types.contains("locality") -> city = component.long_name
                        component.types.contains("administrative_area_level_1") -> state = component.short_name
                        component.types.contains("postal_code") -> zip = component.long_name
                        component.types.contains("country") -> country = component.short_name
                    }
                }

                val streetName = listOfNotNull(streetNumber.ifEmpty { null }, route.ifEmpty { null }).joinToString(" ").trim()
                
                val poiAddress = Address(
                    street = streetName.ifEmpty { result.formatted_address?.substringBefore(",") ?: "" },
                    city = city,
                    state = state,
                    zip = zip,
                    country = country
                )

                Poi(
                    name = result.formatted_address?.substringBefore(",") ?: "Unknown Location",
                    address = poiAddress,
                    hours = emptyList(),
                    phone = null,
                    website = null,
                    lat = lat,
                    lng = lng,
                    isPlace = false,
                    description = "Address"
                )
            }
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error searching addresses", e)
            emptyList()
        }
    }
}

@Serializable
private data class GoogleGeocodeResponse(
    val results: List<GoogleGeocodeResult> = emptyList(),
    val status: String
)

@Serializable
private data class GoogleGeocodeResult(
    val address_components: List<GoogleAddressComponent>? = null,
    val formatted_address: String? = null,
    val geometry: GoogleGeometry? = null
)

@Serializable
private data class GoogleAddressComponent(
    val long_name: String,
    val short_name: String,
    val types: List<String>
)

@Serializable
private data class GoogleGeometry(
    val location: GoogleLocation? = null
)

@Serializable
private data class GoogleLocation(
    val lat: Double,
    val lng: Double
)
