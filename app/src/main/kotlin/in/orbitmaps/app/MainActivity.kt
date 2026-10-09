// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        setContent {
            val mapMode by environment.mapMode.collectAsStateWithLifecycle()
            val data by environment.settings.collectAsStateWithLifecycle()
            LaunchedEffect(mapMode) { MapLibre.setConnected(mapMode == MapMode.Online) }
            OrbitTheme {
                AppShell(
                    state = shell.state,
                    update = shell::update,
                    map = { bottomInset -> MapScreen(bottomInset = bottomInset, mode = mapMode) },
                    places = placesModel.places,
                    online = mapMode == MapMode.Online,
                    data = data,
                    onDataChange = environment::update
                )
            }
        }
    }
}
