// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import `in`.orbitmaps.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaTilesTest {
    @Test
    fun gridSizesMatchValhallasHierarchy() {
        assertEquals(90 to 45, ValhallaTiles.columns(0) to ValhallaTiles.rows(0))
        assertEquals(360 to 180, ValhallaTiles.columns(1) to ValhallaTiles.rows(1))
        assertEquals(1440 to 720, ValhallaTiles.columns(2) to ValhallaTiles.rows(2))
    }

    @Test
    fun tileIdCountsRowsFromTheSouthWest() {
        // 52.1N 5.1E: level-2 row 568, column 740.
        assertEquals(568 * 1440 + 740, ValhallaTiles.tileId(2, LatLon(52.1, 5.1)))
        assertEquals(0, ValhallaTiles.tileId(0, LatLon(-90.0, -180.0)))
        // The north pole and the antimeridian fall in the last row and column, not past them.
        assertEquals(90 * 45 - 1, ValhallaTiles.tileId(0, LatLon(90.0, 180.0)))
    }

    @Test
    fun filePathsArePaddedToGroupsOfThreeDigits() {
        assertEquals("2/000/818/660.gph", GraphTile(2, 818660).path)
        assertEquals("2/001/036/799.gph", GraphTile(2, 1440 * 720 - 1).path)
        assertEquals("1/064/799.gph", GraphTile(1, 360 * 180 - 1).path)
        assertEquals("0/000/000.gph", GraphTile(0, 0).path)
        assertEquals("0/004/049.gph", GraphTile(0, 4049).path)
        assertThrows(IllegalArgumentException::class.java) { GraphTile(0, 4050).path }
        assertThrows(IllegalArgumentException::class.java) { ValhallaTiles.tileId(3, LatLon(0.0, 0.0)) }
    }

    @Test
    fun tilesInABoxCoverItExactly() {
        val box = Box(52.01, 5.01, 52.99, 5.99)
        assertEquals(16, ValhallaTiles.tilesIn(2, box).size)
        assertEquals(setOf(GraphTile(1, ValhallaTiles.tileId(1, LatLon(52.5, 5.5)))), ValhallaTiles.tilesIn(1, box))
    }

    @Test
    fun boxesAreClampedAtTheEdgesOfTheWorld() {
        val tiles = ValhallaTiles.tilesIn(0, Box(85.0, 175.0, 90.0, 180.0).padded(10.0))
        assertTrue(tiles.all { it.id in 0 until 90 * 45 })
        assertTrue(tiles.isNotEmpty())
    }

    private val panaji = LatLon(15.4989, 73.8278)
    private val porvorim = LatLon(15.5395, 73.8135)
    private val mumbai = LatLon(19.076, 72.8777)

    @Test
    fun aShortTripNeedsOnlyAFewTiles() {
        val tiles = TripTiles.forPlanning(panaji, porvorim)
        assertTrue(tiles.size in 3..40)
        assertTrue(tiles.any { it.level == 0 } && tiles.any { it.level == 1 } && tiles.any { it.level == 2 })
        assertTrue(GraphTile(2, ValhallaTiles.tileId(2, panaji)) in tiles)
        assertTrue(GraphTile(2, ValhallaTiles.tileId(2, porvorim)) in tiles)
    }

    @Test
    fun aLongTripFetchesLocalRoadsOnlyNearItsEnds() {
        val tiles = TripTiles.forPlanning(panaji, mumbai)
        val local = tiles.filter { it.level == 2 }
        // Around each end only: nowhere near the 3.5 degrees in between.
        assertTrue(local.size <= 2 * 9)
        assertTrue(GraphTile(2, ValhallaTiles.tileId(2, mumbai)) in tiles)
        assertTrue(GraphTile(0, ValhallaTiles.tileId(0, LatLon(17.0, 73.5))) in tiles)
    }

    @Test
    fun theCorridorFollowsTheRouteOnEveryLevel() {
        val shape = listOf(panaji, LatLon(16.5, 73.5), LatLon(17.8, 73.2), mumbai)
        val corridor = TripTiles.forCorridor(shape)
        shape.forEach { point ->
            for (level in 0..2) assertTrue(GraphTile(level, ValhallaTiles.tileId(level, point)) in corridor)
        }
    }

    @Test
    fun polylineDecodesGooglesReferenceExample() {
        // Google's documented example, encoded at precision 5; decoded at 6 the values are tenfold smaller.
        val points = Polyline6.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(3.85, points[0].latitude, 1e-9)
        assertEquals(-12.02, points[0].longitude, 1e-9)
        assertEquals(4.3252, points[2].latitude, 1e-9)
        assertEquals(-12.6453, points[2].longitude, 1e-9)
    }

    @Test
    fun polylineRoundTripsAtSixDecimals() {
        val shape = listOf(LatLon(15.498912, 73.827801), LatLon(15.539501, 73.813499), LatLon(-33.8688, 151.2093))
        val decoded = Polyline6.decode(encode(shape))
        shape.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-6)
            assertEquals(a.longitude, b.longitude, 1e-6)
        }
        assertThrows(IllegalArgumentException::class.java) { Polyline6.decode("_p~iF~ps|U_") }
    }

    private fun encode(points: List<LatLon>): String = buildString {
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = Math.round(p.latitude * 1e6)
            val lon = Math.round(p.longitude * 1e6)
            appendValue(lat - lastLat)
            appendValue(lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
    }

    private fun StringBuilder.appendValue(value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        append((v + 63).toInt().toChar())
    }
}
