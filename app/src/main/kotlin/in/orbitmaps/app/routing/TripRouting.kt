// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import `in`.orbitmaps.app.net.Download
import `in`.orbitmaps.app.net.HttpFiles
import `in`.orbitmaps.app.net.OnlineData
import `in`.orbitmaps.core.model.LatLon
import java.io.Closeable
import java.io.File
import java.io.IOException

sealed interface TripPlan {
    /** [tiles] reports what had to be downloaded; a route can be found even if some tiles failed. */
    data class Ready(val route: RouteSummary, val tiles: FetchReport) : TripPlan

    /** The engine's config couldn't be fetched (offline on first use) or read. */
    data object NoEngine : TripPlan

    /** No route: [reason] from Valhalla, e.g. a point far from any road, or tiles still missing offline. */
    data class NoRoute(val reason: String, val tiles: FetchReport) : TripPlan
}

/**
 * Routing anywhere, on the phone: downloads only the graph tiles a trip needs from our static host
 * ([OnlineData.ROUTING_TILES_URL]), then routes with valhalla-mobile. [saveCorridor] keeps the tiles
 * along a started trip, so the trip and rerouting continue without signal. The host learns which
 * tiles were fetched (the rough corridor), never the start, destination or route. Blocking: call off
 * the main thread.
 *
 * @param root this routing data's folder, e.g. filesDir/routing/valhalla-3.6.3.
 */
class TripRouting(
    private val root: File,
    private val baseUrl: String = OnlineData.ROUTING_TILES_URL,
    private val engine: (File) -> Router = { config -> OfflineRouter(config) }
) : Closeable {
    /** The routing part of [OfflineRouter], so tests can replace the native engine. */
    interface Router : Closeable {
        fun route(from: LatLon, to: LatLon, costing: Costing = Costing.Car, language: String? = null): RouteSummary

        override fun close() = Unit
    }

    val tiles = TileStore(File(root, "tiles"), baseUrl)
    private val template = File(root, "valhalla-template.json")
    private val config = File(root, "valhalla.json")
    private var router: Router? = null

    /** The engine, built once from the server's config (fetched on first use and kept). */
    private fun router(allowFetch: Boolean): Router? {
        router?.let { return it }
        if (!template.isFile) {
            if (!allowFetch) return null
            if (HttpFiles.download("$baseUrl/valhalla.json", template) !is Download.Saved) return null
        }
        return try {
            config.writeText(ValhallaJson.tileDirConfig(template.readText(), tiles.dir, File(root, "data")))
            engine(config).also { router = it }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            template.delete() // a damaged template is fetched again next time
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * @param fetch false uses only what is on the phone (no network); tiles that are missing are
     *   counted as [FetchReport.failed], so a failed route can be told apart from "no road there".
     */
    fun plan(
        from: LatLon,
        to: LatLon,
        costing: Costing = Costing.Car,
        language: String? = null,
        fetch: Boolean = true
    ): TripPlan {
        val needed = TripTiles.forPlanning(from, to)
        val report = if (fetch) {
            tiles.ensure(needed)
        } else {
            FetchReport(downloaded = 0, alreadyHere = 0, absent = 0, failed = tiles.missing(needed).size, bytes = 0)
        }
        val router = router(allowFetch = fetch) ?: return TripPlan.NoEngine
        return try {
            TripPlan.Ready(router.route(from, to, costing, language), report)
        } catch (e: RoutingException) {
            TripPlan.NoRoute(e.message ?: "no route", report)
        }
    }

    /** Downloads the tiles along [route], so the trip can continue offline. */
    fun saveCorridor(route: RouteSummary): FetchReport = tiles.ensure(TripTiles.forCorridor(route.shape))

    override fun close() {
        router?.close()
        router = null
    }

    companion object {
        /** The folder for the routing data that matches valhalla-mobile's Valhalla version. */
        fun defaultRoot(filesDir: File) = File(filesDir, "routing/valhalla-3.6.3")
    }
}
