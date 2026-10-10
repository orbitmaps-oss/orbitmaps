// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.core.model.LatLon
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionRowsTest {
    private val bounds = RegionBounds(LatLon(14.9, 73.6), LatLon(15.9, 74.4))
    private val goa = CatalogEntry("goa", "Goa", bounds, 5_000_000, "2026-10-10")
    private val kerala = CatalogEntry("kerala", "Kerala", bounds, 9_000_000, "2026-10-10")

    private fun installed(id: String, name: String, built: String = "2026-09-01") = InstalledRegion(
        RegionManifest(
            id,
            name,
            built,
            bounds,
            "ODbL-1.0",
            "© OpenStreetMap contributors",
            listOf(PackFile(PackRole.Map, 4_000_000, "a".repeat(64)))
        ),
        File("/x/$id")
    )

    @Test
    fun availableRegionsAreListedByName() {
        val rows = RegionRows.build(listOf(kerala, goa), emptyList(), emptyMap())
        assertEquals(listOf("Goa", "Kerala"), rows.map { it.name })
        assertEquals(RowState.Available, rows[0].state)
        assertEquals(5_000_000L, rows[0].sizeBytes)
    }

    @Test
    fun anInstalledRegionShowsAsInstalledWithItsBuildDate() {
        val rows = RegionRows.build(listOf(goa, kerala), listOf(installed("goa", "Goa")), emptyMap())
        assertEquals(RowState.Installed("2026-09-01"), rows.first { it.id == "goa" }.state)
        assertEquals(RowState.Available, rows.first { it.id == "kerala" }.state)
    }

    @Test
    fun aDownloadInProgressOrAFailureWinsOverTheStoredState() {
        val moving = mapOf(
            "goa" to RowState.Downloading(1_000_000, 5_000_000),
            "kerala" to RowState.Failed(RegionProblem.NoSpace)
        )
        val rows = RegionRows.build(listOf(goa, kerala), listOf(installed("goa", "Goa")), moving)
        assertEquals(20, (rows[0].state as RowState.Downloading).percent)
        assertEquals(RowState.Failed(RegionProblem.NoSpace), rows[1].state)
    }

    @Test
    fun installedRegionsStayListedWhenTheCatalogueIsUnavailable() {
        val rows = RegionRows.build(null, listOf(installed("goa", "Goa")), emptyMap())
        assertEquals(listOf("goa"), rows.map { it.id })
        assertTrue(rows[0].state is RowState.Installed)
        assertEquals(4_000_000L, rows[0].sizeBytes)
        assertEquals(emptyList<RegionRow>(), RegionRows.build(null, emptyList(), emptyMap()))
    }

    @Test
    fun percentIsClamped() {
        assertEquals(0, RowState.Downloading(0, 0).percent)
        assertEquals(100, RowState.Downloading(11, 10).percent)
    }

    @Test
    fun downloadsAreBlockedOfflineAndOnMobileDataWithWifiOnly() {
        assertEquals(RegionProblem.Offline, RegionRows.blockedBy(online = false, unmetered = false, wifiOnly = true))
        assertEquals(RegionProblem.NeedsWifi, RegionRows.blockedBy(online = true, unmetered = false, wifiOnly = true))
        assertNull(RegionRows.blockedBy(online = true, unmetered = true, wifiOnly = true))
        assertNull(RegionRows.blockedBy(online = true, unmetered = false, wifiOnly = false))
    }
}
