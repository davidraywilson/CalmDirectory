package com.paperapps.papermaps

data class Poi(
    val placeId: String? = null,
    val name: String,
    val address: Address,
    val hours: List<String>,
    val phone: String?,
    val description: String,
    val website: String?,
    val lat: Double?,
    val lng: Double?,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val isOutsideSearchRadius: Boolean = false,
    val isPlace: Boolean = true
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
    val country: String
)

data class Review(
    val authorName: String,
    val rating: Int,
    val text: String,
    val relativeTime: String
)