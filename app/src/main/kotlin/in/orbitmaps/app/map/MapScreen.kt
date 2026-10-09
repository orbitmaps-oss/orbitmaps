// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.orbitmaps.app.Attribution
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.net.MapMode
import `in`.orbitmaps.app.net.OnlineData
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** The OSM attribution shown over the map. It must stay visible whenever map data is on screen. */
@StringRes
internal val ATTRIBUTION_TEXT = R.string.osm_attribution

private sealed interface MapState {
    data object Loading : MapState

    /** [limitToRegion]: keep the camera inside the installed region (offline mode). */
    data class Ready(val styleJson: String, val limitToRegion: Boolean) : MapState

    data object RegionMissing : MapState

    data object Failed : MapState
}

/**
 * Full-screen map: our streamed world tiles in [MapMode.Online], otherwise the installed region
 * ([SampleRegion] for now) fully offline.
 *
 * @param bottomInset extra space below the attribution, for a bottom sheet drawn over the map.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier, bottomInset: Dp = 0.dp, mode: MapMode = MapMode.Offline) {
    val context = LocalContext.current.applicationContext
    var state by remember { mutableStateOf<MapState>(MapState.Loading) }
    LaunchedEffect(mode) {
        state = when (mode) {
            MapMode.Online -> loadStreamingStyle(context)
            MapMode.Offline -> loadOfflineStyle(context)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is MapState.Ready -> OfflineMap(
                current.styleJson,
                current.limitToRegion,
                onLoadFailed = { state = MapState.Failed }
            )
            MapState.RegionMissing -> CenteredMessage(R.string.map_region_missing)
            MapState.Failed -> CenteredMessage(R.string.map_load_failed)
            MapState.Loading -> Unit
        }
        AttributionLabel(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 8.dp, bottom = 8.dp + bottomInset)
        )
    }
}

private suspend fun loadOfflineStyle(context: Context): MapState = when (val installed = installSampleRegion(context)) {
    is InstallResult.Installed -> withContext(Dispatchers.IO) {
        try {
            val template = context.assets.open(OfflineStyle.ASSET_PATH).bufferedReader().use { it.readText() }
            MapState.Ready(OfflineStyle.offlineStyleJson(template, installed.file), limitToRegion = true)
        } catch (e: IOException) {
            MapState.Failed
        } catch (e: IllegalArgumentException) {
            MapState.Failed
        }
    }
    InstallResult.NotBundled -> MapState.RegionMissing
    InstallResult.Invalid, InstallResult.Failed -> MapState.Failed
}

private suspend fun loadStreamingStyle(context: Context): MapState = withContext(Dispatchers.IO) {
    try {
        val template = context.assets.open(OfflineStyle.ASSET_PATH).bufferedReader().use { it.readText() }
        MapState.Ready(OfflineStyle.streamingStyleJson(template, OnlineData.WORLD_TILES_URL), limitToRegion = false)
    } catch (e: IOException) {
        MapState.Failed
    } catch (e: IllegalArgumentException) {
        MapState.Failed
    }
}

@Composable
private fun OfflineMap(styleJson: String, limitToRegion: Boolean, onLoadFailed: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val limiter = remember { RegionCameraLimiter() }
    val mapView = remember {
        val camera = CameraPosition.Builder()
            .target(SampleRegion.center.toLatLng())
            .zoom(SampleRegion.INITIAL_ZOOM)
            .build()
        val options = MapLibreMapOptions.createFromAttributes(context)
            .attributionEnabled(false)
            .logoEnabled(false)
            .localIdeographFontFamily("sans-serif")
            .camera(camera)
        MapView(context, options).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { onLoadFailed() }
            getMapAsync { map ->
                // The limits assume a north-up, flat map; rotating or tilting would show past the region.
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                limiter.attach(this, map)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> limiter.onViewportChanged() }
                map.addOnCameraMoveListener(limiter::onCameraMoved)
            }
        }
    }

    // Switching between online and offline keeps the same MapView and camera; only the style and
    // the region limits change.
    LaunchedEffect(mapView, styleJson, limitToRegion) {
        mapView.getMapAsync { map ->
            limiter.enabled = limitToRegion
            map.setStyle(Style.Builder().fromJson(styleJson))
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/**
 * Applies [CameraLimits] to a map showing [SampleRegion]: a minimum zoom at which the region fills the
 * view, and a camera-target area that keeps every edge of the view inside the region at the current zoom.
 * While [enabled] is false (the streamed world map) the camera can go anywhere.
 */
private class RegionCameraLimiter {
    private var view: MapView? = null
    private var map: MapLibreMap? = null
    private var widthDp = 0.0
    private var heightDp = 0.0
    private var limitedForZoom = Double.NaN

    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            val map = map ?: return
            if (value) {
                widthDp = 0.0
                heightDp = 0.0
                onViewportChanged()
            } else {
                map.setMinZoomPreference(0.0)
                map.setLatLngBoundsForCameraTarget(null)
            }
        }

    fun attach(view: MapView, map: MapLibreMap) {
        this.view = view
        this.map = map
        onViewportChanged()
    }

    fun onViewportChanged() {
        val view = view ?: return
        val map = map ?: return
        if (!enabled) return
        val density = view.resources.displayMetrics.density
        val width = view.width / density.toDouble()
        val height = view.height / density.toDouble()
        if (width <= 0 || height <= 0 || (width == widthDp && height == heightDp)) return
        widthDp = width
        heightDp = height
        val minZoom = CameraLimits.minZoom(SampleRegion.southWest, SampleRegion.northEast, width, height)
        map.setMinZoomPreference(minZoom)
        if (map.cameraPosition.zoom < minZoom) map.moveCamera(CameraUpdateFactory.zoomTo(minZoom))
        limitedForZoom = Double.NaN
        onCameraMoved()
    }

    fun onCameraMoved() {
        val map = map ?: return
        if (!enabled || widthDp <= 0) return
        val zoom = map.cameraPosition.zoom
        if (abs(zoom - limitedForZoom) < ZOOM_EPSILON) return
        limitedForZoom = zoom
        val (low, high) = CameraLimits.targetBounds(
            SampleRegion.southWest,
            SampleRegion.northEast,
            zoom,
            widthDp,
            heightDp
        )
        map.setLatLngBoundsForCameraTarget(
            LatLngBounds.from(high.latitude, high.longitude, low.latitude, low.longitude)
        )
    }

    private companion object {
        const val ZOOM_EPSILON = 1e-6
    }
}

@Composable
private fun AttributionLabel(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = stringResource(ATTRIBUTION_TEXT),
        style = MaterialTheme.typography.labelSmall,
        color = Color.Black,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .clickable(onClickLabel = stringResource(R.string.osm_attribution_open)) {
                // Opens the browser app; Orbit Maps itself makes no network request.
                try {
                    uriHandler.openUri(Attribution.OSM_COPYRIGHT_URL)
                } catch (e: ActivityNotFoundException) {
                    Unit
                } catch (e: IllegalArgumentException) {
                    Unit
                }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun CenteredMessage(@StringRes text: Int) {
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

private fun `in`.orbitmaps.core.model.LatLon.toLatLng() = LatLng(latitude, longitude)
