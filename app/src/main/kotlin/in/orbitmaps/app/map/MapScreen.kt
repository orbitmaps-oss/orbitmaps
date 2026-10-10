// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.location.Location
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import `in`.orbitmaps.app.location.DeviceLocation
import `in`.orbitmaps.app.net.MapMode
import `in`.orbitmaps.app.net.OnlineData
import `in`.orbitmaps.app.regions.InstalledRegion
import `in`.orbitmaps.app.regions.PackRole
import `in`.orbitmaps.app.regions.RegionBounds
import `in`.orbitmaps.app.regions.RegionPicker
import `in`.orbitmaps.core.model.LatLon
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** The OSM attribution shown over the map. It must stay visible whenever map data is on screen. */
@StringRes
internal val ATTRIBUTION_TEXT = R.string.osm_attribution

private sealed interface MapState {
    data object Loading : MapState

    /** [limitTo]: keep the camera inside this region (offline mode); null for the streamed world map. */
    data class Ready(val styleJson: String, val limitTo: RegionBounds?) : MapState

    data object RegionMissing : MapState

    data object Failed : MapState
}

/**
 * Full-screen map: our streamed world tiles in [MapMode.Online], otherwise a downloaded region
 * ([regions], or the bundled sample region in debug builds) fully offline.
 *
 * @param regions the downloaded regions; offline, the one containing the map centre is shown.
 * @param bottomInset extra space below the attribution, for a bottom sheet drawn over the map.
 * @param onCenterChange called with the map centre when the camera stops moving (stays on the phone).
 * @param location the user's position for the blue dot, or null (no permission or no fix yet).
 * @param centerOnMe each time this number changes, the camera moves to [location].
 * @param routeLine the route to draw, or null; while not [follow]ing, the camera fits it.
 * @param follow keep the camera on the user's position (while navigating).
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    mode: MapMode = MapMode.Offline,
    onCenterChange: (LatLon) -> Unit = {},
    location: Location? = null,
    centerOnMe: Int = 0,
    routeLine: List<LatLon>? = null,
    follow: Boolean = false,
    regions: List<InstalledRegion> = emptyList()
) {
    val context = LocalContext.current.applicationContext
    val bottomInsetPx = with(LocalDensity.current) { bottomInset.roundToPx() }
    var state by remember { mutableStateOf<MapState>(MapState.Loading) }
    // If our server can't be reached, fall back to downloaded data for the rest of the session.
    var streamingFailed by remember { mutableStateOf(false) }
    val effectiveMode = if (streamingFailed) MapMode.Offline else mode
    // The downloaded region shown offline: it follows the map centre but stays put while it contains it.
    var centre by remember { mutableStateOf<LatLon?>(null) }
    var shownRegionId by remember { mutableStateOf<String?>(null) }
    val region = RegionPicker.forMap(regions, centre, shownRegionId)
    shownRegionId = region?.id
    val offlineKey = if (effectiveMode == MapMode.Offline) region?.id to region?.manifest?.built else null
    LaunchedEffect(effectiveMode, offlineKey) {
        state = when (effectiveMode) {
            MapMode.Online -> loadStreamingStyle(context)
            MapMode.Offline -> if (region != null) loadPackStyle(context, region) else loadOfflineStyle(context)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is MapState.Ready -> OfflineMap(
                current.styleJson,
                current.limitTo,
                location = location,
                centerOnMe = centerOnMe,
                routeLine = routeLine,
                follow = follow,
                bottomInsetPx = bottomInsetPx,
                onCenterChange = {
                    centre = it
                    onCenterChange(it)
                },
                onLoadFailed = {
                    if (current.limitTo != null) state = MapState.Failed else streamingFailed = true
                }
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
            MapState.Ready(OfflineStyle.offlineStyleJson(template, installed.file), limitTo = SampleRegion.bounds)
        } catch (e: IOException) {
            MapState.Failed
        } catch (e: IllegalArgumentException) {
            MapState.Failed
        }
    }
    InstallResult.NotBundled -> MapState.RegionMissing
    InstallResult.Invalid, InstallResult.Failed -> MapState.Failed
}

private suspend fun loadPackStyle(context: Context, region: InstalledRegion): MapState = withContext(Dispatchers.IO) {
    try {
        val template = context.assets.open(OfflineStyle.ASSET_PATH).bufferedReader().use { it.readText() }
        val file = region.file(PackRole.Map)
        MapState.Ready(OfflineStyle.offlineStyleJson(template, file), limitTo = region.manifest.bounds)
    } catch (e: IOException) {
        MapState.Failed
    } catch (e: IllegalArgumentException) {
        MapState.Failed
    }
}

private suspend fun loadStreamingStyle(context: Context): MapState = withContext(Dispatchers.IO) {
    try {
        val template = context.assets.open(OfflineStyle.ASSET_PATH).bufferedReader().use { it.readText() }
        MapState.Ready(OfflineStyle.streamingStyleJson(template, OnlineData.WORLD_TILES_URL), limitTo = null)
    } catch (e: IOException) {
        MapState.Failed
    } catch (e: IllegalArgumentException) {
        MapState.Failed
    }
}

@Composable
private fun OfflineMap(
    styleJson: String,
    limitTo: RegionBounds?,
    location: Location?,
    centerOnMe: Int,
    routeLine: List<LatLon>?,
    follow: Boolean,
    bottomInsetPx: Int,
    onCenterChange: (LatLon) -> Unit,
    onLoadFailed: () -> Unit
) {
    val currentOnCenterChange = rememberUpdatedState(onCenterChange)
    val currentLine = rememberUpdatedState(routeLine)
    val currentFollow = rememberUpdatedState(follow)
    val hasLocation = rememberUpdatedState(location != null)
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
                map.addOnCameraIdleListener {
                    val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
                    LatLon.orNull(target.latitude, target.longitude)?.let { currentOnCenterChange.value(it) }
                }
            }
        }
    }

    // Switching between online and offline keeps the same MapView and camera; only the style and
    // the region limits change.
    LaunchedEffect(mapView, styleJson, limitTo) {
        mapView.getMapAsync { map ->
            val target = map.cameraPosition.target
            val inside = target != null &&
                LatLon.orNull(target.latitude, target.longitude)?.let { limitTo?.contains(it) } == true
            if (limitTo != null && !inside) {
                // A downloaded region elsewhere: start from its middle instead of being clamped to its edge.
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(limitTo.center.toLatLng(), SampleRegion.INITIAL_ZOOM))
            }
            limiter.bounds = limitTo
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                showLocation(context, map, style, hasLocation.value)
                showRoute(style, currentLine.value)
                applyFollow(map, currentFollow.value)
            }
        }
    }

    // The route line, and (until navigation starts) a camera that fits all of it above the sheet.
    LaunchedEffect(mapView, routeLine) {
        mapView.getMapAsync { map ->
            val style = map.style ?: return@getMapAsync
            showRoute(style, routeLine)
            if (routeLine != null && routeLine.size >= 2 && !currentFollow.value) {
                val bounds = LatLngBounds.Builder().includes(
                    routeLine.map {
                        LatLng(it.latitude, it.longitude)
                    }
                ).build()
                map.easeCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        FIT_SIDE_PX,
                        FIT_TOP_PX,
                        FIT_SIDE_PX,
                        bottomInsetPx + FIT_SIDE_PX
                    ),
                    FIT_MS
                )
            }
        }
    }

    // While navigating, the camera stays on the user, north-up.
    LaunchedEffect(mapView, follow) {
        mapView.getMapAsync { map -> applyFollow(map, follow) }
    }

    // The blue dot follows our own location updates; MapLibre's location engine stays off.
    LaunchedEffect(mapView, location) {
        val fix = location ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            val style = map.style ?: return@getMapAsync
            if (!map.locationComponent.isLocationComponentActivated) {
                showLocation(context, map, style, hasLocation = true)
                applyFollow(map, currentFollow.value)
            }
            if (map.locationComponent.isLocationComponentActivated) map.locationComponent.forceLocationUpdate(fix)
        }
    }

    LaunchedEffect(mapView, centerOnMe) {
        if (centerOnMe == 0) return@LaunchedEffect
        val fix = location ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            val zoom = maxOf(map.cameraPosition.zoom, CENTER_ON_ME_ZOOM)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude, fix.longitude), zoom))
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

private const val CENTER_ON_ME_ZOOM = 15.0
private const val FOLLOW_ZOOM = 16.5
private const val FIT_SIDE_PX = 80
private const val FIT_TOP_PX = 220
private const val FIT_MS = 600
private const val ROUTE_SOURCE = "orbit-route"
private const val ROUTE_CASING = "orbit-route-casing"
private const val ROUTE_LINE = "orbit-route-line"

/**
 * Shows the blue dot (with heading) once there is a position to show: [hasLocation], which is true
 * when location is allowed and a fix arrived, or while a drive is simulated (debug builds).
 */
private fun showLocation(context: Context, map: MapLibreMap, style: Style, hasLocation: Boolean) {
    if (!hasLocation && !DeviceLocation.isPermitted(context)) return
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(
            LocationComponentActivationOptions.Builder(context, style).useDefaultLocationEngine(false).build()
        )
    }
    @Suppress("MissingPermission") // our own updates only; MapLibre's location engine is off
    component.isLocationComponentEnabled = true
    component.cameraMode = CameraMode.NONE
    component.renderMode = RenderMode.COMPASS
}

/** North-up tracking of the user's position while navigating, free camera otherwise. */
private fun applyFollow(map: MapLibreMap, follow: Boolean) {
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) return
    component.cameraMode = if (follow) CameraMode.TRACKING else CameraMode.NONE
    component.renderMode = if (follow) RenderMode.GPS else RenderMode.COMPASS
    if (follow) component.zoomWhileTracking(FOLLOW_ZOOM)
}

/** Draws [line] as a green route with a dark casing, or clears it when null. The layers are added once. */
private fun showRoute(style: Style, line: List<LatLon>?) {
    val features = if (line != null && line.size >= 2) {
        listOf(Feature.fromGeometry(LineString.fromLngLats(line.map { Point.fromLngLat(it.longitude, it.latitude) })))
    } else {
        emptyList()
    }
    val data = FeatureCollection.fromFeatures(features)
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
    if (source != null) {
        source.setGeoJson(data)
        return
    }
    style.addSource(GeoJsonSource(ROUTE_SOURCE, data))
    style.addLayer(
        LineLayer(ROUTE_CASING, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#064E3B"),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(ROUTE_LINE, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#22C55E"),
            PropertyFactory.lineWidth(5.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
}

/**
 * Applies [CameraLimits] to a map showing a downloaded region: a minimum zoom at which the region fills
 * the view, and a camera-target area that keeps every edge of the view inside the region at the current
 * zoom. While [bounds] is null (the streamed world map) the camera can go anywhere.
 */
private class RegionCameraLimiter {
    private var view: MapView? = null
    private var map: MapLibreMap? = null
    private var widthDp = 0.0
    private var heightDp = 0.0
    private var limitedForZoom = Double.NaN

    var bounds: RegionBounds? = null
        set(value) {
            if (field == value) return
            field = value
            val map = map ?: return
            if (value != null) {
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
        val bounds = bounds ?: return
        val density = view.resources.displayMetrics.density
        val width = view.width / density.toDouble()
        val height = view.height / density.toDouble()
        if (width <= 0 || height <= 0 || (width == widthDp && height == heightDp)) return
        widthDp = width
        heightDp = height
        val minZoom = CameraLimits.minZoom(bounds.southWest, bounds.northEast, width, height)
        map.setMinZoomPreference(minZoom)
        if (map.cameraPosition.zoom < minZoom) map.moveCamera(CameraUpdateFactory.zoomTo(minZoom))
        limitedForZoom = Double.NaN
        onCameraMoved()
    }

    fun onCameraMoved() {
        val map = map ?: return
        val bounds = bounds ?: return
        if (widthDp <= 0) return
        val zoom = map.cameraPosition.zoom
        if (abs(zoom - limitedForZoom) < ZOOM_EPSILON) return
        limitedForZoom = zoom
        val (low, high) = CameraLimits.targetBounds(
            bounds.southWest,
            bounds.northEast,
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
