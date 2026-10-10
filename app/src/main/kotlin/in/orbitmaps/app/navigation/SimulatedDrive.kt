// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.core.model.LatLon
import kotlin.math.atan2

/** A simulated position with its heading, for desk-testing navigation. */
data class SimulatedFix(val fix: Fix, val bearingDeg: Float)

/** Walks along a route at a constant speed, one fix per step. Used by debug builds only. */
object SimulatedDrive {
    /**
     * Fixes every [stepM] metres along [route]'s line, starting at its first point and ending at its
     * last, each with [speedMps] and the heading of the segment it is on.
     */
    fun along(route: NavRoute, stepM: Double, speedMps: Float): Sequence<SimulatedFix> = sequence {
        require(stepM > 0) { "step must be positive" }
        var segment = 0
        var distance = 0.0
        val total = route.lengthM
        while (true) {
            val clamped = distance.coerceAtMost(total)
            while (segment < route.shape.size - 2 && route.cumulativeM[segment + 1] < clamped) segment++
            val a = route.shape[segment]
            val b = route.shape[segment + 1]
            val segmentLength = route.cumulativeM[segment + 1] - route.cumulativeM[segment]
            val t = if (segmentLength >
                0
            ) {
                ((clamped - route.cumulativeM[segment]) / segmentLength).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val point = LatLon(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t
            )
            yield(SimulatedFix(Fix(point, accuracyM = 5f, speedMps = speedMps), bearing(a, b)))
            if (clamped >= total) break
            distance += stepM
        }
    }

    /** Compass heading in degrees (0 north, clockwise) from [a] to [b]. */
    fun bearing(a: LatLon, b: LatLon): Float {
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = Math.sin(dLon) * Math.cos(Math.toRadians(b.latitude))
        val x = Math.cos(Math.toRadians(a.latitude)) * Math.sin(Math.toRadians(b.latitude)) -
            Math.sin(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) * Math.cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
