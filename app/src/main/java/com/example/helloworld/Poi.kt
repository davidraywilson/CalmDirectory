package com.example.helloworld

data class Poi(
    val name: String,
    val address: Address,
    val hours: List<String>,
    val phone: String?,
    val description: String,
    val website: String?,
    val lat: Double?,
    val lng: Double?,
    val isPlace: Boolean = true,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val priceLevel: Int? = null,
    val distanceMeters: Double? = null,
    val summary: String? = null,
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
    val country: String
)