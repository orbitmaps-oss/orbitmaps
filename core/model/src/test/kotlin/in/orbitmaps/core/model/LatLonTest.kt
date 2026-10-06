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
    fun toStringDoesNotRevealCoordinates() {
        val text = LatLon(48.8584, 2.2945).toString()
        assertFalse(text.contains("48.8584"))
        assertFalse(text.contains("2.2945"))
    }
}
