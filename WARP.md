# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

**CalmDirectory** (internal name: HelloWorld) is an Android application built with Jetpack Compose that provides a directory/search interface for points of interest (POIs) such as restaurants, gas stations, hotels, etc. The app uses Google Places API for location search and supports both device-based and manual location selection.

### Tech Stack
- **Language**: Kotlin 1.9.22
- **UI Framework**: Jetpack Compose (version 1.7.3)
- **Build System**: Gradle 8.3.0
- **Min SDK**: 28, Target SDK: 35
- **Networking**: Ktor Client (2.3.2), Retrofit (2.9.0), OkHttp (4.12.0)
- **Architecture**: MVVM with ViewModels and StateFlow
- **Data Storage**: DataStore Preferences
- **Material Design**: Material 3 + Custom MMD Components (Mudita Material Design library)

## Build and Development Commands

### Build and Run
```bash
# Build the project (requires Gradle installation or use Android Studio)
gradle build

# Build release APK
gradle assembleRelease

# Build debug APK
gradle assembleDebug

# Clean build artifacts
gradle clean
```

Note: This project does not include Gradle wrapper scripts (`gradlew`). Use system Gradle or Android Studio.

### Run Tests
```bash
# Run unit tests
gradle test

# Run Android instrumented tests
gradle connectedAndroidTest
```

### Install and Run
- Use Android Studio's Run button or deploy via `adb`:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Code Architecture

### Navigation Flow
The app uses Jetpack Compose Navigation with the following routes:
- **`main`**: Landing screen with POI categories (restaurants, gas stations, etc.)
- **`settings`**: Configuration screen for API key and location preferences
- **`search?query={query}&autoFocus={autoFocus}`**: Search interface with real-time results
- **`details/{poiName}/{poiAddress}/...`**: Detail view for a specific POI

Navigation logic is centralized in `MainActivity.kt` using `NavHost`.

### MVVM Architecture

#### ViewModels
- **`MainViewModel`**: Manages API key state and loading status. Reads from `UserPreferencesRepository`.
- **`SearchViewModel`**: Handles search queries, coordinates location services, and fetches POI results via `GooglePlacesApiService`.
- **`SettingsViewModel`**: Manages user preferences including API key, location settings, and location autocomplete suggestions.

#### Data Layer
- **`UserPreferencesRepository`** (`data/UserPreferencesRepository.kt`): Manages persistent settings using DataStore:
  - API key storage
  - Device location preference toggle
  - Default location string
- **`LocationRepository`** (`data/LocationRepository.kt`): Wraps `LocationService` to provide reactive location updates via StateFlow.

### Service Layer

#### Location Services
- **`LocationService`**: Handles GPS/Network location requests via Android LocationManager. Key methods:
  - `getCurrentLocation()`: Suspending function that returns current location
  - `startLocationUpdates()` / `stopLocationUpdates()`: For continuous location tracking
  - Prioritizes GPS_PROVIDER for de-googled device compatibility

#### API Services
- **`GooglePlacesApiService`**: Integrates with Google Places SDK for Android:
  - `search(query, lat, lon)`: Text-based place search with location biasing (10-mile radius)
  - `getAutocompleteSuggestions(query)`: Returns autocomplete predictions
  - Handles API key initialization from DataStore
  - Parses place data into `Poi` objects with custom phone number formatting

- **`NominatimGeocodingService`**: Provides geocoding via OpenStreetMap Nominatim API:
  - `getCoordinates(locationName)`: Converts location string to lat/lon coordinates
  - Used when device location is disabled and user provides a default location

### Data Models
- **`Poi`**: Point of Interest data class with name, address, hours, phone, description, website
- **`Address`**: Structured address with street, city, state, zip, country

### UI Components

#### Screens
- **`LandingScreen`**: Grid of POI categories with icons (uses MMD components)
- **`SearchScreen`**: List view of search results
- **`SearchScreenHost`**: Wrapper that manages search state and loading indicators
- **`SettingsScreen`**: Form for API key and location configuration with real-time validation
- **`PoiDetailsScreen`**: Full details view with action buttons (map, phone, website)
- **`NoApiKeyScreen`**: Prompts user to configure API key on first launch

#### Custom Top Bar
`DirectoryTopAppBar` in `MainActivity.kt` dynamically changes based on navigation route:
- Main screen: Shows "Directory" with search and settings icons
- Search screen: Embeds a SearchBar with clear button
- Details screen: Shows POI name with action icons (website, map, call)
- Settings screen: Shows "Settings" with back button

### Key Dependencies
- **Mudita Material Design (MMD)**: Custom component library (`com.mudita:MMD:1.0.0`) providing styled components like `LazyColumnMMD`, `ButtonMMD`, `TextFieldMMD`, `TopAppBarMMD`, etc.
- **Google Places SDK**: Primary POI search provider
- **AndroidX Compose Navigation**: Screen routing and deep linking
- **DataStore**: Persistent key-value storage for settings

## Configuration Requirements

### API Key Setup
The app requires a Google Places API key:
1. Obtain a key from Google Cloud Console with Places API enabled
2. On first launch, app shows `NoApiKeyScreen` prompting user to enter key
3. Key is stored in DataStore and loaded into `MainViewModel`
4. Key is also referenced in `AndroidManifest.xml` via `@string/places_api_key` meta-data

### Location Permissions
Declared in `AndroidManifest.xml`:
- `ACCESS_FINE_LOCATION`: For GPS-based location
- `ACCESS_COARSE_LOCATION`: For network-based location
- `INTERNET`: For API requests

User can toggle between device location and manual location in Settings.

## Important Implementation Details

### Phone Number Formatting
`GooglePlacesApiService.formatPhoneNumber()` standardizes US phone numbers to `(XXX) XXX-XXXX` format, handling both 10 and 11-digit formats.

### Location Biasing
Search queries are biased within a 10-mile radius of the current/default location using `RectangularBounds` in the Places API request. Calculations use approximate degree conversion (69 miles per latitude degree).

### URL Encoding in Navigation
POI details are passed via URL parameters with encoding/decoding in `MainActivity.kt`. Special handling for forward slashes (`%2F`) to prevent navigation parsing issues.

### State Management Pattern
All ViewModels use `StateFlow` for reactive state:
- `MutableStateFlow` for internal state
- `.asStateFlow()` for exposed read-only state
- `collectAsState()` in Composables for UI updates

### De-Googled Device Support
`LocationService` explicitly prioritizes `GPS_PROVIDER` and provides fallback logic, making it compatible with devices without Google Play Services.
