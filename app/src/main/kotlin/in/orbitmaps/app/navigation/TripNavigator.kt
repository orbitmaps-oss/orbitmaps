// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.core.model.LatLon
import kotlin.math.max
import kotlin.math.min

/** One position fix, as the navigator needs it. */
data class Fix(val position: LatLon, val accuracyM: Float = 10f, val speedMps: Float? = null)

sealed interface NavState {
    /**
     * Following the route. [next] is the maneuver ahead ([nextIndex] in the route), [distanceToNextM]
     * how far it is; [current] is the step being driven. [snapped] is the position on the route line.
     */
    data class OnRoute(
        val current: Maneuver,
        val next: Maneuver?,
        val nextIndex: Int,
        val distanceToNextM: Double,
        val remainingM: Double,
        val remainingSeconds: Double,
        val snapped: LatLon,
        val speedMps: Float?
    ) : NavState

    /** Clearly away from the route; time to reroute from [position]. */
    data class OffRoute(val position: LatLon, val distanceFromRouteM: Double) : NavState

    data object Arrived : NavState
}

data class NavConfig(
    /** Off route when farther than this or 1.5 × the fix's accuracy, whichever is larger (up to [maxOffRouteM]). */
    val offRouteM: Double = 35.0,
    val maxOffRouteM: Double = 100.0,
    /** This many off-route fixes in a row before saying so, so one bad fix doesn't trigger a reroute. */
    val offRouteFixes: Int = 2,
    val arrivedM: Double = 25.0,
    /** How many route-line segments ahead to look for the next position before searching everywhere. */
    val searchAhead: Int = 60
)

/**
 * Follows one route with GPS fixes: snaps each fix onto the route line, searching forward from the
 * last position so the progress doesn't jump back onto an earlier part of the road, and works out
 * the current step, the next maneuver and what's left. Pure: no Android, no clock.
 */
class TripNavigator(val route: NavRoute, private val config: NavConfig = NavConfig()) {
    private var segment = 0
    private var offRouteCount = 0
    private var arrived = false

    fun update(fix: Fix): NavState {
        if (arrived) return NavState.Arrived
        val threshold = min(config.maxOffRouteM, max(config.offRouteM, fix.accuracyM * 1.5))
        var best = nearest(fix.position, max(0, segment - 1), min(route.shape.size - 2, segment + config.searchAhead))
        if (best.distanceM > threshold) {
            // Maybe far ahead (after a tunnel or a gap in fixes): look along the whole route.
            val anywhere = nearest(fix.position, 0, route.shape.size - 2)
            if (anywhere.distanceM < best.distanceM) best = anywhere
        }
        if (best.distanceM > threshold) {
            offRouteCount++
            if (offRouteCount >= config.offRouteFixes) return NavState.OffRoute(fix.position, best.distanceM)
        } else {
            offRouteCount = 0
            segment = best.segment
        }

        val progressM = route.cumulativeM[segment] +
            best.fraction * (route.cumulativeM[segment + 1] - route.cumulativeM[segment])
        val remainingM = max(0.0, route.lengthM - progressM)
        val currentIndex = route.maneuverAt(segment)
        val nextIndex = currentIndex + 1
        val next = route.maneuvers.getOrNull(nextIndex)
        if (remainingM <= config.arrivedM || route.maneuvers[currentIndex].isDestination) {
            arrived = true
            return NavState.Arrived
        }
        val distanceToNextM = next?.let { max(0.0, route.cumulativeM[it.beginIndex] - progressM) } ?: remainingM
        return NavState.OnRoute(
            current = route.maneuvers[currentIndex],
            next = next,
            nextIndex = nextIndex,
            distanceToNextM = distanceToNextM,
            remainingM = remainingM,
            remainingSeconds = remainingSeconds(currentIndex, distanceToNextM),
            snapped = pointAt(segment, best.fraction),
            speedMps = fix.speedMps
        )
    }

    /** Time left: the rest of the current step (in proportion) plus every later step. */
    private fun remainingSeconds(currentIndex: Int, distanceToNextM: Double): Double {
        val current = route.maneuvers[currentIndex]
        val stepM = current.lengthKm * 1000.0
        val share = if (stepM > 0) (distanceToNextM / stepM).coerceIn(0.0, 1.0) else 0.0
        return current.timeSeconds * share + route.maneuvers.drop(currentIndex + 1).sumOf { it.timeSeconds }
    }

    private data class Nearest(val segment: Int, val fraction: Double, val distanceM: Double)

    private fun nearest(p: LatLon, from: Int, to: Int): Nearest {
        var best = Nearest(from, 0.0, Double.MAX_VALUE)
        for (i in from..to) {
            val (t, d) = project(p, route.shape[i], route.shape[i + 1])
            if (d < best.distanceM) best = Nearest(i, t, d)
        }
        return best
    }

    private fun pointAt(segment: Int, fraction: Double): LatLon {
        val a = route.shape[segment]
        val b = route.shape[segment + 1]
        return LatLon(
            a.latitude + (b.latitude - a.latitude) * fraction,
            a.longitude + (b.longitude - a.longitude) * fraction
        )
    }
}

/**
 * Something to say. For [Kind.Alert] the voice layer adds the distance ("In 400 metres, …") from a
 * translatable string; Valhalla's alert text has none.
 */
data class Announcement(val kind: Announcer.Kind, val text: String, val distanceM: Double)

/** What to say, once each: the start, an early alert and the instruction just before each maneuver. */
class Announcer {
    private val spoken = mutableSetOf<Pair<Int, Kind>>()

    enum class Kind { Start, Alert, Pre }

    /** What to speak now for [state], or null. Call on every update. */
    fun next(route: NavRoute, state: NavState.OnRoute): Announcement? {
        if (spoken.add(0 to Kind.Start)) {
            return route.maneuvers.first().verbalPre?.let { Announcement(Kind.Start, it, 0.0) }
        }
        val next = state.next ?: return null
        val speed = (state.speedMps ?: DEFAULT_SPEED_MPS).toDouble()
        val preAt = (speed * PRE_SECONDS).coerceIn(MIN_PRE_M, MAX_PRE_M)
        val alertAt = (speed * ALERT_SECONDS).coerceIn(MIN_ALERT_M, MAX_ALERT_M)
        val text = when {
            state.distanceToNextM <= preAt && spoken.add(state.nextIndex to Kind.Pre) -> Kind.Pre to next.verbalPre
            state.distanceToNextM in preAt..alertAt &&
                state.current.lengthKm * 1000.0 > alertAt + MIN_GAP_M &&
                spoken.add(state.nextIndex to Kind.Alert) -> Kind.Alert to next.verbalAlert
            else -> return null
        }
        return text.second?.let { Announcement(text.first, it, state.distanceToNextM) }
    }

    private companion object {
        const val DEFAULT_SPEED_MPS = 10.0
        const val PRE_SECONDS = 8.0
        const val MIN_PRE_M = 40.0
        const val MAX_PRE_M = 250.0
        const val ALERT_SECONDS = 30.0
        const val MIN_ALERT_M = 150.0
        const val MAX_ALERT_M = 800.0
        const val MIN_GAP_M = 100.0
    }
}
