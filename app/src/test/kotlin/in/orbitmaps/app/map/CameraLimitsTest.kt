// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import `in`.orbitmaps.app.map.CameraLimits.mercatorX
import `in`.orbitmaps.app.map.CameraLimits.mercatorY
import `in`.orbitmaps.core.model.LatLon
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLimitsTest {
    private val sw = SampleRegion.southWest
    private val ne = SampleRegion.northEast
    private val eps = 1e-9

    // Portrait phone (OnePlus 7: 1080 × 2340 px at density 2.625) and a landscape tablet.
    private val viewports = listOf(411.4 to 891.4, 1280.0 to 800.0, 360.0 to 360.0)

    /** The viewport's edges in Mercator units when centred on [target] at [zoom]. */
    private fun edges(target: LatLon, zoom: Double, widthDp: Double, heightDp: Double): DoubleArray {
        val world = 512.0 * 2.0.pow(zoom)
        val x = mercatorX(target.longitude)
        val y = mercatorY(target.latitude)
        val halfWidth = widthDp / world / 2
        val halfHeight = heightDp / world / 2
        return doubleArrayOf(x - halfWidth, x + halfWidth, y - halfHeight, y + halfHeight)
    }

    private fun assertInsideRegion(target: LatLon, zoom: Double, widthDp: Double, heightDp: Double) {
        val (west, east, north, south) = edges(target, zoom, widthDp, heightDp).toList()
        assertTrue("west $west", west >= mercatorX(sw.longitude) - eps)
        assertTrue("east $east", east <= mercatorX(ne.longitude) + eps)
        assertTrue("north $north", north >= mercatorY(ne.latitude) - eps)
        assertTrue("south $south", south <= mercatorY(sw.latitude) + eps)
    }

    @Test
    fun minZoomMakesTheRegionCoverTheViewportExactlyOnOneAxis() {
        for ((w, h) in viewports) {
            val zoom = CameraLimits.minZoom(sw, ne, w, h)
            val (west, east, north, south) = edges(SampleRegion.center, zoom, w, h).toList()
            val widthRatio = (east - west) / (mercatorX(ne.longitude) - mercatorX(sw.longitude))
            val heightRatio = (south - north) / (mercatorY(sw.latitude) - mercatorY(ne.latitude))
            assertEquals("$w x $h", 1.0, maxOf(widthRatio, heightRatio), 1e-9)
            assertTrue("$w x $h", minOf(widthRatio, heightRatio) <= 1.0 + 1e-9)
        }
    }

    @Test
    fun portraitPhoneMinZoomIsAboutTwelve() {
        val zoom = CameraLimits.minZoom(sw, ne, 411.4, 891.4)
        assertTrue("$zoom", zoom in 11.5..12.0)
        assertTrue("initial zoom must not be below the minimum", SampleRegion.INITIAL_ZOOM >= zoom)
    }

    @Test
    fun viewportStaysInsideTheRegionAtEveryCornerOfTheTargetBounds() {
        for ((w, h) in viewports) {
            val min = CameraLimits.minZoom(sw, ne, w, h)
            for (zoom in listOf(min, min + 0.3, min + 1, 15.0, 18.0)) {
                val (low, high) = CameraLimits.targetBounds(sw, ne, zoom, w, h)
                for (lat in listOf(low.latitude, high.latitude)) {
                    for (lon in listOf(low.longitude, high.longitude)) {
                        assertInsideRegion(LatLon(lat, lon), zoom, w, h)
                    }
                }
            }
        }
    }

    @Test
    fun targetBoundsGrowTowardsTheRegionAsZoomIncreases() {
        val (lowA, highA) = CameraLimits.targetBounds(sw, ne, 13.0, 411.4, 891.4)
        val (lowB, highB) = CameraLimits.targetBounds(sw, ne, 16.0, 411.4, 891.4)
        assertTrue(highB.latitude - lowB.latitude > highA.latitude - lowA.latitude)
        assertTrue(highB.longitude - lowB.longitude > highA.longitude - lowA.longitude)
        assertTrue(lowB.latitude > sw.latitude && highB.latitude < ne.latitude)
        assertTrue(lowB.longitude > sw.longitude && highB.longitude < ne.longitude)
    }

    @Test
    fun belowMinZoomTheTooLargeAxisCollapsesToTheCentreLine() {
        val (low, high) = CameraLimits.targetBounds(sw, ne, 8.0, 411.4, 891.4)
        assertEquals(low.latitude, high.latitude, eps)
        assertEquals(low.longitude, high.longitude, eps)
    }

    @Test
    fun emptyViewportIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { CameraLimits.minZoom(sw, ne, 0.0, 800.0) }
        assertThrows(IllegalArgumentException::class.java) { CameraLimits.targetBounds(sw, ne, 13.0, 400.0, 0.0) }
    }
}
