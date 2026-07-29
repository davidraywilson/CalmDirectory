package com.example.helloworld

import android.app.Application
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.calmapps.calmmaps.R

class MainApplication : Application() {

    companion object {
        lateinit var tileStore: TileStore
            private set
    }

    override fun onCreate() {
        super.onCreate()

        val accessToken = getString(R.string.mapbox_access_token)
        MapboxOptions.accessToken = accessToken

        tileStore = TileStore.create()
    }
}