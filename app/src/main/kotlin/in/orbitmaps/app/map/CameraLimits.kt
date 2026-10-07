// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import `in`.orbitmaps.core.model.LatLon
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Keeps the whole map viewport inside a region, so nothing outside the region's tiles is ever drawn.
 *
 * Works in Web Mercator units (the world is 0..1 on each axis) and MapLibre's zoom convention: at zoom
 * z the world is `512 * 2^z` density-independent pixels wide. Assumes the map is north-up and flat.
 */
object CameraLimits {
    private const val TILE_SIZE_DP = 512.0

    /** The lowest zoom at which the region covers a viewport of the given size in both directions. */
    fun minZoom(southWest: LatLon, northEast: LatLon, widthDp: Double, heightDp: Double): Double {
        requireViewport(widthDp, heightDp)
        val spanX = mercatorX(northEast.longitude) - mercatorX(southWest.longitude)
        val spanY = mercatorY(southWest.latitude) - mercatorY(northEast.latitude)
        require(spanX > 0 && spanY > 0) { "region must have a positive size" }
        return log2(max(widthDp / (TILE_SIZE_DP * spanX), heightDp / (TILE_SIZE_DP * spanY)))
    }

    /**
     * The area the camera target (screen centre) may move in at [zoom]: the region shrunk by half the
     * viewport on each side. If the viewport is wider or taller than the region, that axis collapses to
     * the region's centre line. Returns (south-west, north-east).
     */
    fun targetBounds(
        southWest: LatLon,
        northEast: LatLon,
        zoom: Double,
        widthDp: Double,
        heightDp: Double
    ): Pair<LatLon, LatLon> {
        requireViewport(widthDp, heightDp)
        val worldDp = TILE_SIZE_DP * 2.0.pow(zoom)
        val halfWidth = widthDp / worldDp / 2
        val halfHeight = heightDp / worldDp / 2
        val (west, east) = shrink(mercatorX(southWest.longitude), mercatorX(northEast.longitude), halfWidth)
        // Mercator y grows southwards, so north is the smaller value.
        val (north, south) = shrink(mercatorY(northEast.latitude), mercatorY(southWest.latitude), halfHeight)
        return LatLon(latitude(south), longitude(west)) to LatLon(latitude(north), longitude(east))
    }

    private fun shrink(low: Double, high: Double, by: Double): Pair<Double, Double> {
        val newLow = low + by
        val newHigh = high - by
        if (newLow <= newHigh) return newLow to newHigh
        val middle = (low + high) / 2
        return middle to middle
    }

    private fun requireViewport(widthDp: Double, heightDp: Double) {
        require(widthDp > 0 && heightDp > 0) { "viewport must have a positive size" }
    }

    internal fun mercatorX(longitude: Double) = (longitude + 180.0) / 360.0

    internal fun mercatorY(latitude: Double): Double {
        val phi = Math.toRadians(latitude)
        return (1 - ln(tan(PI / 4 + phi / 2)) / PI) / 2
    }

    private fun longitude(x: Double) = x * 360.0 - 180.0

    private fun latitude(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))
}
