// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import `in`.orbitmaps.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSearchTest {
    private val panaji = LatLon(15.4909, 73.8278)

    @Test
    fun urlEncodesTheTextAndBoundsTheBoxAroundTheCentre() {
        val url = OnlineSearch.url("café & bar", panaji, bounded = true)
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?q=caf%C3%A9+%26+bar&viewbox="))
        assertTrue(url.contains("&bounded=1"))
        assertTrue(url.endsWith("&format=json&limit=5&addressdetails=1"))
        assertTrue(!OnlineSearch.url("x", panaji, bounded = false).contains("bounded"))
    }

    @Test
    fun urlNeverCarriesThePhonesIdentityOnlyTheMapCentreBox() {
        val url = OnlineSearch.url("fuel", panaji, bounded = false)
        assertTrue(!url.contains("lat="))
        assertTrue(!url.contains("lon="))
    }

    @Test
    fun viewboxStaysInsideTheWorldNearTheEdges() {
        assertEquals("179.625,90.0,180.0,89.625", OnlineSearch.viewbox(LatLon(89.875, 179.875)))
        assertEquals("-180.0,-89.625,-179.625,-90.0", OnlineSearch.viewbox(LatLon(-89.875, -179.875)))
        // left, top, right, bottom: 0.25 degrees each way.
        assertEquals("73.75,16.25,74.25,15.75", OnlineSearch.viewbox(LatLon(16.0, 74.0)))
    }

    @Test
    fun parseReadsStringCoordinatesAndSkipsBrokenEntries() {
        val body = """
            [
              {"lat":"15.4989","lon":"73.8313","display_name":"Panaji, North Goa, Goa, 403001, India"},
              {"lat":"abc","lon":"73.8","display_name":"Bad latitude"},
              {"lat":"95.0","lon":"73.8","display_name":"Out of range"},
              {"lat":"15.5","lon":"73.8"},
              "not an object",
              {"lat":"15.4","lon":"73.9","display_name":"Miramar Beach, Panaji, Goa, India","extra":1}
            ]
        """.trimIndent()
        val places = OnlineSearch.parse(body)
        assertEquals(listOf("Panaji, Goa", "Miramar Beach, Panaji"), places.map { it.label })
        assertEquals("Panaji, North Goa, Goa, 403001, India", places[0].fullName)
        assertEquals(LatLon(15.4989, 73.8313), places[0].location)
    }

    @Test
    fun parseGivesAnEmptyListForBadJsonOrAnErrorObject() {
        assertTrue(OnlineSearch.parse("").isEmpty())
        assertTrue(OnlineSearch.parse("<html>").isEmpty())
        assertTrue(OnlineSearch.parse("""{"error":"rate limited"}""").isEmpty())
        assertTrue(OnlineSearch.parse("[]").isEmpty())
    }

    @Test
    fun minimalLabelKeepsTheNameAndTheLastAreaBeforeThePostcode() {
        assertEquals("Panaji", OnlineSearch.minimalLabel("Panaji"))
        assertEquals("Mumbai, India", OnlineSearch.minimalLabel("Mumbai, India"))
        assertEquals(
            "Cafe Bhonsle, Goa",
            OnlineSearch.minimalLabel("Cafe Bhonsle, MG Road, Panaji, North Goa, Goa, 403001, India")
        )
        // A house number keeps the street and the next part.
        assertEquals(
            "12, MG Road, Panaji",
            OnlineSearch.minimalLabel("12, MG Road, Panaji, Goa, 403001, India")
        )
        // A leading Plus Code is dropped.
        assertEquals("Fort Aguada, Candolim", OnlineSearch.minimalLabel("7JQ9+X2, Fort Aguada, Candolim, Goa, India"))
    }

    @Test
    fun coordinatesInReadsPlainPairsAndMapLinks() {
        assertEquals(LatLon(15.4909, 73.8278), OnlineSearch.coordinatesIn("15.4909, 73.8278"))
        assertEquals(LatLon(15.49, 73.82), OnlineSearch.coordinatesIn("https://example.org/maps/@15.49,73.82,17z"))
        assertEquals(LatLon(15.5, 73.8), OnlineSearch.coordinatesIn("https://example.org/data=!3d15.5!4d73.8"))
        assertEquals(LatLon(-33.86, 151.2), OnlineSearch.coordinatesIn("-33.86 151.2"))
    }

    @Test
    fun coordinatesInRejectsOutOfRangeAndPlainText() {
        assertNull(OnlineSearch.coordinatesIn("Panaji"))
        assertNull(OnlineSearch.coordinatesIn("95.5, 73.8"))
        assertNull(OnlineSearch.coordinatesIn("15, 73"))
    }
}
