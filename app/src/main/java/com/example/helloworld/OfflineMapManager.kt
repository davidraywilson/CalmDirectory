package com.example.helloworld

import android.content.Context
import android.util.Log
import com.mapbox.bindgen.Value
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.common.TileStoreOptions
import com.mapbox.geojson.Geometry
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

data class OfflineRegionItem(
    val id: String,
    val name: String,
    val status: RegionStatus,
    val progress: Int = 0
)

enum class RegionStatus {
    DOWNLOADING,
    COMPLETED,
    ERROR,
    UNKNOWN
}

class OfflineMapManager(
    private val context: Context,
    private val tileStore: TileStore = MainApplication.tileStore
) {

    init {
        tileStore.setOption(TileStoreOptions.DISK_QUOTA, Value(500L * 1024 * 1024))
    }

    private val offlineManager: OfflineManager = OfflineManager()
    private val prefs = context.getSharedPreferences("offline_regions_metadata", Context.MODE_PRIVATE)

    fun getOfflineRegions(): Flow<List<OfflineRegionItem>> = callbackFlow {
        val callback = {
            tileStore.getAllTileRegions { expected ->
                if (expected.isValue) {
                    val regions = expected.value?.map { region ->
                        val name = prefs.getString(region.id, "Offline Region ${region.id.take(4)}") ?: "Unnamed Region"

                        val status = if (region.requiredResourceCount > 0 && region.completedResourceCount >= region.requiredResourceCount) {
                            RegionStatus.COMPLETED
                        } else {
                            RegionStatus.DOWNLOADING
                        }

                        OfflineRegionItem(
                            id = region.id,
                            name = name,
                            status = status
                        )
                    } ?: emptyList()
                    trySend(regions)
                } else {
                    Log.e("OfflineMapManager", "Error fetching regions: ${expected.error}")
                    trySend(emptyList())
                }
            }
        }

        callback()
        awaitClose { }
    }

    fun downloadRegion(
        regionName: String,
        geometry: Geometry,
        zoomMin: Byte = 0,
        zoomMax: Byte = 16,
        onProgress: (Int) -> Unit,
        onCompletion: (Result<String>) -> Unit
    ) {
        val regionId = UUID.randomUUID().toString()

        prefs.edit().putString(regionId, regionName).apply()

        val tilesetDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.MAPBOX_STREETS)
                .minZoom(zoomMin)
                .maxZoom(zoomMax)
                .build()
        )

        val stylePackOptions = StylePackLoadOptions.Builder()
            .glyphsRasterizationMode(com.mapbox.maps.GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
            .build()

        offlineManager.loadStylePack(
            Style.MAPBOX_STREETS,
            stylePackOptions,
            { /* Progress */ },
            { expected ->
                if (expected.isError) {
                    Log.e("OfflineMapManager", "Style pack download failed: ${expected.error}")
                }
            }
        )

        val loadOptions = TileRegionLoadOptions.Builder()
            .geometry(geometry)
            .descriptors(listOf(tilesetDescriptor))
            .acceptExpired(true)
            .networkRestriction(com.mapbox.common.NetworkRestriction.NONE)
            .build()

        tileStore.loadTileRegion(
            regionId,
            loadOptions,
            { progress ->
                val percent = if (progress.requiredResourceCount > 0) {
                    (100.0 * progress.completedResourceCount / progress.requiredResourceCount).toInt()
                } else {
                    0
                }
                onProgress(percent)
            },
            { expected ->
                if (expected.isValue) {
                    onCompletion(Result.success(regionId))
                } else {
                    prefs.edit().remove(regionId).apply()
                    onCompletion(Result.failure(Exception(expected.error?.message ?: "Unknown error")))
                }
            }
        )
    }

    fun deleteRegion(id: String, onResult: (Boolean) -> Unit) {
        tileStore.removeTileRegion(id) { expected ->
            if (expected.isValue) {
                prefs.edit().remove(id).apply()
            }
            onResult(expected.isValue)
        }
    }
}