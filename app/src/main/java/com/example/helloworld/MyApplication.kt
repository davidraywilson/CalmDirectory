package com.example.helloworld

import android.app.Application
import com.calmapps.calmmaps.R
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val accessToken = getString(R.string.mapbox_access_token)
        com.mapbox.common.MapboxOptions.accessToken = accessToken

//        if (!MapboxNavigationApp.isSetup()) {
//            MapboxNavigationApp.setup(
//                NavigationOptions.Builder(this)
//                    .build()
//            )
//        }
    }
}
