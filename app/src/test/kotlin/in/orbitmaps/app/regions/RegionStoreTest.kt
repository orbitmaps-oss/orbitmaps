// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Region installs against a local server standing in for data.orbitmaps.in. */
class RegionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String
    private val served = mutableMapOf<String, ByteArray>()
    private val ranges = CopyOnWriteArrayList<String?>()

    /** When set, the next response sends only this many bytes of the file, then drops the connection. */
    @Volatile
    private var dropAfter: Int? = null

    private val map = ByteArray(300_000) { (it % 251).toByte() }
    private val routing = ByteArray(50_000) { (it % 13).toByte() }
    private val search = ByteArray(20_000) { (it % 7).toByte() }

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range
            val body = served[path]
            try {
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
                    val slice = body.copyOfRange(from, body.size)
                    exchange.sendResponseHeaders(if (range != null) 206 else 200, slice.size.toLong())
                    val limit = dropAfter
                    dropAfter = null
                    exchange.responseBody.use { out ->
                        if (limit == null) out.write(slice) else out.write(slice, 0, minOf(limit, slice.size))
                    }
                }
            } catch (e: IOException) {
                // The client went away or we cut it off on purpose.
            }
            exchange.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
        served["/goa/map.pmtiles"] = map
        served["/goa/routing.tar"] = routing
        served["/goa/search.sqlite"] = search
    }

    @After
    fun stop() = server.stop(0)

    private fun manifest(version: String = "2026-10-10", mapSha: String = sha(map)): Pair<RegionManifest, String> {
        val text = """
            {"version":1,"id":"goa","name":"Goa","built":"$version","bbox":[73.6,14.9,74.4,15.9],
             "licence":"ODbL-1.0","attribution":"© OpenStreetMap contributors","files":[
              {"role":"map","name":"map.pmtiles","size":${map.size},"sha256":"$mapSha"},
              {"role":"routing","name":"routing.tar","size":${routing.size},"sha256":"${sha(routing)}"},
              {"role":"search","name":"search.sqlite","size":${search.size},"sha256":"${sha(search)}"}
            ]}
        """.trimIndent()
        return RegionJson.parseManifest(text) to text
    }

    private fun store() = RegionStore(tmp.newFolder("regions-${System.nanoTime()}"))

    private val plenty = Long.MAX_VALUE / 4

    @Test
    fun aPackIsDownloadedVerifiedAndInstalled() {
        val store = store()
        val (m, text) = manifest()
        val progress = mutableListOf<Pair<Long, Long>>()
        val result = store.install(m, text, base, plenty, onProgress = { done, total -> progress += done to total })

        assertTrue(result is PackInstall.Installed)
        val region = store.get("goa")
        assertNotNull(region)
        assertEquals(listOf("goa"), store.installed().map { it.id })
        assertTrue(region!!.file(PackRole.Map).readBytes().contentEquals(map))
        assertTrue(region.hasFile(PackRole.Search))
        assertFalse(region.hasFile(PackRole.RoutingConfig))
        assertEquals(m.totalBytes to m.totalBytes, progress.last())
        assertEquals("staging is gone once installed", 0L, store.stagedBytes("goa"))
    }

    @Test
    fun aFileThatDoesNotMatchItsChecksumIsDeletedAndNothingIsInstalled() {
        val store = store()
        val (m, text) = manifest(mapSha = "0".repeat(64))
        val result = store.install(m, text, base, plenty)
        assertEquals(PackInstall.Corrupt("map.pmtiles"), result)
        assertNull(store.get("goa"))
        assertEquals(emptyList<InstalledRegion>(), store.installed())
    }

    @Test
    fun notEnoughSpaceStopsBeforeAnythingIsDownloaded() {
        val store = store()
        val (m, text) = manifest()
        val result = store.install(m, text, base, freeBytes = 1_000)
        assertTrue(result is PackInstall.NoSpace)
        assertTrue((result as PackInstall.NoSpace).neededBytes > 0)
        assertEquals(emptyList<String?>(), ranges)
    }

    @Test
    fun aMissingFileMakesTheRegionUnavailable() {
        val store = store()
        served.remove("/goa/routing.tar")
        val (m, text) = manifest()
        assertEquals(PackInstall.Unavailable, store.install(m, text, base, plenty))
        assertNull(store.get("goa"))
    }

    @Test
    fun aDroppedConnectionResumesWhereItStopped() {
        val store = store()
        val (m, text) = manifest()
        dropAfter = 100_000
        assertEquals(PackInstall.Unavailable, store.install(m, text, base, plenty))
        assertNull("not installed while incomplete", store.get("goa"))
        assertEquals(100_000L, store.stagedBytes("goa"))

        ranges.clear()
        val result = store.install(m, text, base, plenty)
        assertTrue(result is PackInstall.Installed)
        assertEquals("only the rest was asked for", "bytes=100000-", ranges.first())
        assertTrue(store.get("goa")!!.file(PackRole.Map).readBytes().contentEquals(map))
    }

    @Test
    fun cancellingKeepsTheDownloadedPartAndALaterInstallFinishes() {
        val store = store()
        val (m, text) = manifest()
        var polls = 0
        val result = store.install(m, text, base, plenty, shouldContinue = { ++polls < 3 })
        assertEquals(PackInstall.Cancelled, result)
        assertTrue(store.stagedBytes("goa") > 0)
        assertNull(store.get("goa"))

        assertTrue(store.install(m, text, base, plenty) is PackInstall.Installed)
    }

    @Test
    fun aNewerPackReplacesTheOldOneOnlyWhenComplete() {
        val store = store()
        val (v1, text1) = manifest(version = "2026-09-01")
        assertTrue(store.install(v1, text1, base, plenty) is PackInstall.Installed)

        val newMap = ByteArray(310_000) { (it % 199).toByte() }
        served["/goa/map.pmtiles"] = newMap
        val newText = text1.replace("2026-09-01", "2026-10-10")
            .replace("\"size\":${map.size}", "\"size\":${newMap.size}").replace(sha(map), sha(newMap))
        val v2 = RegionJson.parseManifest(newText)
        dropAfter = 50_000
        assertEquals(PackInstall.Unavailable, store.install(v2, newText, base, plenty))
        assertEquals("the old pack still works", "2026-09-01", store.get("goa")?.manifest?.built)

        assertTrue(store.install(v2, newText, base, plenty) is PackInstall.Installed)
        assertEquals("2026-10-10", store.get("goa")?.manifest?.built)
        assertTrue(store.get("goa")!!.file(PackRole.Map).readBytes().contentEquals(newMap))
    }

    @Test
    fun aDamagedInstalledRegionIsNotListed() {
        val store = store()
        val (m, text) = manifest()
        store.install(m, text, base, plenty)
        store.get("goa")!!.file(PackRole.Search).writeBytes(ByteArray(5))
        assertEquals(emptyList<InstalledRegion>(), store.installed())
        assertNull(store.get("goa"))
    }

    @Test
    fun deletingRemovesTheRegionAndUnsafeIdsAreIgnored() {
        val store = store()
        val (m, text) = manifest()
        store.install(m, text, base, plenty)
        store.delete("../goa")
        assertNotNull(store.get("goa"))
        store.delete("goa")
        assertNull(store.get("goa"))
        assertNull(store.get("../../etc"))
        assertEquals(0L, store.stagedBytes(".."))
        assertTrue(File(tmp.root, "x").let { true })
    }
}
