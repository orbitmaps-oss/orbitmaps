// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.places

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.sqlite.SQLiteException
import `in`.orbitmaps.app.map.SampleRegion
import `in`.orbitmaps.app.regions.InstalledRegion
import `in`.orbitmaps.app.regions.PackRole
import `in`.orbitmaps.app.search.CategoryGroup
import `in`.orbitmaps.app.search.IndexedPlace
import `in`.orbitmaps.app.search.OfflineSearch
import `in`.orbitmaps.app.search.SearchHit
import `in`.orbitmaps.app.search.SearchText
import `in`.orbitmaps.app.search.ShardHit
import `in`.orbitmaps.app.search.ShardSearch
import `in`.orbitmaps.app.search.installSampleSearch
import `in`.orbitmaps.app.search.installWorldPlaces
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.app.ui.sample.SamplePlace
import `in`.orbitmaps.app.ui.sample.Status
import `in`.orbitmaps.core.model.LatLon
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A place as the UI shows it, from an offline index or the sample data. */
data class PlaceItem(
    val id: String,
    val name: String,
    @StringRes val category: Int,
    val distanceKm: Double,
    /** Where the place is ("Maharashtra, India"), for places outside the downloaded regions. */
    val detail: String? = null,
    /** The place's position, for routing to it; null for places without one. */
    val location: LatLon? = null,
    /** Community status; null for OpenStreetMap data, which has no New/Confirmed label. */
    val status: Status? = null,
    val confirmations: Int = 0,
    val rating: Double? = null,
    /** Sample places come with sample hours, phone and tags, and a "sample data" note. */
    val isSample: Boolean = false
)

data class SearchResults(val places: List<PlaceItem>, val fromOfflineIndex: Boolean)

interface PlaceRepository {
    suspend fun search(text: String, group: CategoryGroup?): SearchResults

    suspend fun place(id: String): PlaceItem?
}

/** Which index a place comes from. */
enum class PlaceSource(val prefix: String) {
    /** A downloaded region (the sample region for now). */
    Region("osm:"),

    /** The bundled world places index. */
    World("world:"),

    /** An area search shard, fetched on demand and cached. */
    Area("area:"),

    /** A downloaded region pack's search index; ids are `pack:<region id>:<row>`. */
    Pack("pack:")
}

/**
 * A parsed place id: the index, the row in it, for [PlaceSource.Area] the shard's cell, and for
 * [PlaceSource.Pack] the region's id.
 */
data class PlaceRef(val source: PlaceSource, val rowId: Long, val cell: Int? = null, val packId: String? = null)

/** Ids of places from the indexes, as used in [in.orbitmaps.app.ui.shell.Destination]. */
object PlaceIds {
    fun forIndex(rowId: Long, source: PlaceSource = PlaceSource.Region): String {
        require(source != PlaceSource.Area) { "area ids need a cell: use forArea" }
        require(source != PlaceSource.Pack) { "pack ids need a region: use forPack" }
        return "${source.prefix}$rowId"
    }

    fun forArea(cell: Int, rowId: Long) = "${PlaceSource.Area.prefix}$cell:$rowId"

    fun forPack(packId: String, rowId: Long) = "${PlaceSource.Pack.prefix}$packId:$rowId"

    /** The index and row, or null for sample ids and malformed ids. */
    fun parse(id: String): PlaceRef? {
        val source = PlaceSource.entries.firstOrNull { id.startsWith(it.prefix) } ?: return null
        val rest = id.removePrefix(source.prefix)
        if (source == PlaceSource.Pack) {
            val packId = rest.substringBefore(':', "").ifEmpty { return null }
            val rowId = rest.substringAfter(':', "").toLongOrNull() ?: return null
            return PlaceRef(source, rowId, packId = packId)
        }
        if (source != PlaceSource.Area) return rest.toLongOrNull()?.let { PlaceRef(source, it) }
        val cell = rest.substringBefore(':', "").toIntOrNull() ?: return null
        val rowId = rest.substringAfter(':', "").toLongOrNull() ?: return null
        return PlaceRef(source, rowId, cell)
    }
}

/** A world result this close to another result with the same name is the same place. */
private const val SAME_PLACE_KM = 10.0

/** A hit from the search index of the downloaded region [packId]. */
data class PackHit(val packId: String, val hit: SearchHit)

/** One merged result: where it came from, the hit, the shard cell for area results, the region for packs. */
data class MergedHit(val source: PlaceSource, val hit: SearchHit, val cell: Int? = null, val packId: String? = null) {
    val id: String
        get() = when {
            cell != null -> PlaceIds.forArea(cell, hit.place.id)
            packId != null -> PlaceIds.forPack(packId, hit.place.id)
            else -> PlaceIds.forIndex(hit.place.id, source)
        }
}

/**
 * Merges region, pack, area-shard and world results: area results that a downloaded region also has
 * (same OSM object) are dropped, and so are world places another result already names nearby. Keeps
 * the best [limit] by score; on a tie, region before pack before area before world.
 */
internal fun mergeResults(
    region: List<SearchHit>,
    world: List<SearchHit>,
    limit: Int,
    area: List<ShardHit> = emptyList(),
    packs: List<PackHit> = emptyList()
): List<MergedHit> {
    // Overlapping regions can hold the same OSM object; the first one listed wins.
    val seenPackOsm = mutableSetOf<String>()
    val packHits = packs.filter { seenPackOsm.add(it.hit.place.osm) }
    val regionOsm = region.map { it.place.osm }.toSet() + seenPackOsm
    val local = region.map { MergedHit(PlaceSource.Region, it) } +
        packHits.map { MergedHit(PlaceSource.Pack, it.hit, packId = it.packId) } +
        area.filterNot { it.hit.place.osm in regionOsm }.map { MergedHit(PlaceSource.Area, it.hit, it.cell) }
    val distinctWorld = world.filterNot { w ->
        local.any { r ->
            r.hit.place.name.equals(w.place.name, ignoreCase = true) &&
                r.hit.place.location.distanceKmTo(w.place.location) < SAME_PLACE_KM
        }
    }
    return (local + distinctWorld.map { MergedHit(PlaceSource.World, it) })
        .sortedWith(compareByDescending<MergedHit> { it.hit.score }.thenBy { it.source.ordinal })
        .take(limit)
}

/** The sample places, for previews and builds without a search index. [resolve] reads string resources. */
class SamplePlaces(private val resolve: (Int) -> String) : PlaceRepository {
    fun item(place: SamplePlace) = PlaceItem(
        id = place.id,
        name = resolve(place.name),
        category = place.category,
        distanceKm = place.distanceKm,
        status = place.status,
        confirmations = place.confirmations,
        rating = place.rating,
        location = LatLon(place.lat, place.lon),
        isSample = true
    )

    override suspend fun search(text: String, group: CategoryGroup?): SearchResults {
        val query = text.trim()
        val places = SampleData.places
            .filter { group == null || it.category == group.label }
            .filter { resolve(it.name).contains(query, ignoreCase = true) }
            .map(::item)
        return SearchResults(places, fromOfflineIndex = false)
    }

    override suspend fun place(id: String): PlaceItem? = SampleData.places.firstOrNull { it.id == id }?.let(::item)
}

/**
 * The app's places: the installed region index, area search shards around the map centre (fetched
 * when online use is allowed) and the bundled world places index, otherwise the sample places.
 * Everything is searched on the phone; queries never leave it and are never logged.
 */
class AppPlaces(private val application: Application) :
    PlaceRepository,
    Closeable {
    /** Where results are ranked from and shards are fetched for: the map centre, kept up to date by the map. */
    @Volatile var center: LatLon = SampleRegion.center

    /** Whether fetching our files is allowed and possible now ("Browse undownloaded areas" and online). */
    @Volatile var onlineAllowed: Boolean = false

    /** On Wi-Fi, the neighbouring shards are fetched too. */
    @Volatile var unmetered: Boolean = false

    private val sample = SamplePlaces(application::getString)
    private val shards = ShardSearch(ShardSearch.defaultRoot(application.filesDir))
    private val mutex = Mutex()
    private var opened = false
    private val indexes = mutableMapOf<PlaceSource, OfflineSearch>()
    private val packIndexes = mutableMapOf<String, OfflineSearch>()

    /** The downloaded regions, from the Offline regions page; their search indexes are opened on demand. */
    @Volatile var packs: List<InstalledRegion> = emptyList()

    private suspend fun indexes(): Map<PlaceSource, OfflineSearch> = mutex.withLock {
        if (!opened) {
            opened = true
            installSampleSearch(application)?.let { open(it) }?.let { indexes[PlaceSource.Region] = it }
            installWorldPlaces(application)?.let { open(it) }?.let { indexes[PlaceSource.World] = it }
        }
        indexes
    }

    /** Opens the search index of each installed pack and closes the ones that were deleted. */
    private suspend fun packIndexes(): Map<String, OfflineSearch> = mutex.withLock {
        val current = packs.filter { it.hasFile(PackRole.Search) }
        val ids = current.map { it.id }.toSet()
        packIndexes.keys.filterNot { it in ids }.forEach { packIndexes.remove(it)?.close() }
        current.filter { it.id !in packIndexes }.forEach { pack ->
            open(pack.file(PackRole.Search))?.let { packIndexes[pack.id] = it }
        }
        packIndexes.toMap()
    }

    private suspend fun open(file: File): OfflineSearch? = withContext(Dispatchers.IO) {
        try {
            OfflineSearch(file)
        } catch (e: IllegalStateException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: SQLiteException) {
            null
        }
    }

    override suspend fun search(text: String, group: CategoryGroup?): SearchResults {
        val indexes = indexes()
        val packIndexes = packIndexes()
        if (text.isBlank() && group == null) {
            return if (indexes.isEmpty() && packIndexes.isEmpty()) {
                sample.search(
                    text,
                    group
                )
            } else {
                SearchResults(emptyList(), fromOfflineIndex = true)
            }
        }
        val from = center
        val merged = withContext(Dispatchers.IO) {
            val region = indexes[PlaceSource.Region]?.search(text, from, group).orEmpty()
            val packHits = packIndexes.entries.sortedBy { it.key }.flatMap { (id, index) ->
                index.search(text, from, group).map { PackHit(id, it) }
            }
            val area = shards.search(text, from, group, fetch = onlineAllowed, neighbours = unmetered)
            // The world index only has cities and towns, so it can't answer category searches.
            val world = if (group == null) indexes[PlaceSource.World]?.search(text, from).orEmpty() else emptyList()
            mergeResults(region, world, OfflineSearch.DEFAULT_LIMIT, area, packHits)
        }
        if (indexes.isEmpty() && packIndexes.isEmpty() && merged.isEmpty()) return sample.search(text, group)
        return SearchResults(merged.map { item(it.id, it.hit.place, it.hit.distanceKm) }, fromOfflineIndex = true)
    }

    override suspend fun place(id: String): PlaceItem? {
        val ref = PlaceIds.parse(id) ?: return sample.place(id)
        val place = withContext(Dispatchers.IO) {
            if (ref.source == PlaceSource.Area) {
                shards.place(checkNotNull(ref.cell), ref.rowId)
            } else if (ref.source == PlaceSource.Pack) {
                packIndexes()[ref.packId]?.place(ref.rowId)
            } else {
                indexes()[ref.source]?.place(ref.rowId)
            }
        } ?: return null
        return item(id, place, center.distanceKmTo(place.location))
    }

    private fun item(id: String, place: IndexedPlace, distanceKm: Double) = PlaceItem(
        id = id,
        name = place.name,
        category = SearchText.categoryLabel(place.category),
        distanceKm = distanceKm,
        detail = place.detail,
        location = place.location
    )

    override fun close() {
        indexes.values.forEach { it.close() }
        indexes.clear()
        packIndexes.values.forEach { it.close() }
        packIndexes.clear()
        shards.close()
    }
}

/** Keeps one [AppPlaces] (and its open indexes) across configuration changes. */
class PlacesViewModel(application: Application) : AndroidViewModel(application) {
    val places = AppPlaces(application)

    override fun onCleared() = places.close()
}
