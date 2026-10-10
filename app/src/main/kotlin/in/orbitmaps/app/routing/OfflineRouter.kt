// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaException
import `in`.orbitmaps.app.map.InstallResult
import `in`.orbitmaps.app.map.RegionInstaller
import `in`.orbitmaps.app.map.installAsset
import `in`.orbitmaps.core.model.LatLon
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files for routing in the development sample region, bundled in debug builds only. */
object SampleRouting {
    const val TILES_ASSET = "regions/panaji-routing.tar"
    const val CONFIG_ASSET = "regions/panaji-routing.json"

    /** Installed under `filesDir`, next to the map region. */
    const val TILES_PATH = "regions/panaji-routing.tar"
    const val CONFIG_TEMPLATE_PATH = "regions/panaji-routing.json"
    const val DEVICE_CONFIG_PATH = "regions/panaji-valhalla.json"
    const val DATA_DIR = "regions/panaji-routing"
}

sealed interface RoutingSetup {
    data class Ready(val configFile: File) : RoutingSetup

    /** This build doesn't bundle routing tiles (release builds, or not downloaded yet). */
    data object NotBundled : RoutingSetup

    data object Failed : RoutingSetup
}

/**
 * Installs the sample routing tiles and writes a config with this device's paths. Runs on the IO
 * dispatcher; the tile tar is copied only after an app update.
 */
suspend fun setUpSampleRouting(context: Context): RoutingSetup {
    val tiles =
        installAsset(context, SampleRouting.TILES_ASSET, SampleRouting.TILES_PATH, RegionInstaller::hasTarHeader)
    val template = installAsset(context, SampleRouting.CONFIG_ASSET, SampleRouting.CONFIG_TEMPLATE_PATH) {
        it.length() > 0
    }
    if (tiles == InstallResult.NotBundled || template == InstallResult.NotBundled) return RoutingSetup.NotBundled
    if (tiles !is InstallResult.Installed || template !is InstallResult.Installed) return RoutingSetup.Failed
    return withContext(Dispatchers.IO) {
        try {
            val config = File(context.filesDir, SampleRouting.DEVICE_CONFIG_PATH)
            config.writeText(
                ValhallaJson.deviceConfig(
                    template.file.readText(),
                    tileExtract = tiles.file,
                    dataDir = File(context.filesDir, SampleRouting.DATA_DIR)
                )
            )
            RoutingSetup.Ready(config)
        } catch (e: IOException) {
            RoutingSetup.Failed
        } catch (e: IllegalArgumentException) {
            RoutingSetup.Failed
        }
    }
}

/**
 * On-device routing with Valhalla. Building the engine memory-maps the tile tar, so create one router,
 * reuse it for every request, and close it when done. All calls block: use a background dispatcher.
 * Nothing here logs or sends the points it is given.
 */
class OfflineRouter(configFile: File) : TripRouting.Router {
    private val valhalla = Valhalla(configFile.absolutePath)

    override fun route(from: LatLon, to: LatLon, costing: Costing, language: String?): RouteSummary {
        val response = try {
            valhalla.routeRaw(ValhallaJson.routeRequest(from, to, costing, language))
        } catch (e: ValhallaException) {
            throw RoutingException(e.message ?: "Valhalla error")
        }
        return ValhallaJson.parseRoute(response)
    }

    override fun close() = valhalla.close()
}
