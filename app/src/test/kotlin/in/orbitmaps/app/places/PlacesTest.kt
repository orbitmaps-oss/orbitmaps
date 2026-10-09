// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.places

import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.search.CategoryGroup
import `in`.orbitmaps.app.ui.sample.SampleData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    /** Stands in for string resources: sample names resolve to readable fake names. */
    private val names = mapOf(
        R.string.sample_place_cafe to "Riverside Café",
        R.string.sample_place_fuel to "Town Fuel Station",
        R.string.sample_place_ev to "Market EV Charger",
        R.string.sample_place_trail to "Hilltop Trail",
        R.string.sample_place_clinic to "Community Clinic"
    )
    private val samples = SamplePlaces { names.getValue(it) }

    @Test
    fun indexIdsRoundTripAndSampleIdsAreNotIndexIds() {
        assertEquals("osm:42", PlaceIds.forIndex(42))
        assertEquals(42L, PlaceIds.indexRowId(PlaceIds.forIndex(42)))
        assertNull(PlaceIds.indexRowId("cafe"))
        assertNull(PlaceIds.indexRowId("osm:x"))
    }

    @Test
    fun sampleSearchMatchesNamesIgnoringCase() = runBlocking {
        val results = samples.search("river", null)
        assertFalse(results.fromOfflineIndex)
        assertEquals(listOf("cafe"), results.places.map { it.id })
        assertEquals("Riverside Café", results.places.single().name)
        assertTrue(results.places.single().isSample)
    }

    @Test
    fun sampleSearchFiltersByGroup() = runBlocking {
        assertEquals(listOf("fuel"), samples.search("", CategoryGroup.Fuel).places.map { it.id })
        assertEquals(emptyList<String>(), samples.search("river", CategoryGroup.Fuel).places.map { it.id })
    }

    @Test
    fun emptySampleSearchListsEveryPlace() = runBlocking {
        assertEquals(SampleData.places.size, samples.search("", null).places.size)
    }

    @Test
    fun sampleLookupKeepsTheCommunityFields() = runBlocking {
        val cafe = samples.place("cafe")!!
        assertEquals(R.string.category_food, cafe.category)
        assertEquals(4.4, cafe.rating!!, 0.0)
        assertEquals(6, cafe.confirmations)
        assertNull(samples.place("unknown"))
    }
}
