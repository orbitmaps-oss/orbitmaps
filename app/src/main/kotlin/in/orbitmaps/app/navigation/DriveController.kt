// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.core.model.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the driving screens show; null [nav] until the first fix. */
data class DriveUi(
    val route: NavRoute,
    val nav: NavState? = null,
    val rerouting: Boolean = false,
    /** A reroute was needed but no new route could be found (offline without tiles, no road nearby). */
    val rerouteFailed: Boolean = false,
    val fix: Fix? = null,
    val simulated: Boolean = false,
    /** The latest on-route state, so the screen still has a step and a time left while off route. */
    val lastOnRoute: NavState.OnRoute? = null
)

/**
 * Runs one trip: feeds fixes to [TripNavigator], decides what to say, and reroutes when the driver
 * leaves the route. One fix at a time ([onFix] is suspending, so a reroute holds the next fixes back,
 * and the caller conflates them). Nothing here logs positions.
 *
 * @param reroute returns a new route from the given position to the destination, or null.
 * @param speak receives each announcement once.
 * @param now milliseconds clock, injectable for tests.
 */
class DriveController(
    initial: NavRoute,
    private val reroute: suspend (from: LatLon) -> NavRoute?,
    private val speak: (Announcement) -> Unit,
    private val config: NavConfig = NavConfig(),
    private val simulated: Boolean = false,
    private val now: () -> Long = System::currentTimeMillis
) {
    private var navigator = TripNavigator(initial, config)
    private var announcer = Announcer()
    private var route = initial
    private var lastRerouteAt: Long? = null
    private var arrivalSpoken = false

    private val _state = MutableStateFlow(DriveUi(initial, simulated = simulated))
    val state: StateFlow<DriveUi> = _state.asStateFlow()

    suspend fun onFix(fix: Fix) {
        when (val nav = navigator.update(fix)) {
            is NavState.OnRoute -> {
                announcer.next(route, nav)?.let(speak)
                _state.value = DriveUi(route, nav, fix = fix, simulated = simulated, lastOnRoute = nav)
            }
            NavState.Arrived -> {
                if (!arrivalSpoken) {
                    arrivalSpoken = true
                    route.maneuvers.last().let { it.verbalPre ?: it.instruction.takeIf(String::isNotBlank) }
                        ?.let { speak(Announcement(Announcer.Kind.Pre, it, 0.0)) }
                }
                _state.value = DriveUi(
                    route,
                    NavState.Arrived,
                    fix = fix,
                    simulated = simulated,
                    lastOnRoute = _state.value.lastOnRoute
                )
            }
            is NavState.OffRoute -> reroute(nav, fix)
        }
    }

    private suspend fun reroute(off: NavState.OffRoute, fix: Fix) {
        val current = _state.value
        val last = lastRerouteAt
        if (last != null && now() - last < REROUTE_COOLDOWN_MS) {
            _state.value = current.copy(nav = off, fix = fix, rerouteFailed = true, rerouting = false)
            return
        }
        lastRerouteAt = now()
        _state.value = current.copy(nav = off, fix = fix, rerouting = true, rerouteFailed = false)
        val replacement = reroute(off.position)
        if (replacement == null) {
            _state.value = _state.value.copy(rerouting = false, rerouteFailed = true)
            return
        }
        route = replacement
        navigator = TripNavigator(replacement, config)
        announcer = Announcer()
        _state.value = DriveUi(replacement, fix = fix, simulated = simulated)
    }

    private companion object {
        /** After a reroute attempt, wait this long before another, so a failing one isn't retried every fix. */
        const val REROUTE_COOLDOWN_MS = 8_000L
    }
}
