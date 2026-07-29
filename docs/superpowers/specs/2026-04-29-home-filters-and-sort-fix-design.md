# Home Filters Relocation & Sort Bug Fix

**Date:** 2026-04-29

## Problem

1. **Sort bug.** Sort by distance reliably returns no results because
   `GooglePlacesApiService.kt` pairs `RankPreference.DISTANCE` with
   `setLocationBias(RectangularBounds)`. The Places SDK requires
   `setLocationRestriction(CircularBounds)` when ranking by distance —
   the bias-with-rectangle request is malformed and yields nothing.
   Sort by relevance/rating can also feel broken because the
   `SearchViewModel` never re-runs an active search when filter prefs
   change, so toggling sort in Settings leaves stale results until the
   user retypes.
2. **Discoverability.** Search filters and search radius live deep in
   Settings → Search tab, while the home page surfaces a list of POI
   categories that has minimal value. Users want filters one tap away.

## Design

### 1. Home page restructure (`LandingScreen.kt`, `MainScreen.kt`)

- **Top:** existing saved-locations `LazyRow` (Add chip + saved chips)
  unchanged.
- **Below the divider:** a new `SearchFilterControls` composable hosting
  the radius slider + filter section moved verbatim from Settings.
- **Removed:** `poiCategories` constant, `getIconForCategory()`, and
  the `LazyColumn` of category rows in `LandingScreen`. The
  `onCategorySelected` callback is dropped from the `LandingScreen`
  signature, and the corresponding lambda is removed from
  `MainScreen.kt`.

### 2. New composable: `SearchFilterControls.kt`

Single-file extraction of the radius + filters block from
`SettingsScreen.kt:193-352`. Self-contained: instantiates its own
`UserPreferencesRepository` from `LocalContext`, matching the existing
`SettingsScreen` pattern. Renders inside a `Column`, intended to be
placed as a single `item` in a parent `LazyColumn` (or used directly).

Controls (top to bottom):

- Search Radius slider — label adapts to `DistanceUnit` pref (e.g.
  "Search Radius: 10 miles" or "16.1 km").
- Divider.
- "Search filters" header.
- Open now (switch) — mutually exclusive with the next.
- Open for the next hour (switch) — mutually exclusive with the
  previous.
- Sort by — Relevance / Distance / Rating radio rows.
- Show ratings (switch).
- Max price — Any / $ / $$ / $$$ / $$$$ segmented buttons.

### 3. Settings cleanup (`SettingsScreen.kt`)

Remove the radius slider block and the filters block from the Search
tab. The Search tab is left with:

- Use device location (toggle).
- Default Location (display + edit form with autocomplete).

Tab structure unchanged (`Search` / `Maps`). Trim imports that fall out.

### 4. Sort bug fix (`GooglePlacesApiService.kt`)

Switch from `RectangularBounds` to `CircularBounds`. Define
`MAX_SEARCH_RADIUS_MILES = 20.0`. Branch on whether the user's slider is
maxed:

- **`radiusMiles < 20`** (slider has headroom) — hard cap with
  `setLocationRestriction(CircularBounds(LatLng(lat, lon),
  radiusMiles * 1609.34))` for **all** sort modes. If the user wants
  results farther out, they can raise the slider.
- **`radiusMiles == 20`** (slider maxed, user has no way to ask for
  "more") — don't impose a hard wall:
  - `RELEVANCE` / `RATING`: `setLocationBias(CircularBounds(20mi))` —
    prefers nearby but allows distant results through.
  - `DISTANCE`: SDK requires a restriction, so use
    `setLocationRestriction(CircularBounds(100mi))` — far enough that
    rare distant matches surface.

Drop unused `kotlin.math.cos` import. Add `CircularBounds` import.

### 5. Live filter updates (`SearchViewModel.kt`)

Add a second `viewModelScope.launch { ... }` block in `init` that
combines the filter prefs (`searchRadius`, `openNow`, `openIn1Hour`,
`sortMode`, `maxPriceLevel`), drops the initial cold-start emission,
and re-runs the active search via `onSearchQueryChange(_searchQuery
.value)` when any of them change. `onSearchQueryChange` already
debounces (300 ms), cancels in-flight jobs, and updates loading state,
so reusing it is safe. `showRating` and `distanceUnit` are
display-only — excluded from the combine.

### 6. Dead code cleanup (`Poi.kt`, `SearchScreen.kt`)

`Poi.isOutsideSearchRadius` is only ever its default `false` — never
set anywhere. Drop the field and the
`if (poi.isOutsideSearchRadius) { ... }` UI block in `SearchScreen.kt`.

## Verification

- `./gradlew assembleDebug` builds clean.
- Install via `adb -s MK20250404519 install -r` and exercise:
  - Sort = Distance with radius < 20 → non-empty results, distance asc.
  - Sort = Distance at radius = 20 mi → still gets distant results
    (up to 100 mi).
  - Sort = Relevance / Rating → non-empty results.
  - Home page: saved-locations row, radius slider, filter controls
    render and persist.
  - Settings → Search tab: only location settings remain.
  - Existing search → home → change filter → return to search → results
    refresh without retyping.
  - Toggling `Show ratings` does not trigger a re-fetch (display only).
- `adb logcat -s SearchViewModel:D GooglePlacesApiService:E` for
  silent-failure visibility.

## Out of scope

- Restructuring the Settings tabs (was option C in brainstorming;
  user picked option A: leave tabs as-is).
- Adding unit tests — codebase has only `SearchHelpersTest.kt` for pure
  helpers; the new logic is straightforward branching and observation
  patterns that mirror existing tested-by-hand code.
