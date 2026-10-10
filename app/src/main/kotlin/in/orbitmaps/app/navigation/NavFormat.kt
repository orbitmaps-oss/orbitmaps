// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import kotlin.math.roundToInt

/** Pure helpers for showing and speaking navigation values. */
object NavFormat {
    /** At or above this distance, show kilometres: below it the 50 m rounding would give "1000 m". */
    private const val KM_FROM_M = 975.0

    /** Whether [metres] is shown as kilometres. */
    fun isKm(metres: Double) = metres >= KM_FROM_M

    /**
     * A distance rounded the way navigation speaks it: 10 m steps under 100 m, 50 m steps under 1 km
     * (never 0). Only meaningful below one kilometre; use [kmTenths] above.
     */
    fun roundedMetres(metres: Double): Int {
        val step = if (metres < 100.0) 10 else 50
        return ((metres / step).roundToInt() * step).coerceAtLeast(step)
    }

    /** Kilometres to one decimal, e.g. 1234 m -> 1.2. */
    fun kmTenths(metres: Double): Double = (metres / 100.0).roundToInt() / 10.0

    /**
     * Rotation in degrees (clockwise) for a right-pointing arrow, from Valhalla's maneuver type
     * (valhalla/odin/directionsbuilder, "Maneuver::Type"): 0 is straight ahead on the screen, so
     * the arrow points up.
     */
    fun turnRotation(type: Int): Float = when (type) {
        // Straight on: continue, becomes, stay straight, ramp straight, merge, roundabout and ferry.
        0, 1, 7, 8, 17, 22, 25, 26, 27, 28, 29, 36, 37 -> STRAIGHT
        2, 5, 18, 20, 23 -> RIGHT_SLIGHT
        9 -> RIGHT_SLIGHT
        10 -> RIGHT
        11 -> RIGHT_SHARP
        12 -> U_TURN
        13 -> U_TURN
        14 -> LEFT_SHARP
        15 -> LEFT
        16, 3, 6, 19, 21, 24 -> LEFT_SLIGHT
        4 -> STRAIGHT
        else -> STRAIGHT
    }

    private const val STRAIGHT = -90f
    private const val RIGHT_SLIGHT = -45f
    private const val RIGHT = 0f
    private const val RIGHT_SHARP = 45f
    private const val U_TURN = 90f
    private const val LEFT_SHARP = 135f
    private const val LEFT = 180f
    private const val LEFT_SLIGHT = -135f
}
