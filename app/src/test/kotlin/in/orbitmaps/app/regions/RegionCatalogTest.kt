// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RegionCatalogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String
    private val served = mutableMapOf<String, String>()

    private val sha = "b".repeat(64)
    private val index = """
        {"version":1,"regions":[{"id":"goa","name":"Goa","bbox":[73.6,14.9,74.4,15.9],
          "size":1230,"built":"2026-10-10","manifest":"goa/manifest.json"}]}
    """.trimIndent()
    private val manifest = """
        {"version":1,"id":"goa","name":"Goa","built":"2026-10-10","bbox":[73.6,14.9,74.4,15.9],
         "licence":"ODbL-1.0","attribution":"x",
         "files":[{"role":"map","name":"map.pmtiles","size":1230,"sha256":"$sha"}]}
    """.trimIndent()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val body = served[exchange.requestURI.path]?.toByteArray()
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
        served["/index.json"] = index
        served["/goa/manifest.json"] = manifest
    }

    @After
    fun stop() = server.stop(0)

    private fun catalog() = RegionCatalog(tmp.newFolder("cache-${System.nanoTime()}"), base)

    @Test
    fun theCatalogueIsLoadedAndCached() {
        val catalog = catalog()
        val first = catalog.load(allowNetwork = true) as RegionCatalog.Result.Loaded
        assertEquals(listOf("goa"), first.entries.map { it.id })
        assertEquals(false, first.fromCache)

        served.clear() // the server is gone
        val second = catalog.load(allowNetwork = true) as RegionCatalog.Result.Loaded
        assertEquals(listOf("goa"), second.entries.map { it.id })
        assertTrue(second.fromCache)
    }

    @Test
    fun offlineWithNothingCachedIsUnavailable() {
        assertEquals(RegionCatalog.Result.Unavailable, catalog().load(allowNetwork = false))
        served.clear()
        assertEquals(RegionCatalog.Result.Unavailable, catalog().load(allowNetwork = true))
    }

    @Test
    fun aBrokenCatalogueFallsBackToTheCachedOne() {
        val catalog = catalog()
        catalog.load(allowNetwork = true)
        served["/index.json"] = "<html>captive portal</html>"
        val result = catalog.load(allowNetwork = true) as RegionCatalog.Result.Loaded
        assertTrue(result.fromCache)
        assertEquals(listOf("goa"), result.entries.map { it.id })
    }

    @Test
    fun aManifestIsFetchedAndMustMatchTheEntry() {
        val catalog = catalog()
        val entry = (catalog.load(true) as RegionCatalog.Result.Loaded).entries.single()
        val loaded = catalog.manifest(entry)
        assertNotNull(loaded)
        assertEquals("goa", loaded!!.first.id)
        assertEquals(manifest, loaded.second)

        served["/goa/manifest.json"] = manifest.replace("\"id\":\"goa\"", "\"id\":\"kerala\"")
        assertNull(catalog.manifest(entry))
        served.remove("/goa/manifest.json")
        assertNull(catalog.manifest(entry))
    }
}
