// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.orbitmaps.app.net.MapMode
import `in`.orbitmaps.app.net.NetworkStatus
import `in`.orbitmaps.app.net.chooseMapMode
import `in`.orbitmaps.app.net.networkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
class EnvironmentViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppSettings(application)
    val settings: StateFlow<DataSettings> = store.settings

    val network: StateFlow<NetworkStatus> =
        networkStatus(
            application
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NetworkStatus.Offline)

    val mapMode: StateFlow<MapMode> = combine(settings, network) { s, n -> chooseMapMode(s.streamMap, n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapMode.Offline)

    fun update(change: DataSettings.() -> DataSettings) = store.update(change)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
