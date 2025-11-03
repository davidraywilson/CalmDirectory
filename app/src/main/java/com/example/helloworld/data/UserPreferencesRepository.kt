package com.example.helloworld.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(
    private val context: Context
) {
    val apiKey: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY]
        }

    val useDeviceLocation: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_DEVICE_LOCATION] ?: true
        }

    val defaultLocation: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_LOCATION]
        }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { settings ->
            settings[API_KEY] = apiKey
        }
    }

    suspend fun saveUseDeviceLocation(useDeviceLocation: Boolean) {
        context.dataStore.edit { settings ->
            settings[USE_DEVICE_LOCATION] = useDeviceLocation
        }
    }

    suspend fun saveDefaultLocation(defaultLocation: String) {
        context.dataStore.edit { settings ->
            settings[DEFAULT_LOCATION] = defaultLocation
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
    }
}
