package com.example.helloworld

import android.app.Application
import com.calmapps.directory.BuildConfig
import com.mapbox.maps.MapboxOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPBOX_TOKEN.isNotEmpty()) {
            // Set the Maps SDK access token used by MapboxMap composable
            MapboxOptions.accessToken = BuildConfig.MAPBOX_TOKEN
            // Set up Navigation Core so MapboxNavigationProvider.retrieve() works
            // anywhere in the app after a lifecycle owner attaches.
            MapboxNavigationApp.setup(
                NavigationOptions.Builder(this)
                    .accessToken(BuildConfig.MAPBOX_TOKEN)
                    .build()
            )
        }
    }
}
