package com.example.helloworld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.calmapps.calmmaps.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val apiKey: StateFlow<String?> = MutableStateFlow(BuildConfig.GOOGLE_PLACES_API_KEY.ifEmpty { null })
}
