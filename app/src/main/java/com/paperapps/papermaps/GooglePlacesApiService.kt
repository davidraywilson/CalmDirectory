package com.paperapps.papermaps

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.paperapps.papermaps.data.UserPreferencesRepository
import com.paperapps.papermaps.BuildConfig

@Serializable
data class GooglePlacesNearbyRequest(
    val includedTypes: List<String>,
    val maxResultCount: Int,
    val locationRestriction: LocationRestriction,
    val rankPreference: String
)

@Serializable
data class LocationRestriction(
    val circle: Circle
)

@Serializable
data class Circle(
    val center: Center,
    val radius: Double
)

@Serializable
data class Center(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class GooglePlacesSearchTextRequest(
    val textQuery: String,
    val maxResultCount: Int,
    val locationBias: LocationRestriction,
    val rankPreference: String
)

@Serializable
data class GooglePlacesResponse(
    val places: List<GooglePlace>? = null
)

@Serializable
data class GooglePlace(
    val id: String? = null,
    val displayName: LocalizedText? = null,
    val formattedAddress: String? = null,
    val location: Center? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val nationalPhoneNumber: String? = null,
    val websiteUri: String? = null,
    val regularOpeningHours: OpeningHours? = null,
    val types: List<String>? = null,
    val reviews: List<GoogleReview>? = null
)

@Serializable
data class LocalizedText(
    val text: String
)

@Serializable
data class OpeningHours(
    val weekdayDescriptions: List<String>? = null
)

@Serializable
data class GoogleReview(
    val relativePublishTimeDescription: String? = null,
    val rating: Int? = null,
    val text: LocalizedText? = null,
    val authorAttribution: AuthorAttribution? = null
)

@Serializable
data class AuthorAttribution(
    val displayName: String? = null,
    val uri: String? = null,
    val photoUri: String? = null
)

class GooglePlacesApiService(
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
        val key = BuildConfig.GOOGLE_PLACES_API_KEY
        if (key.isEmpty()) {
            Log.e("GooglePlacesApiService", "GOOGLE PLACES API key not configured")
            return null
        }
        return key
    }

    override suspend fun search(query: String, lat: Double, lon: Double, sortOption: SortOption): List<Poi> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val radiusMiles = userPreferencesRepository.searchRadius.first()
            val radiusMeters = (radiusMiles * 1609).coerceAtMost(50_000).toDouble()
            val trimmedQuery = query.trim()

            val isCategorySearch = trimmedQuery.startsWith("category:")
            
            val includedTypes = if (isCategorySearch) {
                trimmedQuery.removePrefix("category:").split(",").map { it.trim() }
            } else {
                emptyList()
            }

            val rankPreference = when (sortOption) {
                SortOption.DISTANCE -> "DISTANCE"
                else -> "POPULARITY" 
            }
            
            val fieldMask = "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.nationalPhoneNumber,places.websiteUri,places.regularOpeningHours,places.types"

            val response: GooglePlacesResponse = if (isCategorySearch) {
                client.post("https://places.googleapis.com/v1/places:searchNearby") {
                    header("X-Goog-Api-Key", apiKey)
                    header("X-Goog-FieldMask", fieldMask)
                    contentType(ContentType.Application.Json)
                    setBody(
                        GooglePlacesNearbyRequest(
                            includedTypes = includedTypes,
                            maxResultCount = 20,
                            locationRestriction = LocationRestriction(
                                circle = Circle(
                                    center = Center(lat, lon),
                                    radius = radiusMeters
                                )
                            ),
                            rankPreference = rankPreference
                        )
                    )
                }.body()
            } else {
                client.post("https://places.googleapis.com/v1/places:searchText") {
                    header("X-Goog-Api-Key", apiKey)
                    header("X-Goog-FieldMask", fieldMask)
                    contentType(ContentType.Application.Json)
                    setBody(
                        GooglePlacesSearchTextRequest(
                            textQuery = trimmedQuery,
                            maxResultCount = 20,
                            locationBias = LocationRestriction(
                                circle = Circle(
                                    center = Center(lat, lon),
                                    radius = radiusMeters
                                )
                            ),
                            rankPreference = if (sortOption == SortOption.DISTANCE) "DISTANCE" else "RELEVANCE"
                        )
                    )
                }.body()
            }

            val pois = response.places?.map { place ->
                val addressParts = place.formattedAddress?.split(",")?.map { it.trim() } ?: emptyList()
                val street = addressParts.getOrNull(0) ?: ""
                val city = addressParts.getOrNull(1) ?: ""
                val stateZip = addressParts.getOrNull(2) ?: ""
                val state = stateZip.substringBefore(" ").trim()
                val zip = stateZip.substringAfter(" ").trim()
                val country = addressParts.getOrNull(3) ?: "USA"
                
                val address = Address(
                    street = street,
                    city = city,
                    state = state,
                    zip = zip,
                    country = country
                )

                Poi(
                    placeId = place.id,
                    name = place.displayName?.text ?: "Unknown Place",
                    address = address,
                    hours = place.regularOpeningHours?.weekdayDescriptions ?: emptyList(),
                    phone = place.nationalPhoneNumber,
                    description = place.types?.firstOrNull()?.replace("_", " ")?.capitalize() ?: "Point of Interest",
                    website = place.websiteUri,
                    lat = place.location?.latitude,
                    lng = place.location?.longitude,
                    rating = place.rating,
                    userRatingCount = place.userRatingCount,
                    isOutsideSearchRadius = false,
                    isPlace = true
                )
            } ?: emptyList()
            
            when (sortOption) {
                SortOption.RATING -> pois.sortedByDescending { it.rating ?: 0.0 }
                SortOption.NAME_AZ -> pois.sortedBy { it.name }
                SortOption.NAME_ZA -> pois.sortedByDescending { it.name }
                SortOption.DISTANCE, SortOption.POPULARITY -> pois 
            }
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error fetching places: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun autocomplete(query: String): List<String> {
        return emptyList()
    }

    override suspend fun getReviews(placeId: String): List<Review> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val response: GooglePlace = client.get("https://places.googleapis.com/v1/places/$placeId") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", "reviews")
            }.body()

            response.reviews?.map { review ->
                Review(
                    authorName = review.authorAttribution?.displayName ?: "Unknown",
                    rating = review.rating ?: 0,
                    text = review.text?.text ?: "",
                    relativeTime = review.relativePublishTimeDescription ?: ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error fetching reviews: ${e.message}", e)
            emptyList()
        }
    }
}
