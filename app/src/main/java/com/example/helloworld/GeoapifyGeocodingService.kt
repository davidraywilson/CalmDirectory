package com.example.helloworld

import android.util.Log
import com.example.helloworld.data.UserPreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GeoapifyGeocodingService(
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
        val key = BuildConfig.GEOAPIFY_API_KEY
        if (key.isNullOrEmpty()) {
            Log.e("GeoapifyGeocoding", "Geoapify API key not configured")
            return null
        }
        return key
    }

    suspend fun getCoordinates(address: String): Pair<Double, Double>? {
        val apiKey = getApiKey() ?: return null

        return try {
            val httpStart = System.currentTimeMillis()
            val response: GeoapifyGeoSearchResponse =
                client.get("https://api.geoapify.com/v1/geocode/search") {
                    parameter("apiKey", apiKey)
                    parameter("text", address)
                    parameter("limit", 1)
                }.body()
            val httpDuration = System.currentTimeMillis() - httpStart
            Log.d("GeoapifyGeocoding", "getCoordinates HTTP call took ${httpDuration} ms")

            val feature = response.features.firstOrNull() ?: return null
            val coords = feature.geometry.coordinates
            if (coords.size >= 2) coords[1] to coords[0] else null
        } catch (e: Exception) {
            Log.e("GeoapifyGeocoding", "Error getting coordinates", e)
            null
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        val apiKey = getApiKey() ?: return null

        return try {
            val httpStart = System.currentTimeMillis()
            val response: GeoapifyGeoSearchResponse =
                client.get("https://api.geoapify.com/v1/geocode/reverse") {
                    parameter("apiKey", apiKey)
                    parameter("lat", lat)
                    parameter("lon", lon)
                    parameter("limit", 1)
                }.body()
            val httpDuration = System.currentTimeMillis() - httpStart
            Log.d("GeoapifyGeocoding", "getAddress HTTP call took ${httpDuration} ms")

            response.features.firstOrNull()?.properties?.formatted
        } catch (e: Exception) {
            Log.e("GeoapifyGeocoding", "Error getting address", e)
            null
        }
    }
}

@Serializable
private data class GeoapifyGeoSearchResponse(
    val features: List<GeoapifyGeoFeature> = emptyList()
)

@Serializable
private data class GeoapifyGeoFeature(
    val properties: GeoapifyGeoProperties,
    val geometry: GeoapifyGeoGeometry
)

@Serializable
private data class GeoapifyGeoProperties(
    val formatted: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    @SerialName("postcode") val postcode: String? = null
)

@Serializable
private data class GeoapifyGeoGeometry(
    val coordinates: List<Double>
)