// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.places

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.sqlite.SQLiteException
import `in`.orbitmaps.app.map.SampleRegion
import `in`.orbitmaps.app.search.CategoryGroup
import `in`.orbitmaps.app.search.IndexedPlace
import `in`.orbitmaps.app.search.OfflineSearch
import `in`.orbitmaps.app.search.SearchHit
import `in`.orbitmaps.app.search.SearchText
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

/** Which offline index a place comes from. */
enum class PlaceSource(val prefix: String) {
    Region("osm:"),
    World("world:")
}

/** Ids of places from the offline indexes, as used in [in.orbitmaps.app.ui.shell.Destination]. */
object PlaceIds {
    fun forIndex(rowId: Long, source: PlaceSource = PlaceSource.Region) = "${source.prefix}$rowId"

    /** The index and row id, or null for sample ids and malformed ids. */
    fun parse(id: String): Pair<PlaceSource, Long>? {
        val source = PlaceSource.entries.firstOrNull { id.startsWith(it.prefix) } ?: return null
        val rowId = id.removePrefix(source.prefix).toLongOrNull() ?: return null
        return source to rowId
    }
}

/** A world result this close to a region result with the same name is the same place. */
private const val SAME_PLACE_KM = 10.0

/**
 * Merges region and world results: drops world places that the region index already has, then keeps
 * the best [limit] by score. Region places come first on a tie.
 */
internal fun mergeResults(
    region: List<SearchHit>,
    world: List<SearchHit>,
    limit: Int
): List<Pair<PlaceSource, SearchHit>> {
    val distinctWorld = world.filterNot { w ->
        region.any { r ->
            r.place.name.equals(w.place.name, ignoreCase = true) &&
                r.place.location.distanceKmTo(w.place.location) < SAME_PLACE_KM
        }
    }
    return (region.map { PlaceSource.Region to it } + distinctWorld.map { PlaceSource.World to it })
        .sortedWith(compareByDescending<Pair<PlaceSource, SearchHit>> { it.second.score }.thenBy { it.first.ordinal })
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
 * The app's places: the installed region index and the world places index when the build has them,
 * otherwise the sample places. Distances are measured from the region centre until the app knows the
 * map centre or the user's position. Nothing here uses the network or logs a query.
 */
class AppPlaces(private val application: Application, private val center: LatLon = SampleRegion.center) :
    PlaceRepository,
    Closeable {
    private val sample = SamplePlaces(application::getString)
    private val mutex = Mutex()
    private var opened = false
    private val indexes = mutableMapOf<PlaceSource, OfflineSearch>()

    private suspend fun indexes(): Map<PlaceSource, OfflineSearch> = mutex.withLock {
        if (!opened) {
            opened = true
            installSampleSearch(application)?.let { open(it) }?.let { indexes[PlaceSource.Region] = it }
            installWorldPlaces(application)?.let { open(it) }?.let { indexes[PlaceSource.World] = it }
        }
        indexes
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
        if (indexes.isEmpty()) return sample.search(text, group)
        if (text.isBlank() && group == null) return SearchResults(emptyList(), fromOfflineIndex = true)
        val merged = withContext(Dispatchers.IO) {
            val region = indexes[PlaceSource.Region]?.search(text, center, group).orEmpty()
            // The world index only has cities and towns, so it can't answer category searches.
            val world = if (group == null) indexes[PlaceSource.World]?.search(text, center).orEmpty() else emptyList()
            mergeResults(region, world, OfflineSearch.DEFAULT_LIMIT)
        }
        return SearchResults(
            merged.map { (source, hit) ->
                item(source, hit.place, hit.distanceKm)
            },
            fromOfflineIndex = true
        )
    }

    override suspend fun place(id: String): PlaceItem? {
        val (source, rowId) = PlaceIds.parse(id) ?: return sample.place(id)
        val index = indexes()[source] ?: return null
        val place = withContext(Dispatchers.IO) { index.place(rowId) } ?: return null
        return item(source, place, center.distanceKmTo(place.location))
    }

    private fun item(source: PlaceSource, place: IndexedPlace, distanceKm: Double) = PlaceItem(
        id = PlaceIds.forIndex(place.id, source),
        name = place.name,
        category = SearchText.categoryLabel(place.category),
        distanceKm = distanceKm,
        detail = place.detail
    )

    override fun close() {
        indexes.values.forEach { it.close() }
        indexes.clear()
    }
}

/** Keeps one [AppPlaces] (and its open indexes) across configuration changes. */
class PlacesViewModel(application: Application) : AndroidViewModel(application) {
    val places = AppPlaces(application)

    override fun onCleared() = places.close()
}
