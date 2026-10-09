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
import `in`.orbitmaps.app.map.MapScreen
import `in`.orbitmaps.app.net.MapMode
import `in`.orbitmaps.app.places.PlacesViewModel
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
                            location = location,
                            centerOnMe = centerOnMe
                        )
                    },
                    places = placesModel.places,
                    online = mapMode == MapMode.Online,
                    data = data,
                    onDataChange = environment::update,
                    locationPermitted = locationPermitted,
                    onLocationPermissionResult = environment::refreshLocationPermission,
                    onCenterOnMe = { centerOnMe++ }
                )
            }
        }
    }
}
