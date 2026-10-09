// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.places

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import `in`.orbitmaps.app.map.SampleRegion
import `in`.orbitmaps.app.search.CategoryGroup
import `in`.orbitmaps.app.search.IndexedPlace
import `in`.orbitmaps.app.search.OfflineSearch
import `in`.orbitmaps.app.search.SearchText
import `in`.orbitmaps.app.search.installSampleSearch
import `in`.orbitmaps.app.ui.sample.SampleData
import `in`.orbitmaps.app.ui.sample.SamplePlace
import `in`.orbitmaps.app.ui.sample.Status
import `in`.orbitmaps.core.model.LatLon
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A place as the UI shows it, from the offline index or the sample data. */
data class PlaceItem(
    val id: String,
    val name: String,
    @StringRes val category: Int,
    val distanceKm: Double,
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

/** Ids of places from the offline index, as used in [in.orbitmaps.app.ui.shell.Destination]. */
object PlaceIds {
    private const val INDEX_PREFIX = "osm:"

    fun forIndex(rowId: Long) = "$INDEX_PREFIX$rowId"

    fun indexRowId(id: String): Long? =
        if (id.startsWith(INDEX_PREFIX)) id.removePrefix(INDEX_PREFIX).toLongOrNull() else null
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
 * The app's places: the installed offline index when there is one, otherwise the sample places.
 * Distances are measured from the region centre until the app knows the map centre or the user's
 * position. Nothing here uses the network or logs a query.
 */
class AppPlaces(private val application: Application, private val center: LatLon = SampleRegion.center) :
    PlaceRepository,
    Closeable {
    private val sample = SamplePlaces(application::getString)
    private val mutex = Mutex()
    private var opened = false
    private var index: OfflineSearch? = null

    private suspend fun index(): OfflineSearch? = mutex.withLock {
        if (!opened) {
            opened = true
            index = withContext(Dispatchers.IO) {
                val file = installSampleSearch(application) ?: return@withContext null
                try {
                    OfflineSearch(file)
                } catch (e: IllegalStateException) {
                    null
                } catch (e: IOException) {
                    null
                } catch (e: androidx.sqlite.SQLiteException) {
                    null
                }
            }
        }
        index
    }

    override suspend fun search(text: String, group: CategoryGroup?): SearchResults {
        val index = index() ?: return sample.search(text, group)
        if (text.isBlank() && group == null) return SearchResults(emptyList(), fromOfflineIndex = true)
        val hits = withContext(Dispatchers.IO) { index.search(text, center, group) }
        return SearchResults(hits.map { item(it.place, it.distanceKm) }, fromOfflineIndex = true)
    }

    override suspend fun place(id: String): PlaceItem? {
        val rowId = PlaceIds.indexRowId(id) ?: return sample.place(id)
        val index = index() ?: return null
        val place = withContext(Dispatchers.IO) { index.place(rowId) } ?: return null
        return item(place, center.distanceKmTo(place.location))
    }

    private fun item(place: IndexedPlace, distanceKm: Double) = PlaceItem(
        id = PlaceIds.forIndex(place.id),
        name = place.name,
        category = SearchText.categoryLabel(place.category),
        distanceKm = distanceKm
    )

    override fun close() {
        index?.close()
        index = null
    }
}

/** Keeps one [AppPlaces] (and its open index) across configuration changes. */
class PlacesViewModel(application: Application) : AndroidViewModel(application) {
    val places = AppPlaces(application)

    override fun onCleared() = places.close()
}
