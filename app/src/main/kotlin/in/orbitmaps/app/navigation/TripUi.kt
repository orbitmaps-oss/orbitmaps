// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import androidx.compose.runtime.Immutable
import `in`.orbitmaps.app.places.PlaceItem
import `in`.orbitmaps.app.routing.Costing
import `in`.orbitmaps.app.routing.RouteSummary
import `in`.orbitmaps.core.model.LatLon

enum class PlanFailure {
    /** The route needs map data that isn't on the phone and can't be fetched (offline or switched off). */
    NeedsData,

    /** No route between the two places, e.g. no road near one of them. */
    NoRoute,

    /** The place has no position to route to. */
    NoDestination
}

/** The route being planned for the preview sheet. */
sealed interface PlanUi {
    data object Idle : PlanUi

    data object Planning : PlanUi

    data class Ready(
        val route: NavRoute,
        val summary: RouteSummary,
        val destination: LatLon,
        val costing: Costing,
        /** Calculated from a downloaded region: no network was used. */
        val fromRegion: Boolean,
        /** Some map data couldn't be fetched, so a better route may exist. */
        val tilesIncomplete: Boolean,
        /** The built-in sample route of debug builds: always driven in simulation. */
        val sample: Boolean = false
    ) : PlanUi

    data class Failed(val reason: PlanFailure) : PlanUi
}

/** What the trip screens need from the navigation model, and the actions they can take. */
@Immutable
class TripUi(
    val plan: PlanUi = PlanUi.Idle,
    val drive: DriveUi? = null,
    /** Debug builds: simulate drives and use the sample route. */
    val debugTools: Boolean = false,
    val onPlan: (PlaceItem, Costing) -> Unit = { _, _ -> },
    val onClearPlan: () -> Unit = {},
    val onStart: (simulate: Boolean) -> Unit = {},
    val onEnd: () -> Unit = {},
    val onUseSampleRoute: () -> Unit = {}
) {
    companion object {
        val None = TripUi()
    }
}
