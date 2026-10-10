// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import android.app.Application
import `in`.orbitmaps.app.map.SampleRegion
import `in`.orbitmaps.app.routing.Costing
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
 * Finds routes on the phone: from a downloaded region when both ends are inside it (no network),
 * otherwise from tiles fetched on demand ([TripRouting]) when [fetchAllowed]. The engine isn't
 * thread-safe, so plans run one at a time. Positions are never logged.
 */
class RoutePlanner(private val application: Application, private val fetchAllowed: () -> Boolean) : Closeable {
    private val trips = TripRouting(TripRouting.defaultRoot(application.filesDir))
    private val mutex = Mutex()
    private var regionChecked = false
    private var region: OfflineRouter? = null

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
            val inRegion = SampleRegion.contains(from) && SampleRegion.contains(to)
            val local = if (inRegion) regionRouter() else null
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

    override fun close() {
        trips.close()
        region?.close()
        region = null
    }
}
