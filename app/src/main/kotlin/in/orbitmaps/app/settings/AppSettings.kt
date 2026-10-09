// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.orbitmaps.app.location.DeviceLocation
import `in`.orbitmaps.app.net.HttpFiles
import `in`.orbitmaps.app.net.MapMode
import `in`.orbitmaps.app.net.NetworkStatus
import `in`.orbitmaps.app.net.OnlineData
import `in`.orbitmaps.app.net.PMTILES_MAGIC
import `in`.orbitmaps.app.net.chooseMapMode
import `in`.orbitmaps.app.net.networkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/** The user's data switches (window 18), stored on the phone only and excluded from backups. */
data class DataSettings(
    /** Stream map tiles for areas that aren't downloaded (PRIVACY.md: "Browse undownloaded areas"). */
    val streamMap: Boolean = true,
    /**
     * Download region packs and their daily updates only on unmetered networks. Tiles for a trip being
     * planned or driven are small and always fetched, so routing works on mobile data.
     */
    val wifiOnlyDownloads: Boolean = true
)

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<DataSettings> = state.asStateFlow()

    private fun read() = DataSettings(
        streamMap = prefs.getBoolean(KEY_STREAM_MAP, DataSettings().streamMap),
        wifiOnlyDownloads = prefs.getBoolean(KEY_WIFI_ONLY, DataSettings().wifiOnlyDownloads)
    )

    fun update(change: DataSettings.() -> DataSettings) {
        val next = state.value.change()
        prefs.edit {
            putBoolean(KEY_STREAM_MAP, next.streamMap)
            putBoolean(KEY_WIFI_ONLY, next.wifiOnlyDownloads)
        }
        state.value = next
    }

    private companion object {
        const val FILE = "data_settings"
        const val KEY_STREAM_MAP = "stream_map"
        const val KEY_WIFI_ONLY = "wifi_only_downloads"
    }
}

/** Settings, network status and the resulting map mode, kept across configuration changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppSettings(application)
    val settings: StateFlow<DataSettings> = store.settings

    val network: StateFlow<NetworkStatus> =
        networkStatus(
            application
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NetworkStatus.Offline)

    /**
     * Whether our server has the world map, checked (first bytes only) when streaming is allowed and
     * the network changes, and again every [RECHECK_MS] while it isn't.
     */
    val serverReady: StateFlow<Boolean> = combine(settings, network) { s, n -> s.streamMap && n.online }
        .distinctUntilChanged()
        .transformLatest { usable ->
            emit(false)
            while (usable) {
                val ready =
                    withContext(Dispatchers.IO) { HttpFiles.startsWith(OnlineData.WORLD_TILES_URL, PMTILES_MAGIC) }
                emit(ready)
                if (ready) break
                delay(RECHECK_MS)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val mapMode: StateFlow<MapMode> = combine(settings, network, serverReady) { s, n, ready ->
        chooseMapMode(s.streamMap, n, ready)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapMode.Offline)

    fun update(change: DataSettings.() -> DataSettings) = store.update(change)

    private val permitted = MutableStateFlow(DeviceLocation.isPermitted(application))

    /** Whether the user allowed location; re-read after the permission dialog and when the app resumes. */
    val locationPermitted: StateFlow<Boolean> = permitted.asStateFlow()

    fun refreshLocationPermission() {
        permitted.value = DeviceLocation.isPermitted(getApplication())
    }

    /**
     * The latest fix while the app is visible and location is allowed, else null. Kept in memory only
     * (never logged, stored or sent).
     */
    val location: StateFlow<Location?> = permitted
        .flatMapLatest { allowed -> if (allowed) DeviceLocation.updates(getApplication()) else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECHECK_MS = 10 * 60 * 1000L
    }
}
