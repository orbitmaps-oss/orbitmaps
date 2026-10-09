// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import `in`.orbitmaps.app.map.MapScreen
import `in`.orbitmaps.app.ui.shell.AppShell
import `in`.orbitmaps.app.ui.shell.ShellViewModel
import `in`.orbitmaps.app.ui.theme.OrbitTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger

class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre's log lines can contain tile coordinates, which reveal where the user is looking.
        Logger.setVerbosity(Logger.NONE)
        MapLibre.getInstance(this)
        // Offline only: MapLibre must never try the network (the app has no INTERNET permission anyway).
        MapLibre.setConnected(false)
        enableEdgeToEdge()
        setContent {
            OrbitTheme {
                AppShell(
                    state = shell.state,
                    update = shell::update,
                    map = { bottomInset -> MapScreen(bottomInset = bottomInset) }
                )
            }
        }
    }
}
