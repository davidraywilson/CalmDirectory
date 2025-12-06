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

class HerePlacesApiService(
    private val userPreferencesRepository: UserPreferencesRepository
) : PlacesBackend {

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
            Log.e("HerePlacesApiService", "HERE API key not configured")
            return null
        }
        return key
    }

    override suspend fun search(query: String, lat: Double, lon: Double): List<Poi> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val radiusMiles = userPreferencesRepository.searchRadius.first()
            val radiusMeters = (radiusMiles * 1609).coerceAtMost(50_000)
            val trimmedQuery = query.trim().ifEmpty { "*" }

            val httpStart = System.currentTimeMillis()
            val response: HereDiscoverResponse = client.get("https://discover.search.hereapi.com/v1/discover") {
                parameter("apiKey", apiKey)
                parameter("q", trimmedQuery)

                if (lat != 0.0 || lon != 0.0) {
                    parameter("at", "$lat,$lon")
                    parameter("radius", radiusMeters)
                }

                parameter("limit", 30)
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart

            Log.d(
                "HerePlacesApiService",
                "search query='${trimmedQuery}' lat=$lat lon=$lon radiusMeters=$radiusMeters items=${response.items.size} httpMs=$httpDuration"
            )

            response.items.mapNotNull { item ->
                val title = item.title ?: return@mapNotNull null
                val addr = item.address
                val resolvedCountry = when (val code = addr?.countryCode) {
                    null, "" -> addr?.countryName ?: ""
                    else -> if (code.length == 2) {
                        // Already an ISO 3166-1 alpha-2 code (e.g., "DE"). Use as-is.
                        code
                    } else {
                        // For 3-letter codes like "DEU", prefer the human-readable country name
                        // so that inferRegionCode can map it and the UI shows a friendly label.
                        addr?.countryName ?: code
                    }
                }
                val composedStreet = when {
                    !addr?.houseNumber.isNullOrBlank() && !addr?.street.isNullOrBlank() ->
                        "${addr!!.houseNumber} ${addr.street}"
                    else -> listOfNotNull(addr?.street, addr?.houseNumber)
                        .joinToString(" ")
                }

                val address = Address(
                    street = composedStreet.ifBlank { "" },
                    city = addr?.city ?: "",
                    state = addr?.state ?: "",
                    zip = addr?.postalCode ?: "",
                    country = resolvedCountry
                )

                val hoursList = item.openingHours
                    ?.flatMap { it.text.orEmpty() }
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    ?: emptyList()

                val itemLat = item.position?.lat
                val itemLon = item.position?.lng

                val isOutsideRadius = if ((lat != 0.0 || lon != 0.0) && itemLat != null && itemLon != null) {
                    val distance = distanceMeters(lat, lon, itemLat, itemLon)
                    distance > radiusMeters
                } else {
                    false
                }

                Poi(
                    name = title,
                    address = address,
                    hours = hoursList,
                    phone = item.contacts?.firstOrNull()?.phone?.firstOrNull()?.value,
                    description = item.categories?.joinToString(", ") { it.name ?: "" } ?: "",
                    website = item.contacts?.firstOrNull()?.www?.firstOrNull()?.value,
                    lat = itemLat,
                    lng = itemLon,
                    isOutsideSearchRadius = isOutsideRadius
                )
            }
        } catch (e: Exception) {
            Log.e("HerePlacesApiService", "Error searching HERE places", e)
            emptyList()
        }
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    override suspend fun autocomplete(query: String): List<String> {
        val apiKey = getApiKey() ?: return emptyList()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        return try {
            val response: HereGeocodeResponse = client.get("https://geocode.search.hereapi.com/v1/geocode") {
                parameter("apiKey", apiKey)
                parameter("q", trimmedQuery)
                parameter("limit", 5)
            }.body()

            response.items.mapNotNull { item ->
                item.title ?: item.address?.label
            }
        } catch (e: Exception) {
            Log.e("HerePlacesApiService", "Error getting HERE autocomplete", e)
            emptyList()
        }
    }
}

@Serializable
private data class HereDiscoverResponse(
    val items: List<HerePlaceItem> = emptyList()
)

@Serializable
private data class HerePlaceItem(
    val title: String? = null,
    val address: HereAddress? = null,
    val position: HerePosition? = null,
    val contacts: List<HereContacts>? = null,
    val categories: List<HereCategory>? = null,
    val openingHours: List<HereOpeningHours>? = null
)

@Serializable
private data class HereAddress(
    val label: String? = null,
    val street: String? = null,
    @SerialName("houseNumber") val houseNumber: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("postalCode") val postalCode: String? = null,
    @SerialName("countryName") val countryName: String? = null,
    @SerialName("countryCode") val countryCode: String? = null
)

@Serializable
private data class HerePosition(
    val lat: Double? = null,
    @SerialName("lng") val lng: Double? = null
)

@Serializable
private data class HereContacts(
    val phone: List<HereContactValue>? = null,
    val www: List<HereContactValue>? = null
)

@Serializable
private data class HereContactValue(
    val value: String? = null
)

@Serializable
private data class HereCategory(
    val id: String? = null,
    val name: String? = null
)

@Serializable
private data class HereOpeningHours(
    val text: List<String>? = null,
    val isOpen: Boolean? = null,
    val structured: List<HereOpeningHoursStructured>? = null
)

@Serializable
private data class HereOpeningHoursStructured(
    val start: String? = null,
    val duration: String? = null,
    val recurrence: String? = null
)

@Serializable
private data class HereGeocodeResponse(
    val items: List<HereGeocodeItem> = emptyList()
)

@Serializable
private data class HereGeocodeItem(
    val title: String? = null,
    val address: HereGeocodeAddress? = null
)

@Serializable
private data class HereGeocodeAddress(
    val label: String? = null
)
