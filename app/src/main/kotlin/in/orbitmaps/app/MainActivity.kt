// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.location.toLatLon
import `in`.orbitmaps.app.map.MapScreen
import `in`.orbitmaps.app.navigation.NavigationViewModel
import `in`.orbitmaps.app.navigation.PlanUi
import `in`.orbitmaps.app.navigation.TripUi
import `in`.orbitmaps.app.net.MapMode
import `in`.orbitmaps.app.places.PlacesViewModel
import `in`.orbitmaps.app.regions.RegionsHost
import `in`.orbitmaps.app.regions.RegionsViewModel
import `in`.orbitmaps.app.settings.EnvironmentViewModel
import `in`.orbitmaps.app.ui.shell.AppShell
import `in`.orbitmaps.app.ui.shell.ShellViewModel
import `in`.orbitmaps.app.ui.theme.OrbitTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger

class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()
    private val placesModel: PlacesViewModel by viewModels()
    private val environment: EnvironmentViewModel by viewModels()
    private val navigation: NavigationViewModel by viewModels()
    private val regionsModel: RegionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre's log lines can contain tile coordinates, which reveal where the user is looking.
        Logger.setVerbosity(Logger.NONE)
        MapLibre.getInstance(this)
        // Offline until the map mode says otherwise: MapLibre only uses the network for our world tiles.
        MapLibre.setConnected(false)
        enableEdgeToEdge()
        // Permission may change in system settings while the app is away.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) environment.refreshLocationPermission()
            }
        )
        setContent {
            val mapMode by environment.mapMode.collectAsStateWithLifecycle()
            val data by environment.settings.collectAsStateWithLifecycle()
            val network by environment.network.collectAsStateWithLifecycle()
            val locationPermitted by environment.locationPermitted.collectAsStateWithLifecycle()
            val location by environment.location.collectAsStateWithLifecycle()
            var centerOnMe by remember { mutableIntStateOf(0) }
            val plan by navigation.plan.collectAsStateWithLifecycle()
            val drive by navigation.drive.collectAsStateWithLifecycle()
            val simulatedLocation by navigation.simulatedLocation.collectAsStateWithLifecycle()
            val debugTools = remember { resources.getBoolean(R.bool.debug_tools) }
            // On-demand map data may be fetched only while the user allows it and the phone is online.
            LaunchedEffect(data.streamMap, network.online) {
                navigation.fetchAllowed = data.streamMap && network.online
            }
            // Real GPS fixes drive a real trip (a simulated one makes its own).
            LaunchedEffect(location) { location?.let(navigation::onLocation) }
            val trip = TripUi(
                plan = plan,
                drive = drive,
                debugTools = debugTools,
                onPlan = { place, costing ->
                    // From where the user is, or from the map centre when location is off.
                    navigation.plan(location?.toLatLon() ?: placesModel.places.center, place, costing)
                },
                onClearPlan = navigation::clearPlan,
                onStart = navigation::start,
                onEnd = navigation::end,
                onUseSampleRoute = navigation::useSampleRoute
            )
            val routeLine = drive?.route?.shape ?: (plan as? PlanUi.Ready)?.route?.shape
            // Downloads follow the network and the user's Wi-Fi-only switch.
            LaunchedEffect(network, data.wifiOnlyDownloads) {
                regionsModel.online = network.online
                regionsModel.unmetered = network.unmetered
                regionsModel.wifiOnly = data.wifiOnlyDownloads
            }
            val regionsUi by regionsModel.ui.collectAsStateWithLifecycle()
            val installedRegions by regionsModel.installed.collectAsStateWithLifecycle()
            LaunchedEffect(installedRegions) {
                placesModel.places.packs = installedRegions
                navigation.packs = installedRegions
            }
            val regions = RegionsHost(
                ui = regionsUi,
                onRefresh = regionsModel::refresh,
                onDownload = regionsModel::download,
                onCancel = regionsModel::cancel,
                onDelete = regionsModel::delete
            )
            LaunchedEffect(mapMode) { MapLibre.setConnected(mapMode == MapMode.Online) }
            LaunchedEffect(mapMode, network) {
                placesModel.places.onlineAllowed = mapMode == MapMode.Online
                placesModel.places.unmetered = network.unmetered
            }
            OrbitTheme {
                AppShell(
                    state = shell.state,
                    update = shell::update,
                    map = { bottomInset ->
                        MapScreen(
                            bottomInset = bottomInset,
                            mode = mapMode,
                            onCenterChange = { placesModel.places.center = it },
                            location = simulatedLocation ?: location,
                            centerOnMe = centerOnMe,
                            routeLine = routeLine,
                            follow = drive != null,
                            regions = installedRegions
                        )
                    },
                    places = placesModel.places,
                    online = mapMode == MapMode.Online,
                    data = data,
                    onDataChange = environment::update,
                    locationPermitted = locationPermitted,
                    onLocationPermissionResult = environment::refreshLocationPermission,
                    onCenterOnMe = { centerOnMe++ },
                    trip = trip,
                    regions = regions
                )
            }
        }
    }
}
