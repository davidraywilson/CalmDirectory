package com.paperapps.papermaps.data

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
    private companion object {
        val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val MAP_APP = stringPreferencesKey("map_app")
        val SEARCH_RADIUS = intPreferencesKey("search_radius")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    }
}