// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import com.sun.net.httpserver.HttpServer
import `in`.orbitmaps.app.routing.GraphTile
import `in`.orbitmaps.app.routing.ValhallaTiles
import `in`.orbitmaps.core.model.LatLon
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShardSearchTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val served = mutableMapOf<String, String>()
    private val requests = CopyOnWriteArrayList<String>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path.removePrefix("/v1/search/v2")
            requests += path
            val body = served[path]?.toByteArray()
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/v1/search/v2"
    }

    @After
    fun stop() = server.stop(0)

    private val panaji = LatLon(15.4989, 73.8278)
    private val here = GraphTile(2, ValhallaTiles.tileId(2, panaji))

    /** A fake shard whose file content is the name of its one place. */
    private class FakeShard(private val file: File) : ShardSearch.Searchable {
        private val place get() = IndexedPlace(
            1,
            "n${file.readText().hashCode()}",
            file.readText(),
            null,
            "amenity=cafe",
            LatLon(15.5, 73.8),
            30,
            null
        )

        override fun search(text: String, center: LatLon, group: CategoryGroup?, limit: Int): List<SearchHit> =
            if (place.name.contains(
                    text,
                    ignoreCase = true
                )
            ) {
                listOf(SearchHit(place, 1.0, place.name.length.toDouble()))
            } else {
                emptyList()
            }

        override fun place(id: Long) = if (id == 1L) place else null

        override fun close() = Unit
    }

    private fun search(root: File = tmp.root) = ShardSearch(root, baseUrl) { FakeShard(it) }

    private fun path(tile: GraphTile) = "/${tile.level}/${ValhallaTiles.fileSuffix(tile.level, tile.id)}.sqlite"

    @Test
    fun theCellUnderTheCentreComesFirstAndNeighboursMakeNine() {
        val cells = search().cellsAround(panaji, neighbours = true)
        assertEquals(here, cells.first())
        assertEquals(9, cells.size)
        assertEquals(listOf(here), search().cellsAround(panaji, neighbours = false))
    }

    @Test
    fun searchFetchesTheShardThenSearchesItOnThePhone() {
        served[path(here)] = "Café Bhosle"
        val hits = search().search("bhosle", panaji, fetch = true, neighbours = false)
        assertEquals(listOf("Café Bhosle"), hits.map { it.hit.place.name })
        assertEquals(here.id, hits.single().cell)
        assertEquals("the query is never sent", listOf(path(here)), requests)
    }

    @Test
    fun withoutPermissionNothingIsFetchedButCachedShardsStillWork() {
        served[path(here)] = "Café Bhosle"
        val shards = search()
        assertEquals(emptyList<ShardHit>(), shards.search("bhosle", panaji, fetch = false, neighbours = false))
        assertEquals(emptyList<String>(), requests)

        shards.search("bhosle", panaji, fetch = true, neighbours = false)
        requests.clear()
        val offline = search().search("bhosle", panaji, fetch = false, neighbours = false)
        assertEquals(1, offline.size)
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun neighbouringShardsAreMergedByScore() {
        val cells = search().cellsAround(panaji, neighbours = true)
        served[path(cells[0])] = "Cafe"
        served[path(cells[1])] = "Cafe Longer Name"
        val hits = search().search("cafe", panaji, fetch = true, neighbours = true)
        assertEquals(listOf("Cafe Longer Name", "Cafe"), hits.map { it.hit.place.name })
        assertEquals(9, requests.size)
    }

    @Test
    fun placesAreLookedUpInTheirShard() {
        served[path(here)] = "Café Bhosle"
        val shards = search()
        shards.search("cafe", panaji, fetch = true, neighbours = false)
        assertEquals("Café Bhosle", shards.place(here.id, 1)?.name)
        assertNull(shards.place(here.id + 1, 1))
    }

    @Test
    fun aShardThatWontOpenIsDeletedAndFetchedAgainLater() {
        served[path(here)] = "broken"
        val shards = ShardSearch(tmp.root, baseUrl) { throw IllegalStateException("search index schema 1, expected 2") }
        assertEquals(emptyList<ShardHit>(), shards.search("x", panaji, fetch = true, neighbours = false))
        assertTrue(!shards.shards.has(here))
    }
}
