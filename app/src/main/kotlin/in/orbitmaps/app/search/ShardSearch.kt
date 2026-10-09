// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import androidx.sqlite.SQLiteException
import `in`.orbitmaps.app.net.OnlineData
import `in`.orbitmaps.app.routing.Box
import `in`.orbitmaps.app.routing.GraphTile
import `in`.orbitmaps.app.routing.TileStore
import `in`.orbitmaps.app.routing.ValhallaTiles
import `in`.orbitmaps.core.model.LatLon
import java.io.Closeable
import java.io.File

/** A hit from one area shard; [cell] says which shard, for looking the place up again. */
data class ShardHit(val cell: Int, val hit: SearchHit)

/**
 * Search anywhere without a search server: the area around [center] is covered by small search
 * shards (0.25° cells on Valhalla's level-2 grid, built by pipeline/sample_region/build_search_shards.py),
 * downloaded from [OnlineData.SEARCH_SHARDS_URL] and searched on the phone. The server learns which
 * areas are searched, never the query. Shards stay cached for offline use. Blocking: call off the
 * main thread.
 *
 * @param open opens a shard; replaceable in tests, where the bundled SQLite driver can't load.
 */
class ShardSearch(
    root: File,
    baseUrl: String = OnlineData.SEARCH_SHARDS_URL,
    private val open: (File) -> Searchable = { OfflineSearch(it) }
) : Closeable {
    /** The parts of [OfflineSearch] used here. */
    interface Searchable : Closeable {
        fun search(
            text: String,
            center: LatLon,
            group: CategoryGroup? = null,
            limit: Int = OfflineSearch.DEFAULT_LIMIT
        ): List<SearchHit>

        fun place(id: Long): IndexedPlace?
    }

    val shards = TileStore(root, baseUrl, extension = "sqlite")
    private val opened = mutableMapOf<GraphTile, Searchable>()
    private val lock = Any()

    /** The cell under [center], plus its eight neighbours when [neighbours] (e.g. on Wi-Fi). */
    fun cellsAround(center: LatLon, neighbours: Boolean): List<GraphTile> {
        val here = GraphTile(LEVEL, ValhallaTiles.tileId(LEVEL, center))
        if (!neighbours) return listOf(here)
        val around = ValhallaTiles.tilesIn(LEVEL, Box.around(listOf(center)).padded(CELL_DEGREES))
        return listOf(here) + (around - here).sortedBy { it.id }
    }

    /**
     * Fetches missing shards around [center] when [fetch] is true (online and allowed), then searches
     * every shard that is here. Results from neighbouring cells are merged and ranked together.
     */
    fun search(
        text: String,
        center: LatLon,
        group: CategoryGroup? = null,
        fetch: Boolean,
        neighbours: Boolean,
        limit: Int = OfflineSearch.DEFAULT_LIMIT
    ): List<ShardHit> {
        val cells = cellsAround(center, neighbours)
        if (fetch) shards.ensure(cells)
        return cells
            .flatMap { cell -> shard(cell)?.search(text, center, group, limit).orEmpty().map { ShardHit(cell.id, it) } }
            .sortedByDescending { it.hit.score }
            .take(limit)
    }

    fun place(cell: Int, id: Long): IndexedPlace? = shard(GraphTile(LEVEL, cell))?.place(id)

    private fun shard(cell: GraphTile): Searchable? = synchronized(lock) {
        opened[cell]?.let { return it }
        if (!shards.has(cell)) return null
        try {
            open(shards.file(cell)).also { opened[cell] = it }
        } catch (e: IllegalStateException) {
            shards.file(cell).delete() // wrong schema or damaged: fetched again next time
            null
        } catch (e: SQLiteException) {
            shards.file(cell).delete()
            null
        }
    }

    override fun close() = synchronized(lock) {
        opened.values.forEach { it.close() }
        opened.clear()
    }

    companion object {
        const val LEVEL = 2
        const val CELL_DEGREES = 0.25

        fun defaultRoot(filesDir: File) = File(filesDir, "search/v2")
    }
}
