// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

import `in`.orbitmaps.app.map.ATTRIBUTION_TEXT
import `in`.orbitmaps.app.map.OfflineStyle
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionTest {
    private val osmAttribution = "© OpenStreetMap contributors"

    @Test
    fun allLinksUseHttps() {
        Attribution.all.forEach { url -> assertTrue(url, url.startsWith("https://")) }
    }

    @Test
    fun osmLinkPointsToCopyrightPage() {
        assertEquals("https://www.openstreetmap.org/copyright", Attribution.OSM_COPYRIGHT_URL)
    }

    @Test
    fun sourceLinkPointsToProjectRepository() {
        assertEquals("https://github.com/orbitmaps-oss/orbitmaps", Attribution.SOURCE_CODE_URL)
    }

    @Test
    fun osmAttributionStringIsExact() {
        val resDir = File(requireNotNull(System.getProperty("orbitmaps.resDir")) { "run through Gradle" })
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "values/strings.xml"))
        val strings = doc.getElementsByTagName("string")
        val value = (0 until strings.length).map { strings.item(it) }
            .single { it.attributes.getNamedItem("name").nodeValue == "osm_attribution" }.textContent
        assertEquals(osmAttribution, value)
    }

    @Test
    fun mapOverlayShowsTheOsmAttributionString() {
        assertEquals(R.string.osm_attribution, ATTRIBUTION_TEXT)
    }

    @Test
    fun bundledStyleSourceCarriesOsmAttribution() {
        val stylesDir = File(requireNotNull(System.getProperty("orbitmaps.stylesDir")) { "run through Gradle" })
        val style = Json.parseToJsonElement(File(stylesDir, OfflineStyle.ASSET_PATH).readText()).jsonObject
        val source = style.getValue("sources").jsonObject.getValue(OfflineStyle.SOURCE_ID).jsonObject
        assertEquals(osmAttribution, source.getValue("attribution").jsonPrimitive.content)
    }
}
