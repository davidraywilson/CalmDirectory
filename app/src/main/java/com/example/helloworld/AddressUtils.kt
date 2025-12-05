package com.example.helloworld

/**
 * Normalizes street addresses so house numbers come first.
 * Handles addresses like "Main St 123" → "123 Main St".
 */
fun normalizeStreetInAddress(address: String): String {
    // Split by comma to get segments
    val segments = address.split(",").map { it.trim() }
    if (segments.isEmpty()) return address

    // Only normalize the first segment (the street)
    val normalizedStreet = normalizeStreet(segments[0])
    
    // Reconstruct the full address with the normalized street
    return listOf(normalizedStreet).plus(segments.drop(1)).joinToString(", ")
}

/**
 * Normalizes a street segment by moving trailing numeric house numbers to the front.
 * "Main St 123" → "123 Main St"
 * "Main St" → "Main St" (unchanged)
 */
fun normalizeStreet(street: String): String {
    val tokens = street.split(" ").filter { it.isNotBlank() }
    if (tokens.isEmpty()) return street

    val last = tokens.last()
    val isNumeric = last.all { it.isDigit() }

    return if (isNumeric && tokens.size > 1) {
        val namePart = tokens.dropLast(1).joinToString(" ")
        "$last $namePart"
    } else {
        street
    }
}