# Google Places Reincorporation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace HERE Places with Google Places as the sole search backend, sourcing the API key from `.env` at build time. Add Google-only result enrichment (rating + price) and filters (open-now, open-in-1-hour, sort by relevance/distance, max-price chip, show-rating toggle). Mapbox Navigation handoff is unchanged.

**Architecture:** Single backend `GooglePlacesApiService` implementing a `PlacesBackend` interface (re-introduced from `/StudioProjects/GOOGLE_PLACES_API_INTEGRATION.md`). API key flows from `.env` → `app/build.gradle` `buildConfigField` → `BuildConfig.GOOGLE_PLACES_API_KEY`. Open-now is server-side via `isOpenNow=true`; open-in-1-hour is client-side using `place.openingHours.periods`. Distance is computed via Haversine and stored per-`Poi`. Settings UI gains a "Search filters" section with five controls. Result rows render stars (Material `Star`/`StarHalf`/`StarBorder`), price `$$`, and distance.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Mapbox SDK (untouched), Google Places SDK 3.5.0, Jetpack DataStore Preferences, JUnit 4.

**Testing scope:** This codebase has no existing test source set. JVM unit tests are added only for the two pure-logic helpers where TDD pays off (rating-rounding/star-decomposition + open-in-1-hour period checker). Compose UI changes are validated by manual smoke. ViewModel/SDK-coupled service code is exercised end-to-end by the smoke build.

---

## Task 1: Build config — Places SDK dependency, .env-sourced API key, drop HERE

**Files:**
- Modify: `app/build.gradle`

- [ ] **Step 1: Add Places SDK dep, replace HERE_API_KEY buildConfigField with GOOGLE_PLACES_API_KEY**

In `app/build.gradle`, in `defaultConfig`, replace the HERE line:

```groovy
buildConfigField "String", "HERE_API_KEY", "\"${localProperties.getProperty('HERE_API_KEY', '')}\""
```

with:

```groovy
buildConfigField "String", "GOOGLE_PLACES_API_KEY", "\"${envProperties.getProperty('GOOGLE_PLACES_API_KEY', '')}\""
```

(`envProperties` is already loaded at the top of `app/build.gradle` from a prior change.)

In `dependencies`, anywhere in the block, add:

```groovy
implementation 'com.google.android.libraries.places:places:3.5.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1'
```

(`kotlinx-coroutines-play-services` is required for `Task<T>.await()` on the SDK's `searchByText`/`findAutocompletePredictions`.)

- [ ] **Step 2: Verify the build evaluates**

Run: `./gradlew :app:help --offline -q`
Expected: exits 0, no Groovy syntax error.
(If the wrapper isn't present, `gradle :app:help -q` from the project root works.)

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "build: swap HERE_API_KEY for GOOGLE_PLACES_API_KEY, add Places SDK"
```

---

## Task 2: Pure helpers + JVM unit tests

Sets up `app/src/test/` with a single test class covering the rating math and open-in-1-hour period checker. These two functions are the highest-value targets: small, pure, with edge cases (rounding boundaries, midnight-crossing periods, 24/7 places).

**Files:**
- Create: `app/src/main/java/com/example/helloworld/util/SearchHelpers.kt`
- Create: `app/src/test/java/com/example/helloworld/util/SearchHelpersTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/helloworld/util/SearchHelpersTest.kt`:

```kotlin
package com.example.helloworld.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class SearchHelpersTest {

    // ---- decomposeStars ----

    @Test fun decomposeStars_exactInteger() =
        assertEquals(StarBreakdown(full = 4, half = false, empty = 1), decomposeStars(4.0))

    @Test fun decomposeStars_roundsDown() =
        assertEquals(StarBreakdown(full = 4, half = false, empty = 1), decomposeStars(4.24))

    @Test fun decomposeStars_roundsToHalf() =
        assertEquals(StarBreakdown(full = 4, half = true, empty = 0), decomposeStars(4.25))

    @Test fun decomposeStars_roundsToHalfHigh() =
        assertEquals(StarBreakdown(full = 4, half = true, empty = 0), decomposeStars(4.74))

    @Test fun decomposeStars_roundsUp() =
        assertEquals(StarBreakdown(full = 5, half = false, empty = 0), decomposeStars(4.75))

    @Test fun decomposeStars_clampsLow() =
        assertEquals(StarBreakdown(full = 0, half = false, empty = 5), decomposeStars(-1.0))

    @Test fun decomposeStars_clampsHigh() =
        assertEquals(StarBreakdown(full = 5, half = false, empty = 0), decomposeStars(7.0))

    // ---- isOpenThroughout ----

    private fun mondayAt(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 4, 27, hour, minute) // 2026-04-27 is a Monday

    @Test fun isOpenThroughout_nullPeriodsLenient() =
        assertTrue(isOpenThroughout(null, mondayAt(10), mondayAt(11)))

    @Test fun isOpenThroughout_currentlyOpenAndStaysOpen() {
        // Mon 09:00 → Mon 17:00
        val periods = listOf(period(DayOfWeek.MONDAY, 9, 0, DayOfWeek.MONDAY, 17, 0))
        assertTrue(isOpenThroughout(periods, mondayAt(10), mondayAt(11)))
    }

    @Test fun isOpenThroughout_closesBeforeTarget() {
        // Mon 09:00 → Mon 10:30; target is 11:00
        val periods = listOf(period(DayOfWeek.MONDAY, 9, 0, DayOfWeek.MONDAY, 10, 30))
        assertFalse(isOpenThroughout(periods, mondayAt(10), mondayAt(11)))
    }

    @Test fun isOpenThroughout_currentlyClosed() {
        // Mon 14:00 → Mon 17:00; current is 10:00
        val periods = listOf(period(DayOfWeek.MONDAY, 14, 0, DayOfWeek.MONDAY, 17, 0))
        assertFalse(isOpenThroughout(periods, mondayAt(10), mondayAt(11)))
    }

    @Test fun isOpenThroughout_periodCrossesMidnight() {
        // Mon 22:00 → Tue 02:00; now Mon 23:30, target Tue 00:30
        val periods = listOf(period(DayOfWeek.MONDAY, 22, 0, DayOfWeek.TUESDAY, 2, 0))
        assertTrue(isOpenThroughout(periods, mondayAt(23, 30), mondayAt(23, 30).plusHours(1)))
    }

    @Test fun isOpenThroughout_twentyFourSeven() {
        // Single period with no close time → 24/7
        val periods = listOf(period(DayOfWeek.MONDAY, 0, 0, null))
        assertTrue(isOpenThroughout(periods, mondayAt(3), mondayAt(4)))
    }

    @Test fun isOpenThroughout_disjointPeriods_inGap() {
        // Lunch 11–14, dinner 17–22; current 15:00 → in gap
        val periods = listOf(
            period(DayOfWeek.MONDAY, 11, 0, DayOfWeek.MONDAY, 14, 0),
            period(DayOfWeek.MONDAY, 17, 0, DayOfWeek.MONDAY, 22, 0),
        )
        assertFalse(isOpenThroughout(periods, mondayAt(15), mondayAt(16)))
    }

    private fun period(
        openDay: DayOfWeek, openHour: Int, openMinute: Int,
        closeDay: DayOfWeek?, closeHour: Int = 0, closeMinute: Int = 0,
    ): TimePeriod = TimePeriod(
        openDay = openDay, openHour = openHour, openMinute = openMinute,
        closeDay = closeDay, closeHour = closeHour, closeMinute = closeMinute,
    )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.helloworld.util.SearchHelpersTest`
Expected: FAIL — `SearchHelpers` and its types are not defined.

- [ ] **Step 3: Write the helper module**

Create `app/src/main/java/com/example/helloworld/util/SearchHelpers.kt`:

```kotlin
package com.example.helloworld.util

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class StarBreakdown(val full: Int, val half: Boolean, val empty: Int) {
    init { require(full + (if (half) 1 else 0) + empty == 5) }
}

fun decomposeStars(rating: Double): StarBreakdown {
    val clamped = rating.coerceIn(0.0, 5.0)
    val halves = (clamped * 2.0).roundToInt()      // 0..10
    val full = halves / 2
    val half = halves % 2 == 1
    val empty = 5 - full - if (half) 1 else 0
    return StarBreakdown(full, half, empty)
}

/** Mapbox-SDK-agnostic value object so [isOpenThroughout] can be unit tested without the SDK. */
data class TimePeriod(
    val openDay: DayOfWeek,
    val openHour: Int,
    val openMinute: Int,
    val closeDay: DayOfWeek?,    // null → 24/7 (no close time)
    val closeHour: Int,
    val closeMinute: Int,
)

/**
 * True if the place is open at [now] and remains open through [target].
 * Lenient: returns true when [periods] is null (caller should treat unknown hours as "include").
 */
fun isOpenThroughout(periods: List<TimePeriod>?, now: LocalDateTime, target: LocalDateTime): Boolean {
    if (periods == null) return true
    if (periods.size == 1 && periods[0].closeDay == null) return true  // 24/7

    val ref = now.toLocalDate()
    for (p in periods) {
        if (p.closeDay == null) continue
        val openAt = onSameWeekAs(ref, p.openDay, p.openHour, p.openMinute)
        var closeAt = onSameWeekAs(ref, p.closeDay, p.closeHour, p.closeMinute)
        if (!closeAt.isAfter(openAt)) closeAt = closeAt.plusDays(7)
        // Try the canonical instance and a +7d shift for periods straddling the reference week boundary.
        for (shift in listOf(0L, -7L, 7L)) {
            val o = openAt.plusDays(shift)
            val c = closeAt.plusDays(shift)
            if (!now.isBefore(o) && now.isBefore(c) && !target.isAfter(c)) return true
        }
    }
    return false
}

private fun onSameWeekAs(refDate: java.time.LocalDate, day: DayOfWeek, hour: Int, minute: Int): LocalDateTime {
    val refMonday = refDate.minusDays((refDate.dayOfWeek.value - 1).toLong())
    val targetDate = refMonday.plusDays((day.value - 1).toLong())
    return LocalDateTime.of(targetDate, java.time.LocalTime.of(hour, minute))
}

/** Haversine distance in meters between two lat/lon points. */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

/** Format meters as "0.4 mi" (<10mi, one decimal) or "12 mi" (≥10mi). */
fun formatMiles(meters: Double): String {
    val miles = meters / 1609.344
    return if (miles < 10.0) "%.1f mi".format(miles) else "${miles.roundToInt()} mi"
}

/** Format meters as "0.4 km" (<10km, one decimal) or "12 km". */
fun formatKilometers(meters: Double): String {
    val km = meters / 1000.0
    return if (km < 10.0) "%.1f km".format(km) else "${km.roundToInt()} km"
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.helloworld.util.SearchHelpersTest`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/helloworld/util/SearchHelpers.kt app/src/test/java/com/example/helloworld/util/SearchHelpersTest.kt
git commit -m "feat(search): add star/distance/openIn1Hour pure helpers with unit tests"
```

---

## Task 3: Extend Poi with rating, price, distance

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/Poi.kt`

- [ ] **Step 1: Add fields to Poi**

Replace the contents of `Poi.kt` with:

```kotlin
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
    val isOutsideSearchRadius: Boolean = false,
    val isPlace: Boolean = true,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val priceLevel: Int? = null,
    val distanceMeters: Double? = null,
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
    val country: String,
)
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS (existing call sites still work — new fields have defaults).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/helloworld/Poi.kt
git commit -m "feat(poi): add rating, userRatingCount, priceLevel, distanceMeters fields"
```

---

## Task 4: Preferences — SortMode enum, new flows, mutex setters

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/data/UserPreferencesRepository.kt`

- [ ] **Step 1: Add enum, flows, keys, and setters**

Edit the file. Add after `enum class DistanceUnit { ... }`:

```kotlin
enum class SortMode {
    RELEVANCE,
    DISTANCE,
}
```

Inside `class UserPreferencesRepository`, after the existing `distanceUnit` flow block, add:

```kotlin
    val openNow: Flow<Boolean> = context.dataStore.data
        .map { it[OPEN_NOW] ?: false }

    val openIn1Hour: Flow<Boolean> = context.dataStore.data
        .map { it[OPEN_IN_1_HOUR] ?: false }

    val sortMode: Flow<SortMode> = context.dataStore.data
        .map { prefs ->
            val stored = prefs[SORT_MODE]
            try { SortMode.valueOf(stored ?: SortMode.RELEVANCE.name) }
            catch (_: IllegalArgumentException) { SortMode.RELEVANCE }
        }

    val showRating: Flow<Boolean> = context.dataStore.data
        .map { it[SHOW_RATING] ?: true }

    /** null means "Any" — no constraint sent to the API. Otherwise 0..4. */
    val maxPriceLevel: Flow<Int?> = context.dataStore.data
        .map { it[MAX_PRICE_LEVEL] }
```

After `saveDistanceUnit`, add:

```kotlin
    suspend fun saveOpenNow(value: Boolean) {
        context.dataStore.edit { settings ->
            settings[OPEN_NOW] = value
            if (value) settings[OPEN_IN_1_HOUR] = false
        }
    }

    suspend fun saveOpenIn1Hour(value: Boolean) {
        context.dataStore.edit { settings ->
            settings[OPEN_IN_1_HOUR] = value
            if (value) settings[OPEN_NOW] = false
        }
    }

    suspend fun saveSortMode(mode: SortMode) {
        context.dataStore.edit { settings -> settings[SORT_MODE] = mode.name }
    }

    suspend fun saveShowRating(value: Boolean) {
        context.dataStore.edit { settings -> settings[SHOW_RATING] = value }
    }

    suspend fun saveMaxPriceLevel(level: Int?) {
        context.dataStore.edit { settings ->
            if (level == null) settings.remove(MAX_PRICE_LEVEL) else settings[MAX_PRICE_LEVEL] = level
        }
    }
```

In the `companion object`, add the keys:

```kotlin
        val OPEN_NOW = booleanPreferencesKey("open_now")
        val OPEN_IN_1_HOUR = booleanPreferencesKey("open_in_1_hour")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val SHOW_RATING = booleanPreferencesKey("show_rating")
        val MAX_PRICE_LEVEL = intPreferencesKey("max_price_level")
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/helloworld/data/UserPreferencesRepository.kt
git commit -m "feat(prefs): add SortMode + open-now/open-in-1h/sort/showRating/maxPrice with mutex"
```

---

## Task 5: GoogleGeocodingService — re-introduced, reads BuildConfig

**Files:**
- Create: `app/src/main/java/com/example/helloworld/GoogleGeocodingService.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.helloworld

import android.util.Log
import com.calmapps.calmmaps.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleGeocodingService {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun getCoordinates(address: String): Pair<Double, Double>? {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isEmpty()) return null
        return try {
            val response: GeocodingResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("address", address)
                parameter("key", apiKey)
            }.body()
            response.results.firstOrNull()?.geometry?.location?.let { it.lat to it.lng }
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting coordinates", e)
            null
        }
    }

    suspend fun getAddress(lat: Double, lon: Double): String? {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        if (apiKey.isEmpty()) {
            Log.e("GoogleGeocodingService", "API key is missing")
            return null
        }
        return try {
            val body = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("latlng", "$lat,$lon")
                parameter("key", apiKey)
            }.bodyAsText()
            val response: GeocodingResponse = Json {
                ignoreUnknownKeys = true; isLenient = true
            }.decodeFromString(body)
            response.results.firstOrNull()?.formattedAddress
        } catch (e: Exception) {
            Log.e("GoogleGeocodingService", "Error getting address", e)
            null
        }
    }
}

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>, val status: String = "")

@Serializable
data class GeocodingResult(
    @SerialName("address_components") val addressComponents: List<AddressComponent> = emptyList(),
    @SerialName("formatted_address") val formattedAddress: String? = null,
    val geometry: Geometry,
    @SerialName("place_id") val placeId: String = "",
    val types: List<String> = emptyList(),
)

@Serializable
data class AddressComponent(
    @SerialName("long_name") val longName: String = "",
    @SerialName("short_name") val shortName: String = "",
    val types: List<String> = emptyList(),
)

@Serializable data class Geometry(val location: GLocation)

@Serializable data class GLocation(val lat: Double, @SerialName("lng") val lng: Double)
```

(Type renamed `Location` → `GLocation` to avoid colliding with `android.location.Location`.)

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/helloworld/GoogleGeocodingService.kt
git commit -m "feat(geocoding): reintroduce GoogleGeocodingService reading BuildConfig key"
```

---

## Task 6: GooglePlacesApiService — search + autocomplete with new params + client filter

**Files:**
- Create: `app/src/main/java/com/example/helloworld/GooglePlacesApiService.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.helloworld

import android.content.Context
import android.util.Log
import com.calmapps.calmmaps.BuildConfig
import com.example.helloworld.data.SortMode
import com.example.helloworld.data.UserPreferencesRepository
import com.example.helloworld.util.TimePeriod
import com.example.helloworld.util.haversineMeters
import com.example.helloworld.util.isOpenThroughout
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.cos

interface PlacesBackend {
    suspend fun search(query: String, lat: Double, lon: Double): List<Poi>
    suspend fun autocomplete(query: String): List<String>
}

class GooglePlacesApiService(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) : PlacesBackend {

    private var placesClient: PlacesClient? = null
    private var token: AutocompleteSessionToken? = null

    private fun ensureClient() {
        val apiKey = BuildConfig.GOOGLE_PLACES_API_KEY
        check(apiKey.isNotEmpty()) {
            "GOOGLE_PLACES_API_KEY is empty. Set it in .env and rebuild."
        }
        if (!Places.isInitialized()) {
            Places.initialize(context.applicationContext, apiKey)
        }
        if (placesClient == null) placesClient = Places.createClient(context)
        if (token == null) token = AutocompleteSessionToken.newInstance()
    }

    override suspend fun search(query: String, lat: Double, lon: Double): List<Poi> {
        ensureClient()
        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.OPENING_HOURS,
            Place.Field.TYPES,
            Place.Field.WEBSITE_URI,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.PRICE_LEVEL,
        )
        val builder = SearchByTextRequest.builder(query, placeFields)

        val openNow = userPreferencesRepository.openNow.first()
        val openIn1Hour = userPreferencesRepository.openIn1Hour.first()
        val sortMode = userPreferencesRepository.sortMode.first()
        val maxPrice = userPreferencesRepository.maxPriceLevel.first()
        val radiusMiles = userPreferencesRepository.searchRadius.first().toDouble()

        if (openNow) builder.isOpenNow = true
        builder.setRankPreference(
            when (sortMode) {
                SortMode.RELEVANCE -> SearchByTextRequest.RankPreference.RELEVANCE
                SortMode.DISTANCE -> SearchByTextRequest.RankPreference.DISTANCE
            }
        )
        if (maxPrice != null) builder.maxPriceLevel = maxPrice

        if (lat != 0.0 || lon != 0.0) {
            val latDeg = radiusMiles / 69.0
            val lonDeg = radiusMiles / (cos(Math.toRadians(lat)) * 69.0)
            builder.setLocationBias(
                RectangularBounds.newInstance(
                    LatLng(lat - latDeg, lon - lonDeg),
                    LatLng(lat + latDeg, lon + lonDeg),
                )
            )
        }

        return try {
            val response = placesClient!!.searchByText(builder.build()).await()
            val now = LocalDateTime.now()
            val target = now.plusHours(1)
            response.places
                .let { if (openIn1Hour) it.filter { p -> passesOpenIn1Hour(p, now, target) } else it }
                .map { place -> place.toPoi(userLat = lat, userLon = lon) }
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error searching for places", e)
            emptyList()
        }
    }

    override suspend fun autocomplete(query: String): List<String> {
        ensureClient()
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(token)
            .setQuery(query)
            .build()
        return try {
            val response = placesClient!!.findAutocompletePredictions(request).await()
            response.autocompletePredictions.map { it.getFullText(null).toString() }
        } catch (e: Exception) {
            Log.e("GooglePlacesApiService", "Error getting autocomplete suggestions", e)
            emptyList()
        }
    }

    private fun passesOpenIn1Hour(place: Place, now: LocalDateTime, target: LocalDateTime): Boolean {
        val periods = place.openingHours?.periods?.map { p ->
            TimePeriod(
                openDay = p.open?.day?.toJavaDayOfWeek() ?: DayOfWeek.MONDAY,
                openHour = p.open?.time?.hours ?: 0,
                openMinute = p.open?.time?.minutes ?: 0,
                closeDay = p.close?.day?.toJavaDayOfWeek(),
                closeHour = p.close?.time?.hours ?: 0,
                closeMinute = p.close?.time?.minutes ?: 0,
            )
        }
        return isOpenThroughout(periods, now, target)
    }

    private fun com.google.android.libraries.places.api.model.DayOfWeek.toJavaDayOfWeek(): DayOfWeek =
        when (this) {
            com.google.android.libraries.places.api.model.DayOfWeek.MONDAY -> DayOfWeek.MONDAY
            com.google.android.libraries.places.api.model.DayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
            com.google.android.libraries.places.api.model.DayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
            com.google.android.libraries.places.api.model.DayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
            com.google.android.libraries.places.api.model.DayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
            com.google.android.libraries.places.api.model.DayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
            com.google.android.libraries.places.api.model.DayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
        }

    private fun Place.toPoi(userLat: Double, userLon: Double): Poi {
        val placeLat = latLng?.latitude
        val placeLng = latLng?.longitude
        val distance = if (placeLat != null && placeLng != null && (userLat != 0.0 || userLon != 0.0))
            haversineMeters(userLat, userLon, placeLat, placeLng) else null
        val priceLevelInt = priceLevel?.let {
            when (it) {
                Place.PriceLevel.FREE -> 0
                Place.PriceLevel.INEXPENSIVE -> 1
                Place.PriceLevel.MODERATE -> 2
                Place.PriceLevel.EXPENSIVE -> 3
                Place.PriceLevel.VERY_EXPENSIVE -> 4
                else -> null
            }
        }
        return Poi(
            name = name ?: "N/A",
            address = parseAddress(address),
            hours = openingHours?.weekdayText ?: emptyList(),
            phone = phoneNumber?.let { formatPhoneNumber(it) },
            description = placeTypes?.joinToString(", ") ?: "N/A",
            website = websiteUri?.toString(),
            lat = placeLat,
            lng = placeLng,
            rating = rating,
            userRatingCount = userRatingsTotal,
            priceLevel = priceLevelInt,
            distanceMeters = distance,
        )
    }

    private fun parseAddress(addressString: String?): Address {
        if (addressString == null) return Address("N/A", "", "", "", "")
        val parts = addressString.split(",").map { it.trim() }
        val street = parts.getOrElse(0) { addressString }
        val city = parts.getOrElse(1) { "" }
        val country = if (parts.size > 2 && parts.last().matches(Regex("[A-Za-z ]+"))) parts.last() else ""
        var state = ""
        var zip = ""
        if (parts.size > 2) {
            val stateZip = if (country.isNotEmpty()) parts[parts.size - 2] else parts.last()
            val tokens = stateZip.split(" ")
            zip = tokens.lastOrNull { it.any(Char::isDigit) } ?: ""
            state = tokens.filter { it != zip }.joinToString(" ")
        }
        return Address(street, city, state, zip, country)
    }

    private fun formatPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("1") ->
                "(${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            digits.length == 10 ->
                "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            else -> phone
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS. If `Place.priceLevel` returns nullable enum and has unhandled `else`, the `else -> null` branch covers it.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/helloworld/GooglePlacesApiService.kt
git commit -m "feat(search): reintroduce GooglePlacesApiService with rating/price/sort/openIn1Hour"
```

---

## Task 7: Wire SearchViewModel to Google + delete HERE services

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/SearchViewModel.kt`
- Delete: `app/src/main/java/com/example/helloworld/HerePlacesApiService.kt`
- Delete: `app/src/main/java/com/example/helloworld/HereGeocodingService.kt`

- [ ] **Step 1: Read SearchViewModel and identify HERE references**

Run: `grep -n "Here\|HERE" app/src/main/java/com/example/helloworld/SearchViewModel.kt`
Expected: lines instantiating `HerePlacesApiService` and `HereGeocodingService`.

- [ ] **Step 2: Replace HERE wiring with Google**

In `SearchViewModel.kt`, replace:

```kotlin
private val backend: PlacesBackend = HerePlacesApiService(userPreferencesRepository)
```

with:

```kotlin
private val backend: PlacesBackend = GooglePlacesApiService(application, userPreferencesRepository)
```

Replace any `HereGeocodingService(...)` instantiation with `GoogleGeocodingService()` and update the call sites (it no longer takes `userPreferencesRepository`). If any call site uses `hereGeocodingService.someMethod(...)`, the method signatures from `GoogleGeocodingService` are: `getCoordinates(address: String): Pair<Double, Double>?` and `getAddress(lat: Double, lon: Double): String?`.

- [ ] **Step 3: Delete HERE service files**

```bash
git rm app/src/main/java/com/example/helloworld/HerePlacesApiService.kt
git rm app/src/main/java/com/example/helloworld/HereGeocodingService.kt
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS. Fix any other call sites that referenced the HERE classes (likely none beyond ViewModels).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/helloworld/SearchViewModel.kt
git commit -m "refactor(search): wire SearchViewModel to Google Places, drop HERE backend"
```

---

## Task 8: Wire SettingsViewModel to Google

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/SettingsViewModel.kt`

- [ ] **Step 1: Identify HERE references**

Run: `grep -n "Here\|HERE" app/src/main/java/com/example/helloworld/SettingsViewModel.kt`

- [ ] **Step 2: Replace with Google equivalents**

Same pattern as Task 7 — `HerePlacesApiService` → `GooglePlacesApiService(application, userPreferencesRepository)`, `HereGeocodingService(userPreferencesRepository)` → `GoogleGeocodingService()`.

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/helloworld/SettingsViewModel.kt
git commit -m "refactor(settings): wire SettingsViewModel to Google Places + Geocoding"
```

---

## Task 9: MainViewModel — switch API key gate to GOOGLE_PLACES_API_KEY

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/MainViewModel.kt`

- [ ] **Step 1: Replace HERE_API_KEY reference**

Replace the existing line:

```kotlin
val apiKey: StateFlow<String?> = MutableStateFlow(BuildConfig.HERE_API_KEY.ifEmpty { null })
```

with:

```kotlin
val apiKey: StateFlow<String?> = MutableStateFlow(BuildConfig.GOOGLE_PLACES_API_KEY.ifEmpty { null })
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/helloworld/MainViewModel.kt
git commit -m "refactor(main): use GOOGLE_PLACES_API_KEY for the no-key gate"
```

---

## Task 10: SettingsScreen — Search filters section

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/SettingsScreen.kt`

- [ ] **Step 1: Read existing layout**

Run: `wc -l app/src/main/java/com/example/helloworld/SettingsScreen.kt && grep -n "fun \|searchRadius\|MapApp\|defaultLocation\|distanceUnit" app/src/main/java/com/example/helloworld/SettingsScreen.kt`
Expected: locate the section that follows the search-radius slider and precedes Map App / Default Location.

- [ ] **Step 2: Add the Search filters section**

Insert this composable block after the search radius slider, before Map App / Default Location. Add the necessary imports at the top of the file:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.helloworld.data.SortMode
import kotlinx.coroutines.launch
```

(Many of these may already be imported — only add what's missing.)

Inside the screen body, after the radius slider section, insert:

```kotlin
val openNow by userPreferencesRepository.openNow.collectAsState(initial = false)
val openIn1Hour by userPreferencesRepository.openIn1Hour.collectAsState(initial = false)
val sortMode by userPreferencesRepository.sortMode.collectAsState(initial = SortMode.RELEVANCE)
val showRating by userPreferencesRepository.showRating.collectAsState(initial = true)
val maxPriceLevel by userPreferencesRepository.maxPriceLevel.collectAsState(initial = null)
val coroutineScope = rememberCoroutineScope()

Spacer(Modifier.height(16.dp))
Text("Search filters", style = MaterialTheme.typography.titleMedium)
Spacer(Modifier.height(8.dp))

Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Open now", modifier = Modifier.weight(1f))
    Switch(
        checked = openNow,
        onCheckedChange = { coroutineScope.launch { userPreferencesRepository.saveOpenNow(it) } },
    )
}
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Open in 1 hour", modifier = Modifier.weight(1f))
    Switch(
        checked = openIn1Hour,
        onCheckedChange = { coroutineScope.launch { userPreferencesRepository.saveOpenIn1Hour(it) } },
    )
}

Spacer(Modifier.height(8.dp))
Text("Sort by")
Row(verticalAlignment = Alignment.CenterVertically) {
    RadioButton(
        selected = sortMode == SortMode.RELEVANCE,
        onClick = { coroutineScope.launch { userPreferencesRepository.saveSortMode(SortMode.RELEVANCE) } },
    )
    Text("Relevance", modifier = Modifier.padding(end = 16.dp))
    RadioButton(
        selected = sortMode == SortMode.DISTANCE,
        onClick = { coroutineScope.launch { userPreferencesRepository.saveSortMode(SortMode.DISTANCE) } },
    )
    Text("Distance")
}

Spacer(Modifier.height(8.dp))
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Show ratings", modifier = Modifier.weight(1f))
    Switch(
        checked = showRating,
        onCheckedChange = { coroutineScope.launch { userPreferencesRepository.saveShowRating(it) } },
    )
}

Spacer(Modifier.height(8.dp))
Text("Max price")
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    val options: List<Pair<Int?, String>> = listOf(
        null to "Any",
        1 to "$",
        2 to "$$",
        3 to "$$$",
        4 to "$$$$",
    )
    options.forEach { (level, label) ->
        FilterChip(
            selected = maxPriceLevel == level,
            onClick = { coroutineScope.launch { userPreferencesRepository.saveMaxPriceLevel(level) } },
            label = { Text(label) },
        )
    }
}
Spacer(Modifier.height(16.dp))
```

If the existing screen used `userPreferencesRepository` differently (e.g., wrapped via a ViewModel), adapt the lambdas to call the equivalent VM functions. Read the file first to determine the pattern.

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/helloworld/SettingsScreen.kt
git commit -m "feat(settings): add Search filters (open-now/open-in-1h, sort, show-rating, max-price)"
```

---

## Task 11: SearchScreen — render rating / price / distance

**Files:**
- Modify: `app/src/main/java/com/example/helloworld/SearchScreen.kt`

- [ ] **Step 1: Add helpers and pass distance unit**

Add a parameter `distanceUnit: DistanceUnit` (and `showRating: Boolean`) to `SearchScreen`. Update its caller (likely in `SearchScreenHost.kt`) to pass them in from `userPreferencesRepository`.

```kotlin
@Composable
fun SearchScreen(
    searchResults: List<Poi>,
    showRating: Boolean,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
    onPoiSelected: (Poi) -> Unit,
) { ... }
```

- [ ] **Step 2: Replace the `Column` body with the enriched layout**

Inside the `Column(modifier = Modifier.weight(1f))`, after the existing name `Text` and below the city/state line, add:

```kotlin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.layout.size
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.util.decomposeStars
import com.example.helloworld.util.formatKilometers
import com.example.helloworld.util.formatMiles
```

```kotlin
if (showRating && poi.rating != null) {
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val stars = decomposeStars(poi.rating)
        repeat(stars.full) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        if (stars.half) {
            Icon(Icons.Filled.StarHalf, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        repeat(stars.empty) {
            Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        if (poi.userRatingCount != null) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = "(${"%,d".format(poi.userRatingCount)})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

val priceText = poi.priceLevel?.takeIf { it in 1..4 }?.let { "$".repeat(it) }
val distanceText = poi.distanceMeters?.let {
    if (distanceUnit == DistanceUnit.METRIC) formatKilometers(it) else formatMiles(it)
}
val secondary = listOfNotNull(priceText, distanceText).joinToString(" · ")
if (secondary.isNotEmpty()) {
    Spacer(Modifier.height(2.dp))
    Text(text = secondary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

- [ ] **Step 3: Update the caller to pass the new params**

Read `SearchScreenHost.kt` (or wherever `SearchScreen(...)` is invoked) and pass:

```kotlin
val showRating by userPreferencesRepository.showRating.collectAsState(initial = true)
val distanceUnit by userPreferencesRepository.distanceUnit.collectAsState(initial = DistanceUnit.IMPERIAL)
SearchScreen(
    searchResults = ...,
    showRating = showRating,
    distanceUnit = distanceUnit,
    modifier = ...,
    onPoiSelected = ...,
)
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/helloworld/SearchScreen.kt app/src/main/java/com/example/helloworld/SearchScreenHost.kt
git commit -m "feat(search-ui): render rating stars, price chip, and distance per result"
```

---

## Task 12: Final smoke build + manual checklist

- [ ] **Step 1: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL with the real `MAPBOX_DOWNLOADS_TOKEN` and `GOOGLE_PLACES_API_KEY` in `.env`.

If the build fails on Mapbox SDK download, verify `MAPBOX_DOWNLOADS_TOKEN` has scopes `DOWNLOADS:READ` and `NAVIGATION:DOWNLOAD`. If it fails on Google API issues, the placeholder `insert-token` may have been left in `.env` — the build itself will still succeed (key is just baked into BuildConfig as a string), but runtime calls will fail.

- [ ] **Step 2: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all SearchHelpers tests pass.

- [ ] **Step 3: Manual smoke checklist (run on device/emulator)**

Cannot be automated; verify each item:

- [ ] Settings screen shows the new "Search filters" section.
- [ ] Toggling "Open now" ON disables "Open in 1 hour" automatically.
- [ ] Toggling "Open in 1 hour" ON disables "Open now" automatically.
- [ ] Sort radio defaults to Relevance; switching to Distance reorders subsequent search results closest-first.
- [ ] Show ratings off → no stars in result rows. On → stars + count rendered.
- [ ] Max price set to `$$` excludes `$$$` and `$$$$` results from the API response.
- [ ] Search result row shows: name, stars + count (if showRating), `$$` chip (if priceLevel), distance in mi/km (per `distanceUnit`), then address.
- [ ] Tapping a result → details screen → tapping the navigate icon launches Mapbox `NavigationScreen` (unchanged behavior).
- [ ] No HERE-related code remains: `git grep -i 'here_api_key\|HerePlacesApiService\|HereGeocodingService'` returns nothing.

- [ ] **Step 4: Final commit if any cleanup was needed**

If steps 1–3 surfaced minor fixes, commit them with a tight message. Otherwise nothing to commit.

---

## Self-Review Notes

- **Spec coverage:** all five Settings options + result-row enrichment + provider replacement + .env wiring + Mapbox-nav preservation are each tied to a numbered task.
- **Type consistency:** `SortMode` (data layer enum), `Place.PriceLevel` (SDK) → `Int` (Poi field) bridge happens in one place in Task 6. `StarBreakdown` consumed only in Task 11. `TimePeriod` defined in Task 2, consumed in Task 6.
- **No placeholders:** every code step contains the literal text to write.
- **Known gaps:** Compose UI not unit-tested (deliberate — see Testing scope at top).
