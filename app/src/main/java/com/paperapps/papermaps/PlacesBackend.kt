package com.paperapps.papermaps

enum class SortOption {
    DISTANCE,
    RATING,
    POPULARITY,
    NAME_AZ,
    NAME_ZA
}

interface PlacesBackend {
    suspend fun search(query: String, lat: Double, lon: Double, sortOption: SortOption = SortOption.DISTANCE): List<Poi>
    suspend fun autocomplete(query: String): List<String>
    suspend fun getReviews(placeId: String): List<Review>
}
