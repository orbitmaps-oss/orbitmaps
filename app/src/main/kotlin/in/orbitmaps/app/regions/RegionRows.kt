// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

/** Why a download didn't start or didn't finish. */
enum class RegionProblem { Offline, NeedsWifi, NoSpace, Unavailable, Corrupt }

sealed interface RowState {
    data object Available : RowState

    data class Downloading(val doneBytes: Long, val totalBytes: Long) : RowState {
        val percent: Int get() = if (totalBytes > 0) (doneBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
    }

    data class Installed(val built: String) : RowState

    data class Failed(val problem: RegionProblem) : RowState
}

/** One line of the Offline regions page. */
data class RegionRow(val id: String, val name: String, val sizeBytes: Long, val state: RowState)

/** The page's state: the rows, and whether the catalogue could be loaded at all. */
data class RegionsUi(
    val rows: List<RegionRow> = emptyList(),
    val loading: Boolean = false,
    val catalogUnavailable: Boolean = false
)

object RegionRows {
    /**
     * The rows to show: every catalogue entry, plus installed regions the catalogue doesn't list
     * (so they can still be deleted offline). A download in progress or a failure ([transient]) wins
     * over "available"; an installed region shows as installed even while an update downloads.
     */
    fun build(
        catalogue: List<CatalogEntry>?,
        installed: List<InstalledRegion>,
        transient: Map<String, RowState>
    ): List<RegionRow> {
        val byId = installed.associateBy { it.id }
        val rows = mutableMapOf<String, RegionRow>()
        for (entry in catalogue.orEmpty()) {
            val have = byId[entry.id]
            val state = transient[entry.id]
                ?: have?.let { RowState.Installed(it.manifest.built) }
                ?: RowState.Available
            rows[entry.id] = RegionRow(entry.id, entry.name, entry.sizeBytes, state)
        }
        for (region in installed) {
            if (region.id !in rows) {
                val state = transient[region.id] ?: RowState.Installed(region.manifest.built)
                rows[region.id] = RegionRow(region.id, region.manifest.name, region.manifest.totalBytes, state)
            }
        }
        return rows.values.sortedBy { it.name.lowercase() }
    }

    /** Why a download can't start now, or null: no connection, or mobile data with Wi-Fi only on. */
    fun blockedBy(online: Boolean, unmetered: Boolean, wifiOnly: Boolean): RegionProblem? = when {
        !online -> RegionProblem.Offline
        wifiOnly && !unmetered -> RegionProblem.NeedsWifi
        else -> null
    }
}
