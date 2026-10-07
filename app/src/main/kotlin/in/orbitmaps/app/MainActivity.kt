// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import `in`.orbitmaps.app.map.MapScreen
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre's log lines can contain tile coordinates, which reveal where the user is looking.
        Logger.setVerbosity(Logger.NONE)
        MapLibre.getInstance(this)
        // Offline only: MapLibre must never try the network (the app has no INTERNET permission anyway).
        MapLibre.setConnected(false)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MapScreen()
            }
        }
    }
}
