// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.core.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionManifestTest {
    private val sha = "a".repeat(64)

    private fun manifestJson(
        id: String = "goa",
        version: Int = 1,
        files: String = """
            {"role":"map","name":"map.pmtiles","size":1000,"sha256":"$sha"},
            {"role":"routing","name":"routing.tar","size":200,"sha256":"$sha"},
            {"role":"search","name":"search.sqlite","size":30,"sha256":"$sha"}
        """,
        bbox: String = "[73.6, 14.9, 74.4, 15.9]",
        licence: String = "ODbL-1.0"
    ) = """
        {"version":$version,"id":"$id","name":"Goa","built":"2026-10-10","bbox":$bbox,
         "licence":"$licence","attribution":"© OpenStreetMap contributors","files":[$files]}
    """.trimIndent()

    private fun bad(json: String) = assertThrows(ManifestException::class.java) { RegionJson.parseManifest(json) }

    @Test
    fun aValidManifestIsParsed() {
        val manifest = RegionJson.parseManifest(manifestJson())
        assertEquals("goa", manifest.id)
        assertEquals("Goa", manifest.name)
        assertEquals(1230L, manifest.totalBytes)
        assertEquals(1000L, manifest.file(PackRole.Map)?.size)
        assertNull(manifest.file(PackRole.RoutingConfig))
        assertEquals(LatLon(14.9, 73.6), manifest.bounds.southWest)
        assertEquals(LatLon(15.9, 74.4), manifest.bounds.northEast)
        assertTrue(manifest.bounds.contains(LatLon(15.5, 73.8)))
        assertFalse(manifest.bounds.contains(LatLon(19.0, 72.8)))
        assertEquals(15.4, manifest.bounds.center.latitude, 1e-9)
    }

    @Test
    fun anUnsafeIdOrFileNameIsRefused() {
        bad(manifestJson(id = "../etc"))
        bad(manifestJson(id = "Goa"))
        bad(manifestJson(files = """{"role":"map","name":"../../map.pmtiles","size":10,"sha256":"$sha"}"""))
        bad(manifestJson(files = """{"role":"map","name":"other.pmtiles","size":10,"sha256":"$sha"}"""))
    }

    @Test
    fun brokenOrUnsupportedContentIsRefused() {
        bad("not json")
        bad("[1,2]")
        bad(manifestJson(version = 2))
        bad(manifestJson(files = """{"role":"map","name":"map.pmtiles","size":0,"sha256":"$sha"}"""))
        bad(manifestJson(files = """{"role":"map","name":"map.pmtiles","size":5,"sha256":"XYZ"}"""))
        bad(manifestJson(files = """{"role":"routing","name":"routing.tar","size":5,"sha256":"$sha"}"""))
        bad(
            manifestJson(
                files = """
                    {"role":"map","name":"map.pmtiles","size":5,"sha256":"$sha"},
                    {"role":"map","name":"map.pmtiles","size":5,"sha256":"$sha"}
                """
            )
        )
        bad(manifestJson(bbox = "[74.4, 14.9, 73.6, 15.9]"))
        bad(manifestJson(bbox = "[1, 2, 3]"))
        bad(manifestJson(licence = ""))
    }

    @Test
    fun theCatalogueIsParsed() {
        val index = """
            {"version":1,"regions":[
              {"id":"goa","name":"Goa","bbox":[73.6,14.9,74.4,15.9],"size":5000000,"built":"2026-10-10","manifest":"goa/manifest.json"},
              {"id":"kerala","name":"Kerala","bbox":[74.8,8.2,77.5,12.9],"size":9000000,"built":"2026-10-10","manifest":"kerala/manifest.json"}
            ]}
        """.trimIndent()
        val entries = RegionJson.parseIndex(index)
        assertEquals(listOf("goa", "kerala"), entries.map { it.id })
        assertEquals(5_000_000L, entries[0].sizeBytes)
        assertEquals("2026-10-10", entries[1].built)
    }

    @Test
    fun aCatalogueEntryCannotPointAtAnotherManifestPath() {
        val index = """
            {"version":1,"regions":[
              {"id":"goa","name":"Goa","bbox":[73.6,14.9,74.4,15.9],"size":5,"built":"x","manifest":"../secret/manifest.json"}
            ]}
        """.trimIndent()
        assertThrows(ManifestException::class.java) { RegionJson.parseIndex(index) }
        assertThrows(ManifestException::class.java) { RegionJson.parseIndex("""{"version":1}""") }
    }
}
