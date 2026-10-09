// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.places

import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.search.CategoryGroup
import `in`.orbitmaps.app.search.IndexedPlace
import `in`.orbitmaps.app.search.SearchHit
import `in`.orbitmaps.app.search.ShardHit
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.core.model.LatLon
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
    fun indexIdsRoundTripPerSourceAndSampleIdsAreNotIndexIds() {
        assertEquals("osm:42", PlaceIds.forIndex(42))
        assertEquals("world:7", PlaceIds.forIndex(7, PlaceSource.World))
        assertEquals(PlaceRef(PlaceSource.Region, 42), PlaceIds.parse("osm:42"))
        assertEquals(PlaceRef(PlaceSource.World, 7), PlaceIds.parse("world:7"))
        assertEquals("area:818660:5", PlaceIds.forArea(818660, 5))
        assertEquals(PlaceRef(PlaceSource.Area, 5, cell = 818660), PlaceIds.parse("area:818660:5"))
        assertNull(PlaceIds.parse("cafe"))
        assertNull(PlaceIds.parse("osm:x"))
        assertNull(PlaceIds.parse("area:5"))
        assertNull(PlaceIds.parse("area:x:5"))
    }

    private fun hit(
        name: String,
        lat: Double,
        lon: Double,
        score: Double,
        importance: Int = 30,
        osm: String = "n$name"
    ) = SearchHit(
        IndexedPlace(1, osm, name, null, "place=town", LatLon(lat, lon), importance, null),
        distanceKm = 0.0,
        score = score
    )

    @Test
    fun worldDuplicatesOfRegionPlacesAreDropped() {
        val region = listOf(hit("Panaji", 15.4989, 73.8278, score = 5.0))
        val world = listOf(hit("Panaji", 15.50, 73.83, score = 9.0), hit("Mumbai", 19.07, 72.88, score = 7.0))
        val merged = mergeResults(region, world, limit = 10)
        assertEquals(listOf("Mumbai", "Panaji"), merged.map { it.hit.place.name })
        assertEquals(listOf(PlaceSource.World, PlaceSource.Region), merged.map { it.source })
    }

    @Test
    fun sameNameFarAwayIsADifferentPlace() {
        val region = listOf(hit("Margao", 15.27, 73.96, score = 5.0))
        val world = listOf(hit("Margao", 40.0, -3.0, score = 1.0))
        assertEquals(2, mergeResults(region, world, limit = 10).size)
    }

    @Test
    fun mergeKeepsTheBestAndPrefersRegionOnTies() {
        val region = listOf(hit("A", 15.0, 73.0, score = 3.0), hit("B", 15.1, 73.1, score = 1.0))
        val world = listOf(hit("C", 19.0, 72.0, score = 3.0))
        val merged = mergeResults(region, world, limit = 2)
        assertEquals(listOf("A", "C"), merged.map { it.hit.place.name })
    }

    @Test
    fun areaResultsTheRegionAlreadyHasAreDroppedAndKeepTheirCell() {
        val region = listOf(hit("Cafe A", 15.49, 73.82, score = 2.0, osm = "n10"))
        val area = listOf(
            ShardHit(818660, hit("Cafe A", 15.49, 73.82, score = 9.0, osm = "n10")),
            ShardHit(818660, hit("Cafe B", 15.48, 73.81, score = 5.0, osm = "n11"))
        )
        val world = listOf(hit("Cafe B", 15.48, 73.81, score = 1.0))
        val merged = mergeResults(region, world, limit = 10, area = area)
        assertEquals(listOf("Cafe B", "Cafe A"), merged.map { it.hit.place.name })
        assertEquals(listOf(PlaceSource.Area, PlaceSource.Region), merged.map { it.source })
        assertEquals("area:818660:1", merged.first().id)
        assertEquals("osm:1", merged.last().id)
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
