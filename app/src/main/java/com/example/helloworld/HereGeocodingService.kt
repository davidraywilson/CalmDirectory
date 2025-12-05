package com.example.helloworld

import android.util.Log
import com.calmapps.directory.BuildConfig
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

class HereGeocodingService(
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
        val key = BuildConfig.HERE_API_KEY
        if (key.isEmpty()) {
            Log.e("HereGeocodingService", "HERE API key not configured")
            return null
        }
        return key
    }

    suspend fun getCoordinates(address: String): Pair<Double, Double>? {
        val apiKey = getApiKey() ?: return null

        return try {
            val httpStart = System.currentTimeMillis()
            val response: HereGeoSearchResponse = client.get("https://geocode.search.hereapi.com/v1/geocode") {
                parameter("apiKey", apiKey)
                parameter("q", address)
                parameter("limit", 1)
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart
            Log.d("HereGeocodingService", "getCoordinates HTTP call took ${httpDuration} ms")

            val item = response.items.firstOrNull() ?: return null
            val position = item.position ?: return null
            val lat = position.lat ?: return null
            val lng = position.lng ?: return null
            lat to lng
        } catch (e: Exception) {
            Log.e("HereGeocodingService", "Error getting coordinates", e)
            null
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        val apiKey = getApiKey() ?: return null

        return try {
            val httpStart = System.currentTimeMillis()
            val response: HereRevGeoResponse = client.get("https://revgeocode.search.hereapi.com/v1/revgeocode") {
                parameter("apiKey", apiKey)
                parameter("at", "$lat,$lon")
                parameter("limit", 1)
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart
            Log.d("HereGeocodingService", "getAddress HTTP call took ${httpDuration} ms")

            val label = response.items.firstOrNull()?.address?.label
            label?.let { normalizeStreetInAddress(it) }
        } catch (e: Exception) {
            Log.e("HereGeocodingService", "Error getting address", e)
            null
        }
    }
}

@Serializable
private data class HereGeoSearchResponse(
    val items: List<HereGeoItem> = emptyList()
)

@Serializable
private data class HereGeoItem(
    val position: HereGeoPosition? = null
)

@Serializable
private data class HereGeoPosition(
    val lat: Double? = null,
    @SerialName("lng") val lng: Double? = null
)

@Serializable
private data class HereRevGeoResponse(
    val items: List<HereRevGeoItem> = emptyList()
)

@Serializable
private data class HereRevGeoItem(
    val address: HereRevGeoAddress? = null
)

@Serializable
private data class HereRevGeoAddress(
    val label: String? = null
)
