// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatLonTest {
    @Test
    fun acceptsValidCoordinates() {
        val point = LatLon(12.5, -45.25)
        assertEquals(12.5, point.latitude, 0.0)
        assertEquals(-45.25, point.longitude, 0.0)
    }

    @Test
    fun acceptsBoundaries() {
        LatLon(90.0, 180.0)
        LatLon(-90.0, -180.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLatitudeAbove90() {
        LatLon(90.0001, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLongitudeBelowMinus180() {
        LatLon(0.0, -180.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNaN() {
        LatLon(Double.NaN, 0.0)
    }

    @Test
    fun orNullReturnsNullForInvalidInput() {
        assertNull(LatLon.orNull(91.0, 0.0))
        assertNull(LatLon.orNull(0.0, Double.POSITIVE_INFINITY))
        assertEquals(LatLon(1.0, 2.0), LatLon.orNull(1.0, 2.0))
    }

    @Test
    fun isValidMatchesConstructor() {
        assertTrue(LatLon.isValid(0.0, 0.0))
        assertFalse(LatLon.isValid(-90.5, 0.0))
    }

    @Test
    fun distanceToItselfIsZero() {
        val panaji = LatLon(15.4989, 73.8278)
        assertEquals(0.0, panaji.distanceKmTo(panaji), 0.0)
    }

    @Test
    fun distanceMatchesKnownValues() {
        // One degree of latitude is about 111.2 km everywhere.
        assertEquals(111.2, LatLon(0.0, 0.0).distanceKmTo(LatLon(1.0, 0.0)), 0.1)
        // Paris to London is about 344 km.
        assertEquals(344.0, LatLon(48.8566, 2.3522).distanceKmTo(LatLon(51.5074, -0.1278)), 2.0)
        // Antipodes: half the Earth's circumference.
        assertEquals(Math.PI * LatLon.EARTH_RADIUS_KM, LatLon(0.0, 0.0).distanceKmTo(LatLon(0.0, 180.0)), 0.001)
    }

    @Test
    fun distanceIsSymmetric() {
        val a = LatLon(15.4989, 73.8278)
        val b = LatLon(15.5395, 73.8135)
        assertEquals(a.distanceKmTo(b), b.distanceKmTo(a), 1e-12)
    }

    @Test
    fun toStringDoesNotRevealCoordinates() {
        val text = LatLon(48.8584, 2.2945).toString()
        assertFalse(text.contains("48.8584"))
        assertFalse(text.contains("2.2945"))
    }
}
