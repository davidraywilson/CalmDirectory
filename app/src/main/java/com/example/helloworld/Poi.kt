package com.example.helloworld

data class Poi(
    val name: String,
    val address: Address,
    val hours: List<String>,
    val phone: String?,
    val description: String,
    val website: String?
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
    val country: String
)
