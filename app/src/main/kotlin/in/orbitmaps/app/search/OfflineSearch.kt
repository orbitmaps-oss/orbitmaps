// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import `in`.orbitmaps.app.map.InstallResult
import `in`.orbitmaps.app.map.RegionInstaller
import `in`.orbitmaps.app.map.installAsset
import `in`.orbitmaps.core.model.LatLon
import java.io.Closeable
import java.io.File

/** One place from the index. [id] is the row id in `places`. */
data class IndexedPlace(
    val id: Long,
    val osm: String,
    val name: String,
    val nameEn: String?,
    val category: String,
    val location: LatLon,
    val importance: Int,
    /** Where the place is ("Maharashtra, India"); null for region places. */
    val detail: String?
)

data class SearchHit(val place: IndexedPlace, val distanceKm: Double, val score: Double)

/** The search index of the development sample region, bundled in debug builds only. */
object SampleSearch {
    const val ASSET = "regions/panaji-search.sqlite"
    const val INSTALLED_PATH = "regions/panaji-search.sqlite"
}

/** The world places index (cities and towns everywhere), bundled in every build when generated. */
object WorldPlaces {
    const val ASSET = "places/world-places.sqlite"
    const val INSTALLED_PATH = "places/world-places.sqlite"
}

/** Installs the sample search index, or returns null if this build doesn't bundle one. */
suspend fun installSampleSearch(context: Context): File? =
    installIndex(context, SampleSearch.ASSET, SampleSearch.INSTALLED_PATH)

/** Installs the world places index, or returns null if this build doesn't bundle one. */
suspend fun installWorldPlaces(context: Context): File? =
    installIndex(context, WorldPlaces.ASSET, WorldPlaces.INSTALLED_PATH)

private suspend fun installIndex(context: Context, asset: String, installedPath: String): File? =
    when (val result = installAsset(context, asset, installedPath, RegionInstaller::hasSqliteHeader)) {
        is InstallResult.Installed -> result.file
        else -> null
    }

/**
 * Offline place search over a region's SQLite FTS5 index (built by
 * pipeline/sample_region/build_sample_search.py). Read-only; all calls block, so use a background
 * dispatcher. One connection, used by one caller at a time. Queries are never logged.
 */
class OfflineSearch(file: File) : Closeable {
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(file.absolutePath, SQLITE_OPEN_READONLY)
    private val lock = Any()

    init {
        val version = try {
            meta("schema_version")?.toIntOrNull()
        } catch (e: SQLiteException) {
            connection.close()
            throw e
        }
        if (version != SCHEMA_VERSION) {
            connection.close()
            throw IllegalStateException("search index schema $version, expected $SCHEMA_VERSION")
        }
    }

    fun meta(key: String): String? = synchronized(lock) {
        connection.prepare("SELECT value FROM meta WHERE key = ?").use { st ->
            st.bindText(1, key)
            if (st.step()) st.getText(0) else null
        }
    }

    /**
     * Text search (every word as a prefix), optionally limited to a category group, ranked by
     * [SearchText.score]. With no words but a group, returns that group's places nearest [center].
     */
    fun search(
        text: String,
        center: LatLon,
        group: CategoryGroup? = null,
        limit: Int = DEFAULT_LIMIT
    ): List<SearchHit> {
        val match = SearchText.ftsQuery(text)
        val (groupSql, groupArgs) = group?.sqlCondition() ?: ("" to emptyList())
        val candidates = synchronized(lock) {
            when {
                match != null -> query(
                    "SELECT $COLUMNS, bm25(places_fts) FROM places_fts JOIN places p ON p.id = places_fts.rowid " +
                        "WHERE places_fts MATCH ?" + (if (group != null) " AND $groupSql" else "") +
                        " ORDER BY bm25(places_fts) LIMIT $CANDIDATES",
                    listOf(match) + groupArgs
                )
                group != null -> query(
                    "SELECT $COLUMNS, 0.0 FROM places p WHERE $groupSql " +
                        "ORDER BY (p.lat - ?) * (p.lat - ?) + (p.lon - ?) * (p.lon - ?) LIMIT $CANDIDATES",
                    groupArgs,
                    listOf(center.latitude, center.latitude, center.longitude, center.longitude)
                )
                else -> emptyList()
            }
        }
        return candidates
            .map { (place, bm25) ->
                val distance = center.distanceKmTo(place.location)
                SearchHit(place, distance, SearchText.score(place.name, text, bm25, place.importance, distance))
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun place(id: Long): IndexedPlace? = synchronized(lock) {
        query("SELECT $COLUMNS, 0.0 FROM places p WHERE p.id = ?", emptyList(), longs = listOf(id)).firstOrNull()?.first
    }

    private fun query(
        sql: String,
        texts: List<String>,
        doubles: List<Double> = emptyList(),
        longs: List<Long> = emptyList()
    ): List<Pair<IndexedPlace, Double>> = connection.prepare(sql).use { st ->
        var index = 1
        texts.forEach { st.bindText(index++, it) }
        doubles.forEach { st.bindDouble(index++, it) }
        longs.forEach { st.bindLong(index++, it) }
        buildList {
            while (st.step()) {
                val place = st.toPlace() ?: continue
                add(place to st.getDouble(COLUMN_COUNT))
            }
        }
    }

    private fun SQLiteStatement.toPlace(): IndexedPlace? {
        val location = LatLon.orNull(getDouble(5), getDouble(6)) ?: return null
        return IndexedPlace(
            id = getLong(0),
            osm = getText(1),
            name = getText(2),
            nameEn = if (isNull(3)) null else getText(3),
            category = getText(4),
            location = location,
            importance = getLong(7).toInt(),
            detail = if (isNull(8)) null else getText(8)
        )
    }

    override fun close() = synchronized(lock) { connection.close() }

    companion object {
        /** Must match SCHEMA_VERSION in build_sample_search.py. */
        const val SCHEMA_VERSION = 2
        const val DEFAULT_LIMIT = 30
        private const val CANDIDATES = 200
        private const val COLUMNS = "p.id, p.osm, p.name, p.name_en, p.category, p.lat, p.lon, p.importance, p.detail"
        private const val COLUMN_COUNT = 9
    }
}
