package com.example.helloworld

import android.util.Log
import com.example.helloworld.data.UserPreferencesRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleGeocodingService(private val userPreferencesRepository: UserPreferencesRepository) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getCoordinates(address: String): Pair<Double, Double>? {
        val apiKey = userPreferencesRepository.googleApiKey.first()
        if (apiKey.isNullOrEmpty()) {
            return null
        }
        return try {
            val response: GeocodingResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("address", address)
                parameter("key", apiKey)
            }.body()
            response.results.firstOrNull()?.geometry?.location?.let {
                it.lat to it.lng
            }
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting coordinates", e)
            null
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        val apiKey = userPreferencesRepository.googleApiKey.first()
        if (apiKey.isNullOrEmpty()) {
            Log.e("GoogleGeocodingService", "API key is missing")
            return null
        }
        return try {
            val httpResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("latlng", "$lat,$lon")
                parameter("key", apiKey)
            }
            val responseBody = httpResponse.bodyAsText()
            Log.d("GoogleGeocodingService", "Geocoding API response: $responseBody")
            val response: GeocodingResponse = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(responseBody)
            if (response.status != "OK") {
                Log.e("GoogleGeocodingService", "Geocoding API returned status: ${response.status}")
            }
            val address = response.results.firstOrNull()?.formattedAddress
            address?.let { normalizeStreetInAddress(it) }
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting address", e)
            null
        }
    }
}

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>,
    val status: String
)

@Serializable
data class GeocodingResult(
    @SerialName("address_components")
    val addressComponents: List<AddressComponent> = emptyList(),
    @SerialName("formatted_address")
    val formattedAddress: String? = null,
    val geometry: Geometry,
    @SerialName("place_id")
    val placeId: String = "",
    val types: List<String> = emptyList()
)

@Serializable
data class AddressComponent(
    @SerialName("long_name")
    val longName: String = "",
    @SerialName("short_name")
    val shortName: String = "",
    val types: List<String> = emptyList()
)

@Serializable
data class Geometry(
    val location: Location
)

@Serializable
data class Location(
    val lat: Double,
    @SerialName("lng")
    val lng: Double
)