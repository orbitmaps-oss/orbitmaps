// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import com.sun.net.httpserver.HttpServer
import `in`.orbitmaps.app.net.HttpFiles
import `in`.orbitmaps.core.model.LatLon
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Downloads against a local HTTP server standing in for data.orbitmaps.in. */
class TripRoutingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    /** Path -> body; anything else is 404, and "/fail/" paths answer 500. */
    private val files = mutableMapOf<String, ByteArray>()
    private val requests = CopyOnWriteArrayList<Pair<String, String?>>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path.removePrefix("/v1/routing")
            requests += path to exchange.requestHeaders.getFirst("User-Agent")
            val body = files[path]
            when {
                path.contains("/fail/") -> exchange.sendResponseHeaders(500, -1)
                body == null -> exchange.sendResponseHeaders(404, -1)
                else -> {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
            exchange.close()
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/v1/routing"
    }

    @After
    fun stop() = server.stop(0)

    private fun tileBody(tile: GraphTile) = "tile ${tile.level}/${tile.id}".toByteArray()

    private fun serve(tiles: Collection<GraphTile>) = tiles.forEach { files["/${it.path}"] = tileBody(it) }

    @Test
    fun ensureDownloadsMissingTilesAndRemembersAbsentOnes() {
        val store = TileStore(tmp.newFolder("tiles"), baseUrl)
        val land = GraphTile(2, 818660)
        val sea = GraphTile(2, 1)
        serve(listOf(land))

        val first = store.ensure(listOf(land, sea))
        assertEquals(
            FetchReport(downloaded = 1, alreadyHere = 0, absent = 1, failed = 0, bytes = tileBody(land).size.toLong()),
            first
        )
        assertTrue(first.complete)
        assertEquals(String(tileBody(land)), store.file(land).readText())

        requests.clear()
        val second = store.ensure(listOf(land, sea))
        assertEquals(FetchReport(downloaded = 0, alreadyHere = 1, absent = 1, failed = 0, bytes = 0), second)
        assertEquals("nothing is asked for again", emptyList<Pair<String, String?>>(), requests)
    }

    @Test
    fun absentTilesAreAskedForAgainAfterAMonth() {
        var now = 1_000_000_000_000L
        val store = TileStore(tmp.newFolder("tiles"), baseUrl, clock = { now })
        val sea = GraphTile(1, 5)
        store.ensure(listOf(sea))
        val marker = File(store.dir, "${sea.path}.absent")
        marker.setLastModified(now)
        now += (TileStore.ABSENT_DAYS + 1) * 24 * 60 * 60 * 1000
        requests.clear()
        store.ensure(listOf(sea))
        assertEquals(listOf("/${sea.path}"), requests.map { it.first })
    }

    @Test
    fun serverErrorsAreReportedAndLeaveNoFiles() {
        val dir = tmp.newFolder("tiles")
        val store = TileStore(dir, "$baseUrl/fail")
        val report = store.ensure(listOf(GraphTile(0, 7)))
        assertEquals(1, report.failed)
        assertFalse(report.complete)
        assertEquals(emptyList<File>(), dir.walkTopDown().filter { it.isFile }.toList())
    }

    @Test
    fun requestsCarryOnlyAGenericUserAgent() {
        val store = TileStore(tmp.newFolder("tiles"), baseUrl)
        store.ensure(listOf(GraphTile(0, 9)))
        assertEquals(HttpFiles.USER_AGENT, requests.single().second)
    }

    @Test
    fun cleanUpDeletesTheOldestTilesButKeepsSavedTrips() {
        var now = 1_000_000_000_000L
        val store = TileStore(tmp.newFolder("tiles"), baseUrl, clock = { now })
        val tiles = (0 until 4).map { GraphTile(2, 1000 + it) }
        serve(tiles)
        tiles.forEachIndexed { i, tile ->
            store.ensure(listOf(tile))
            store.file(tile).setLastModified(now + i * 1000L) // tiles[0] is the oldest
        }
        val one = store.file(tiles[0]).length()
        val deleted = store.cleanUp(maxBytes = 2 * one, keep = setOf(tiles[0]))
        assertEquals(2, deleted)
        assertTrue("kept for a saved trip", store.has(tiles[0]))
        assertFalse(store.has(tiles[1]))
        assertFalse(store.has(tiles[2]))
        assertTrue(store.has(tiles[3]))
    }

    private val template = """
        {"mjolnir":{"tile_dir":"/data/tiles","tile_extract":"/data/tiles.tar","admin":"/data/admins.sqlite"},"loki":{}}
    """.trimIndent()

    private class FakeRouter(private val result: () -> RouteSummary) : TripRouting.Router {
        var routed = 0
        override fun route(from: LatLon, to: LatLon, costing: Costing, language: String?): RouteSummary {
            routed++
            return result()
        }
    }

    private val panaji = LatLon(15.4989, 73.8278)
    private val porvorim = LatLon(15.5395, 73.8135)

    @Test
    fun planFetchesConfigAndTilesThenRoutesOnThePhone() {
        files["/valhalla.json"] = template.toByteArray()
        serve(TripTiles.forPlanning(panaji, porvorim))
        val route = RouteSummary(6.1, 600.0, 7, listOf(panaji, porvorim))
        val fake = FakeRouter { route }
        var configSeen: File? = null
        val root = tmp.newFolder("routing")
        val trips = TripRouting(root, baseUrl) { config ->
            configSeen = config
            fake
        }

        val plan = trips.plan(panaji, porvorim)
        assertTrue(plan is TripPlan.Ready)
        plan as TripPlan.Ready
        assertEquals(route, plan.route)
        assertTrue(plan.tiles.complete)
        assertEquals(TripTiles.forPlanning(panaji, porvorim).size, plan.tiles.downloaded)

        val mjolnir = Json.parseToJsonElement(configSeen!!.readText()).jsonObject.getValue("mjolnir").jsonObject
        assertEquals(trips.tiles.dir.absolutePath, mjolnir.getValue("tile_dir").jsonPrimitive.content)
        assertFalse("tile_extract" in mjolnir)

        // The engine is built once and the config isn't fetched again.
        requests.clear()
        trips.plan(panaji, porvorim)
        assertEquals(2, fake.routed)
        assertEquals(emptyList<String>(), requests.map { it.first })
    }

    @Test
    fun withoutTheConfigThereIsNoEngine() {
        val trips = TripRouting(tmp.newFolder("routing"), baseUrl) { FakeRouter { error("never") } }
        assertEquals(TripPlan.NoEngine, trips.plan(panaji, porvorim))
    }

    @Test
    fun routingErrorsBecomeNoRoute() {
        files["/valhalla.json"] = template.toByteArray()
        val trips = TripRouting(tmp.newFolder("routing"), baseUrl) {
            FakeRouter { throw RoutingException("171: No suitable edges near location") }
        }
        val plan = trips.plan(panaji, porvorim)
        assertTrue(plan is TripPlan.NoRoute)
        assertEquals("171: No suitable edges near location", (plan as TripPlan.NoRoute).reason)
    }

    @Test
    fun saveCorridorDownloadsTheTilesAlongTheRoute() {
        val shape = listOf(panaji, LatLon(15.52, 73.82), porvorim)
        serve(TripTiles.forCorridor(shape))
        val trips = TripRouting(tmp.newFolder("routing"), baseUrl) { FakeRouter { error("unused") } }
        val report = trips.saveCorridor(RouteSummary(6.1, 600.0, 7, shape))
        assertTrue(report.complete)
        assertTrue(TripTiles.forCorridor(shape).all(trips.tiles::has))
    }

    @Test
    fun withFetchOffNothingIsRequestedAndMissingTilesAreCountedAsFailed() {
        files["/valhalla.json"] = template.toByteArray()
        val trips = TripRouting(tmp.newFolder("routing"), baseUrl) { FakeRouter { error("no engine without config") } }
        assertEquals(TripPlan.NoEngine, trips.plan(panaji, porvorim, fetch = false))
        assertEquals("not even the engine config is fetched", emptyList<String>(), requests.map { it.first })
    }

    @Test
    fun withFetchOffAndTheEngineKnownAMissingTileFailsTheRouteAsNeedingData() {
        files["/valhalla.json"] = template.toByteArray()
        serve(TripTiles.forPlanning(panaji, porvorim))
        val root = tmp.newFolder("routing")
        val noRoute = FakeRouter { throw RoutingException("171: No suitable edges near location") }
        val trips = TripRouting(root, baseUrl) { noRoute }
        // First online, so the engine exists; then forget one tile and go offline.
        assertTrue(trips.plan(panaji, porvorim) is TripPlan.NoRoute)
        val someTile = TripTiles.forPlanning(panaji, porvorim).first { trips.tiles.has(it) }
        trips.tiles.file(someTile).delete()
        requests.clear()
        val plan = trips.plan(panaji, porvorim, fetch = false)
        assertTrue(plan is TripPlan.NoRoute)
        assertEquals(1, (plan as TripPlan.NoRoute).tiles.failed)
        assertEquals(emptyList<String>(), requests.map { it.first })
    }
}
