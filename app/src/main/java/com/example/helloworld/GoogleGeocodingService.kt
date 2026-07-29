package com.example.helloworld

import android.util.Log
import com.calmapps.calmmaps.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleGeocodingService {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun getCoordinates(address: String): Pair<Double, Double>? {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isEmpty()) return null
        return try {
            val response: GeocodingResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("address", address)
                parameter("key", apiKey)
            }.body()
            response.results.firstOrNull()?.geometry?.location?.let { it.lat to it.lng }
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting coordinates", e)
            null
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isEmpty()) {
            Log.e("GoogleGeocodingService", "API key is missing")
            return null
        }
        return try {
            val body = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("latlng", "$lat,$lon")
                parameter("key", apiKey)
            }.bodyAsText()
            val response: GeocodingResponse = Json {
                ignoreUnknownKeys = true; isLenient = true
            }.decodeFromString(body)
            response.results.firstOrNull()?.formattedAddress
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting address", e)
            null
        }
    }
}

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>, val status: String = "")

@Serializable
data class GeocodingResult(
    @SerialName("address_components") val addressComponents: List<AddressComponent> = emptyList(),
    @SerialName("formatted_address") val formattedAddress: String? = null,
    val geometry: Geometry,
    @SerialName("place_id") val placeId: String = "",
    val types: List<String> = emptyList(),
)

@Serializable
data class AddressComponent(
    @SerialName("long_name") val longName: String = "",
    @SerialName("short_name") val shortName: String = "",
    val types: List<String> = emptyList(),
)

@Serializable data class Geometry(val location: GLocation)

@Serializable data class GLocation(val lat: Double, @SerialName("lng") val lng: Double)
