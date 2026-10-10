// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.core.model.LatLon
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RegionPickerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun region(id: String, sw: LatLon, ne: LatLon, routing: Boolean = false): InstalledRegion {
        val dir = tmp.newFolder(id)
        File(dir, "map.pmtiles").writeText("x")
        val files = mutableListOf(PackFile(PackRole.Map, 1, "a".repeat(64)))
        if (routing) {
            File(dir, "routing.tar").writeText("x")
            File(dir, "routing.json").writeText("{}")
            files += PackFile(PackRole.Routing, 1, "b".repeat(64))
            files += PackFile(PackRole.RoutingConfig, 2, "c".repeat(64))
        }
        return InstalledRegion(
            RegionManifest(id, id, "2026-10-10", RegionBounds(sw, ne), "ODbL-1.0", "x", files),
            dir
        )
    }

    private val goa by lazy { region("goa", LatLon(14.9, 73.6), LatLon(15.9, 74.4), routing = true) }
    private val panaji by lazy { region("panaji", LatLon(15.4, 73.7), LatLon(15.6, 73.9), routing = true) }
    private val kerala by lazy { region("kerala", LatLon(8.0, 74.8), LatLon(12.8, 77.5)) }

    @Test
    fun noRegionsMeansNoMap() {
        assertNull(RegionPicker.forMap(emptyList(), LatLon(15.5, 73.8)))
    }

    @Test
    fun theSmallestRegionContainingTheCentreWins() {
        val pick = RegionPicker.forMap(listOf(goa, panaji, kerala), LatLon(15.5, 73.8))
        assertEquals("panaji", pick?.id)
    }

    @Test
    fun theCurrentRegionIsKeptWhileItStillContainsTheCentre() {
        val pick = RegionPicker.forMap(listOf(goa, panaji), LatLon(15.5, 73.8), currentId = "goa")
        assertEquals("goa", pick?.id)
    }

    @Test
    fun outsideEveryRegionTheCurrentOrFirstOneIsUsed() {
        val far = LatLon(28.6, 77.2)
        assertEquals("kerala", RegionPicker.forMap(listOf(goa, kerala), far, currentId = "kerala")?.id)
        assertEquals("goa", RegionPicker.forMap(listOf(kerala, goa), far)?.id)
        assertEquals("goa", RegionPicker.forMap(listOf(kerala, goa), null)?.id)
    }

    @Test
    fun routesNeedBothEndsInOneRegionWithRoutingData() {
        val a = LatLon(15.5, 73.8)
        assertEquals("panaji", RegionPicker.forRoute(listOf(goa, panaji), a, LatLon(15.45, 73.85))?.id)
        assertEquals("goa", RegionPicker.forRoute(listOf(goa, panaji), a, LatLon(15.1, 74.0))?.id)
        assertNull(RegionPicker.forRoute(listOf(goa, panaji), a, LatLon(12.9, 77.6)))
        assertNull(RegionPicker.forRoute(listOf(kerala), LatLon(10.0, 76.0), LatLon(10.1, 76.1)))
    }

    @Test
    fun theRoutingConfigPointsAtThePackTar() {
        File(goa.dir, "routing.json").writeText("""{"mjolnir":{"tile_extract":"/build/x.tar"}}""")
        val config = RegionRouting.configFor(goa)!!
        assert(config.readText().contains(File(goa.dir, "routing.tar").absolutePath.replace("\\", "\\\\")))
        assertNull(RegionRouting.configFor(kerala))
    }
}
