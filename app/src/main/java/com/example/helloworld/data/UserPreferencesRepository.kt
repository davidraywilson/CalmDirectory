package com.example.helloworld.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class DistanceUnit {
    IMPERIAL,
    METRIC
}

enum class SortMode {
    RELEVANCE,
    DISTANCE,
    RATING,
}

@Serializable
data class QuickLocation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val address: String
)

class UserPreferencesRepository(
    private val context: Context
) {
    val useDeviceLocation: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[USE_DEVICE_LOCATION] ?: false }

    val defaultLocation: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[DEFAULT_LOCATION] }

    val mapApp: Flow<MapApp> = context.dataStore.data
        .map { preferences ->
            MapApp.valueOf(preferences[MAP_APP] ?: MapApp.DEFAULT.name)
        }

    val searchRadius: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[SEARCH_RADIUS] ?: 10 }

    val distanceUnit: Flow<DistanceUnit> = context.dataStore.data
        .map { preferences ->
            val stored = preferences[DISTANCE_UNIT]
            if (stored != null) {
                try {
                    DistanceUnit.valueOf(stored)
                } catch (e: IllegalArgumentException) {
                    DistanceUnit.IMPERIAL
                }
            } else {
                DistanceUnit.IMPERIAL
            }
        }

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

    val maxPriceLevel: Flow<Int?> = context.dataStore.data
        .map { it[MAX_PRICE_LEVEL] }

    private val QUICK_LOCATIONS = stringPreferencesKey("quick_locations")

    val quickLocations: Flow<List<QuickLocation>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[QUICK_LOCATIONS] ?: "[]"
            try {
                Json.decodeFromString<List<QuickLocation>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun addQuickLocation(label: String, address: String) {
        context.dataStore.edit { settings ->
            val currentJson = settings[QUICK_LOCATIONS] ?: "[]"
            val currentList: MutableList<QuickLocation> = try {
                Json.decodeFromString<List<QuickLocation>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            currentList.add(QuickLocation(label = label, address = address))
            settings[QUICK_LOCATIONS] = Json.encodeToString(currentList)
        }
    }

    suspend fun removeQuickLocation(id: String) {
        context.dataStore.edit { settings ->
            val currentJson = settings[QUICK_LOCATIONS] ?: "[]"
            val currentList: List<QuickLocation> = try {
                Json.decodeFromString<List<QuickLocation>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            settings[QUICK_LOCATIONS] = Json.encodeToString(currentList.filter { it.id != id })
        }
    }
    suspend fun saveUseDeviceLocation(useDeviceLocation: Boolean) {
        context.dataStore.edit { settings -> settings[USE_DEVICE_LOCATION] = useDeviceLocation }
    }

    suspend fun saveDefaultLocation(defaultLocation: String) {
        context.dataStore.edit { settings -> settings[DEFAULT_LOCATION] = defaultLocation }
    }

    suspend fun saveSearchRadius(radius: Int) {
        context.dataStore.edit { settings -> settings[SEARCH_RADIUS] = radius }
    }

    suspend fun saveDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { settings -> settings[DISTANCE_UNIT] = unit.name }
    }

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

    private companion object {
        val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val MAP_APP = stringPreferencesKey("map_app")
        val SEARCH_RADIUS = intPreferencesKey("search_radius")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val OPEN_NOW = booleanPreferencesKey("open_now")
        val OPEN_IN_1_HOUR = booleanPreferencesKey("open_in_1_hour")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val SHOW_RATING = booleanPreferencesKey("show_rating")
        val MAX_PRICE_LEVEL = intPreferencesKey("max_price_level")
    }
}