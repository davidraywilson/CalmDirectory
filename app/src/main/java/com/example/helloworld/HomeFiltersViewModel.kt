package com.example.helloworld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.SortMode
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Holds search filter prefs as hot StateFlows so the home page composables don't
 * flicker through `collectAsState` initial values on each navigation. Scoped to
 * the "main" nav entry — the back-stack-entry-scoped ViewModel survives
 * navigation while the destination's composition is rebuilt.
 *
 * Initial values are loaded synchronously via a one-time runBlocking on cold
 * start. DataStore reads are file-backed and fast (~10ms total for 7 prefs);
 * the alternative — letting the cold flow emit asynchronously — produces a
 * visible flicker on e-ink, which is what we're avoiding.
 */
class HomeFiltersViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = UserPreferencesRepository(application)

    val searchRadius: StateFlow<Int>
    val openNow: StateFlow<Boolean>
    val openIn1Hour: StateFlow<Boolean>
    val sortMode: StateFlow<SortMode>
    val showRating: StateFlow<Boolean>
    val maxPriceLevel: StateFlow<Int?>
    val distanceUnit: StateFlow<DistanceUnit>

    init {
        val initial = runBlocking {
            InitialFilters(
                searchRadius = repo.searchRadius.first(),
                openNow = repo.openNow.first(),
                openIn1Hour = repo.openIn1Hour.first(),
                sortMode = repo.sortMode.first(),
                showRating = repo.showRating.first(),
                maxPriceLevel = repo.maxPriceLevel.first(),
                distanceUnit = repo.distanceUnit.first(),
            )
        }
        searchRadius = repo.searchRadius
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.searchRadius)
        openNow = repo.openNow
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.openNow)
        openIn1Hour = repo.openIn1Hour
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.openIn1Hour)
        sortMode = repo.sortMode
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.sortMode)
        showRating = repo.showRating
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.showRating)
        maxPriceLevel = repo.maxPriceLevel
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.maxPriceLevel)
        distanceUnit = repo.distanceUnit
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial.distanceUnit)
    }

    fun setSearchRadius(value: Int) =
        viewModelScope.launch { repo.saveSearchRadius(value) }

    fun setOpenNow(value: Boolean) =
        viewModelScope.launch { repo.saveOpenNow(value) }

    fun setOpenIn1Hour(value: Boolean) =
        viewModelScope.launch { repo.saveOpenIn1Hour(value) }

    fun setSortMode(value: SortMode) =
        viewModelScope.launch { repo.saveSortMode(value) }

    fun setShowRating(value: Boolean) =
        viewModelScope.launch { repo.saveShowRating(value) }

    fun setMaxPriceLevel(value: Int?) =
        viewModelScope.launch { repo.saveMaxPriceLevel(value) }

    private data class InitialFilters(
        val searchRadius: Int,
        val openNow: Boolean,
        val openIn1Hour: Boolean,
        val sortMode: SortMode,
        val showRating: Boolean,
        val maxPriceLevel: Int?,
        val distanceUnit: DistanceUnit,
    )
}
