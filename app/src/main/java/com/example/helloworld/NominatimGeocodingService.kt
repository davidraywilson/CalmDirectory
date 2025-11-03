package com.example.helloworld

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NominatimGeocodingService {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        return try {
            val response: NominatimResponse = client.get("https://nominatim.openstreetmap.org/reverse") {
                parameter("format", "json")
                parameter("lat", lat)
                parameter("lon", lon)
            }.body()
            response.displayName
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCoordinates(locationName: String): Pair<Double, Double>? {
        return try {
            val response: List<NominatimSearchResponse> = client.get("https://nominatim.openstreetmap.org/search") {
                parameter("q", locationName)
                parameter("format", "json")
                parameter("limit", 1)
            }.body()
            response.firstOrNull()?.let {
                it.lat.toDouble() to it.lon.toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
data class NominatimResponse(
    @SerialName("display_name")
    val displayName: String
)

@Serializable
data class NominatimSearchResponse(
    val lat: String,
    val lon: String
)
