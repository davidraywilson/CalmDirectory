package com.example.helloworld

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import java.util.MissingResourceException

/**
 * Format a raw phone number string for display using the country where the POI is located.
 *
 * - Uses libphonenumber for robust international formatting.
 * - Returns NATIONAL format (no leading +country-code).
 * - Falls back to the raw value if parsing or validation fails.
 */
fun formatPhoneNumberForCountry(rawPhone: String?, country: String?): String {
    // Clean up common URI-style prefixes like "tel:+49..." which HERE and
    // other providers sometimes return. libphonenumber expects just the
    // number, so strip the scheme before parsing.
    val cleaned = rawPhone?.trim().orEmpty()
    val phone = if (cleaned.lowercase(Locale.US).startsWith("tel:")) {
        cleaned.substring(4).trim()
    } else {
        cleaned
    }
    if (phone.isEmpty()) return ""

    val util = PhoneNumberUtil.getInstance()
    val region = inferRegionCode(country)

    return try {
        val toParse = normalizePhoneForRegion(phone, region, util)
        val parsed = util.parse(toParse, region)
        if (!util.isValidNumber(parsed)) {
            phone
        } else {
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        }
    } catch (e: NumberParseException) {
        phone
    }
}

/**
 * Format a raw phone number string into a dialable, international form.
 *
 * - Uses libphonenumber and returns E164 format (e.g., "+494036810").
 * - This is what should be passed to the tel: URI when launching the dialer.
 * - Falls back to the raw value if parsing or validation fails.
 */
fun formatPhoneNumberForDial(rawPhone: String?, country: String?): String {
    // As above, strip URI schemes like "tel:" before feeding to
    // libphonenumber so that we get a clean, dialable output.
    val cleaned = rawPhone?.trim().orEmpty()
    val phone = if (cleaned.lowercase(Locale.US).startsWith("tel:")) {
        cleaned.substring(4).trim()
    } else {
        cleaned
    }
    if (phone.isEmpty()) return ""

    val util = PhoneNumberUtil.getInstance()
    val region = inferRegionCode(country)

    return try {
        val toParse = normalizePhoneForRegion(phone, region, util)
        val parsed = util.parse(toParse, region)
        if (!util.isValidNumber(parsed)) {
            phone
        } else {
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        }
    } catch (e: NumberParseException) {
        phone
    }
}

private fun normalizePhoneForRegion(phone: String, region: String, util: PhoneNumberUtil): String {
    val trimmed = phone.trim()
    if (trimmed.startsWith("+") || trimmed.startsWith("00")) return trimmed

    val digits = trimmed.filter { it.isDigit() }
    // If it's too short, let libphonenumber decide; don't try to interpret as full international.
    if (digits.length < 8) return trimmed

    val countryCode = util.getCountryCodeForRegion(region)
    if (countryCode > 0) {
        val cc = countryCode.toString()
        if (digits.startsWith(cc)) {
            // Looks like an international-format number missing a leading '+'.
            return "+$digits"
        }
    }

    return trimmed
}

/**
 * Attempt to infer a reasonable ISO 3166-1 alpha-2 region code from a country
 * name or code string.
 */
fun inferRegionCode(country: String?): String {
    if (country.isNullOrBlank()) {
        return Locale.getDefault().country.ifBlank { "US" }
    }

    val trimmed = country.trim()

    // If it's already a 2-letter code, assume it's an ISO country code.
    if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
        return trimmed.uppercase(Locale.US)
    }

    // If it's a 3-letter ISO 3166-1 alpha-3 code (e.g., "DEU", "USA"),
    // try to map it to an alpha-2 code using the JDK Locale data.
    if (trimmed.length == 3 && trimmed.all { it.isLetter() }) {
        val upper = trimmed.uppercase(Locale.US)
        val iso2FromIso3 = Locale.getISOCountries().firstOrNull { iso2 ->
            try {
                Locale("", iso2).getISO3Country().equals(upper, ignoreCase = true)
            } catch (_: MissingResourceException) {
                false
            }
        }
        if (iso2FromIso3 != null) {
            return iso2FromIso3
        }
    }

    val normalized = trimmed.lowercase(Locale.US)

    val commonNameToCode = mapOf(
        "united states" to "US",
        "usa" to "US",
        "us" to "US",
        "canada" to "CA",
        "united kingdom" to "GB",
        "uk" to "GB",
        "germany" to "DE",
        "deutschland" to "DE"
    )

    commonNameToCode[normalized]?.let { return it }

    // Try matching full display country names to ISO codes.
    val isoMatch = Locale.getISOCountries().firstOrNull { iso ->
        Locale("", iso).displayCountry.lowercase(Locale.US) == normalized
    }

    return isoMatch ?: "US"
}
