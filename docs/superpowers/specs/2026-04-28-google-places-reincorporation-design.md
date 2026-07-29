# Google Places Reincorporation — Design

**Date:** 2026-04-28
**Branch context:** `main` was reset to `upstream/feature/mapbox-navigation` (`25a2e3b`). Branch currently uses HERE Places exclusively for search; Mapbox Navigation for routing.

## Goal

Replace HERE Places with Google Places as the sole search backend, sourcing the API key from `.env` at build time. Add Google-only result enrichment (rating + price) and filters (open-now, open-in-1-hour, sort by relevance/distance, max-price chip, show-rating toggle). Mapbox Navigation handoff is unchanged — POIs flow into the existing `NavigationScreen` regardless of provider.

## Architecture

**Provider:** single backend, `GooglePlacesApiService` (re-introduced from `/StudioProjects/GOOGLE_PLACES_API_INTEGRATION.md`). `HerePlacesApiService` and `HereGeocodingService` are deleted. No `SearchProvider` enum, no provider switching.

**API key:** `GOOGLE_PLACES_API_KEY` in `.env` → `buildConfigField` in `app/build.gradle` → `BuildConfig.GOOGLE_PLACES_API_KEY` read directly by the service. No DataStore key, no Settings entry, no onboarding key prompt. Removes `HERE_API_KEY` `buildConfigField`.

**Mapbox nav:** unchanged. `MainActivity.kt:373` already routes the navigate icon to `composable("map?...")` (Mapbox `NavigationScreen`).

## Data model — `Poi.kt`

```kotlin
data class Poi(
    val name: String,
    val address: Address,
    val hours: List<String>,
    val phone: String?,
    val description: String,
    val website: String?,
    val lat: Double?,
    val lng: Double?,
    val rating: Double? = null,           // 1.0..5.0
    val userRatingCount: Int? = null,
    val priceLevel: Int? = null,          // 0..4
    val distanceMeters: Double? = null,   // computed via Haversine
)
```

`geoapifyPlaceId` field removed.

## Preferences — `data/UserPreferencesRepository.kt`

| Pref | Type | Default | Notes |
|---|---|---|---|
| `openNow` | `Boolean` | `false` | Existing, preserved |
| `openIn1Hour` | `Boolean` | `false` | New. Mutex enforced in setters |
| `sortMode` | `enum SortMode { RELEVANCE, DISTANCE }` | `RELEVANCE` | New |
| `showRating` | `Boolean` | `true` | New |
| `maxPriceLevel` | `Int?` (0..4, null = Any) | `null` | New |

Mutex implementation: `saveOpenNow(true)` writes `openNow=true, openIn1Hour=false` in one `dataStore.edit { }` block. `saveOpenIn1Hour(true)` does the inverse. Setting either to `false` is a single-key write.

## `GooglePlacesApiService.search` — request mapping

- `placeFields` includes (in addition to schematic baseline): `Place.Field.RATING`, `Place.Field.USER_RATINGS_TOTAL`, `Place.Field.PRICE_LEVEL`.
- `requestBuilder.isOpenNow = userPreferencesRepository.openNow.first()`. **Not** set for `openIn1Hour`.
- `requestBuilder.setRankPreference(...)` from `sortMode` → `RankPreference.RELEVANCE` or `RankPreference.DISTANCE`.
- `requestBuilder.setMaxPriceLevel(...)` only when `maxPriceLevel != null`.
- `setLocationBias` rectangular bounds from `searchRadius` (mile→degree math unchanged from schematic).

## Client-side post-filter

Only runs when `openIn1Hour == true`. After API returns, filter the list:

```kotlin
private fun isOpenAtAndIn(periods: List<Period>?, now: LocalDateTime, target: LocalDateTime): Boolean {
    if (periods == null) return true                    // lenient (Q2-B)
    if (periods.size == 1 && periods[0].close == null) return true  // 24/7
    for (p in periods) {
        val openAt = p.open?.toLocalDateTime(now) ?: continue
        val closeAt = p.close?.toLocalDateTime(now) ?: continue
        // Handles midnight crossing: closeAt may be on next day per period.close.day
        if (!now.isBefore(openAt) && now.isBefore(closeAt) && !target.isAfter(closeAt)) {
            return true
        }
    }
    return false
}
```

`Period.TimeOfWeek.toLocalDateTime(reference)` builds a `LocalDateTime` for the given weekday/hour/minute, normalised to the same week as `reference`.

Distance is computed unconditionally on every result (Haversine, `now`-independent), stored as `distanceMeters` on the `Poi`.

## UI — result row (`SearchScreen.kt`)

```
Coffee Shop Name
★★★★½  (1,234)  ·  $$  ·  0.4 mi
123 Main St, Springfield
```

- Stars row: only when `showRating == true && rating != null`. Renders 5 icons: full = `Icons.Filled.Star`, half = `Icons.Filled.StarHalf`, empty = `Icons.Outlined.StarBorder`. Rounding: `roundToHalf(rating)` = `round(rating * 2) / 2`.
- Review count: `(${formatCount(userRatingCount)})` in muted small caption text. `formatCount` uses comma separators.
- Price: `"$".repeat(priceLevel)` when `priceLevel in 1..4`. (`priceLevel == 0` → "FREE" rare; render as nothing.)
- Distance: `formatMiles(distanceMeters)` — `0.4 mi` (`< 10 mi`, one decimal) or `12 mi` (≥ 10mi, integer). Always when `distanceMeters != null`.
- Separators between rating-cluster, price, and distance: middle dot `·` with surrounding spaces.

## UI — Settings (`SettingsScreen.kt`)

New "Search filters" section between the search-radius slider and MapApp/Default Location:

- Open now switch (existing — relabel by removing "(Google only)")
- Open in 1 hour switch (new) — toggling either to `true` flips the other to `false` via repository mutex
- Sort by: radio "Relevance" / "Distance" (default Relevance)
- Show ratings switch (new, default on)
- Max price: single-select chip row `Any | $ | $$ | $$$ | $$$$`, default `Any`

Removed:
- "Open now (Google only)" label suffix
- Provider radio (none in current branch anyway)
- API key entry field, masked display, edit affordance, save snackbar
- `NoApiKeyScreen` and its route in `MainActivity` if only triggered by missing Google key

## Build config

`app/build.gradle`:
- Add `implementation 'com.google.android.libraries.places:places:3.5.0'`
- Add `buildConfigField "String", "GOOGLE_PLACES_API_KEY", "\"${envProperties.getProperty('GOOGLE_PLACES_API_KEY', '')}\""` (read from already-loaded `envProperties`)
- Remove `buildConfigField "String", "HERE_API_KEY", ...`

`.env`: `GOOGLE_PLACES_API_KEY` already present.

## Files touched

**Created:** `GooglePlacesApiService.kt`, `GoogleGeocodingService.kt` (from schematic, with diffs above).

**Modified:** `Poi.kt`, `data/UserPreferencesRepository.kt`, `SearchViewModel.kt`, `SettingsViewModel.kt`, `MainViewModel.kt`, `SettingsScreen.kt`, `SearchScreen.kt` (or wherever the result row is rendered), `app/build.gradle`, `MainActivity.kt` (if `NoApiKeyScreen` route is removed).

**Deleted:** `HerePlacesApiService.kt`, `HereGeocodingService.kt`, possibly `NoApiKeyScreen.kt`.

## Out of scope

- Custom Mudita-styled star/chip vector assets (Material is the placeholder)
- `MapApp.HERE_WEGO` enum value removal (keep — it's an external app launch target, independent of the search backend)
- Geoapify (already gone from this branch)
- Any change to Mapbox navigation, offline maps, or active-navigation screens
