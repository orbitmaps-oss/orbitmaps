// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Where the app's static files live. The only host the app fetches from (config/network-hosts.txt,
 * PRIVACY.md). Paths are versioned so the layout can change without breaking older app versions.
 * The hosting layout is described in docs/HOSTING.md.
 */
object OnlineData {
    const val BASE_URL = "https://data.orbitmaps.in/v1"

    /** The whole world's map tiles as one PMTiles archive; MapLibre reads it with HTTP range requests. */
    const val WORLD_TILES_URL = "$BASE_URL/tiles/world.pmtiles"

    /** Valhalla graph tiles, one file per tile; the version must match valhalla-mobile's Valhalla. */
    const val ROUTING_TILES_URL = "$BASE_URL/routing/valhalla-3.6.3"

    /** Search shards, one small SQLite FTS5 index per area. */
    const val SEARCH_SHARDS_URL = "$BASE_URL/search/v2"

    fun isOurs(url: String): Boolean = url.startsWith("$BASE_URL/")
}

/** Whether the phone can reach the internet, and whether that connection is unmetered (Wi-Fi). */
data class NetworkStatus(val online: Boolean, val unmetered: Boolean) {
    companion object {
        val Offline = NetworkStatus(online = false, unmetered = false)
    }
}

/** How the map gets its tiles. */
enum class MapMode {
    /** Our world tiles, streamed and cached by MapLibre; the camera can go anywhere. */
    Online,

    /** Only downloaded data: the camera stays inside the installed region. */
    Offline
}

/** Streams the world map when the user allows it and the phone is online; otherwise downloaded data only. */
fun chooseMapMode(streamAllowed: Boolean, network: NetworkStatus): MapMode =
    if (streamAllowed && network.online) MapMode.Online else MapMode.Offline

/** The default network's status, updated as it changes. Needs ACCESS_NETWORK_STATE. */
fun networkStatus(context: Context): Flow<NetworkStatus> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    fun status(capabilities: NetworkCapabilities?) = if (capabilities == null) {
        NetworkStatus.Offline
    } else {
        NetworkStatus(
            online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        )
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(status(capabilities))
        }

        override fun onLost(network: Network) {
            trySend(NetworkStatus.Offline)
        }
    }
    trySend(status(manager.getNetworkCapabilities(manager.activeNetwork)))
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
