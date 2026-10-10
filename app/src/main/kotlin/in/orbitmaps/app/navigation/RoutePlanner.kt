// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import android.app.Application
import `in`.orbitmaps.app.map.SampleRegion
import `in`.orbitmaps.app.regions.InstalledRegion
import `in`.orbitmaps.app.regions.RegionPicker
import `in`.orbitmaps.app.regions.RegionRouting
import `in`.orbitmaps.app.routing.Costing
import `in`.orbitmaps.app.routing.FetchReport
import `in`.orbitmaps.app.routing.OfflineRouter
import `in`.orbitmaps.app.routing.RouteSummary
import `in`.orbitmaps.app.routing.RoutingException
import `in`.orbitmaps.app.routing.RoutingSetup
import `in`.orbitmaps.app.routing.TripPlan
import `in`.orbitmaps.app.routing.TripRouting
import `in`.orbitmaps.app.routing.setUpSampleRouting
import `in`.orbitmaps.core.model.LatLon
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PlanOutcome {
    /** [tilesIncomplete]: some map data couldn't be fetched, so the route may be a worse one. */
    data class Ok(val summary: RouteSummary, val tilesIncomplete: Boolean, val fromRegion: Boolean) : PlanOutcome

    /** The route needs map data that isn't on the phone and can't be fetched now. */
    data object NeedsData : PlanOutcome

    data object NoRoute : PlanOutcome
}

/**
 * Finds routes on the phone: from a downloaded region ([packs], or the bundled sample) when both ends are inside it (no network),
 * otherwise from tiles fetched on demand ([TripRouting]) when [fetchAllowed]. The engine isn't
 * thread-safe, so plans run one at a time. Positions are never logged.
 */
class RoutePlanner(private val application: Application, private val fetchAllowed: () -> Boolean) : Closeable {
    private val trips = TripRouting(TripRouting.defaultRoot(application.filesDir))
    private val mutex = Mutex()
    private var regionChecked = false
    private var region: OfflineRouter? = null
    private val packRouters = mutableMapOf<String, OfflineRouter>()
    private val brokenPacks = mutableSetOf<String>()

    /** The downloaded regions with routing data; a route inside one needs no network at all. */
    @Volatile var packs: List<InstalledRegion> = emptyList()

    /** The engine of a downloaded pack, built on first use; null if it has none or can't be opened. */
    private fun packRouter(pack: InstalledRegion): OfflineRouter? {
        packRouters[pack.id]?.let { return it }
        if (pack.id in brokenPacks) return null
        val router = RegionRouting.configFor(pack)?.let {
            try {
                OfflineRouter(it)
            } catch (e: RuntimeException) {
                null
            }
        }
        if (router == null) brokenPacks += pack.id else packRouters[pack.id] = router
        return router
    }

    /** Closes engines of packs that were deleted, so their files can go. */
    private fun dropRemovedPacks(current: List<InstalledRegion>) {
        val ids = current.map { it.id }.toSet()
        packRouters.keys.filterNot { it in ids }.forEach { packRouters.remove(it)?.close() }
        brokenPacks.retainAll(ids)
    }

    /** The bundled sample region's engine, built once; null in builds that don't bundle one. */
    private suspend fun regionRouter(): OfflineRouter? {
        if (!regionChecked) {
            regionChecked = true
            val setup = setUpSampleRouting(application)
            if (setup is RoutingSetup.Ready) {
                region = try {
                    OfflineRouter(setup.configFile)
                } catch (e: RuntimeException) {
                    null
                }
            }
        }
        return region
    }

    suspend fun plan(from: LatLon, to: LatLon, costing: Costing, language: String): PlanOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = packs
            dropRemovedPacks(current)
            val pack = RegionPicker.forRoute(current, from, to)
            val inSample = SampleRegion.contains(from) && SampleRegion.contains(to)
            val local = pack?.let(::packRouter) ?: if (inSample) regionRouter() else null
            if (local != null) {
                return@withContext try {
                    PlanOutcome.Ok(
                        local.route(from, to, costing, language),
                        tilesIncomplete = false,
                        fromRegion = true
                    )
                } catch (e: RoutingException) {
                    PlanOutcome.NoRoute
                }
            }
            when (val plan = trips.plan(from, to, costing, language, fetch = fetchAllowed())) {
                is TripPlan.Ready -> PlanOutcome.Ok(plan.route, !plan.tiles.complete, fromRegion = false)
                TripPlan.NoEngine -> PlanOutcome.NeedsData
                is TripPlan.NoRoute -> if (plan.tiles.failed > 0) PlanOutcome.NeedsData else PlanOutcome.NoRoute
            }
        }
    }

    /**
     * Saves the tiles along a started trip so it continues without signal. Only when fetching is
     * allowed and the route didn't come from a downloaded region. Runs beside planning (it only
     * writes tile files) and never throws.
     */
    suspend fun saveCorridor(route: RouteSummary): FetchReport? {
        if (!fetchAllowed()) return null
        return withContext(Dispatchers.IO) { trips.saveCorridor(route) }
    }

    override fun close() {
        trips.close()
        region?.close()
        region = null
        packRouters.values.forEach { it.close() }
        packRouters.clear()
    }
}
