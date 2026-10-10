// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.core.model.LatLon

/** Chooses which installed region serves the map and routes. Pure, so it is easy to test. */
object RegionPicker {
    /**
     * The installed region to show the map from: [currentId] while it still contains [center] (so the
     * map doesn't jump between overlapping regions), else the smallest region containing it, else the
     * first installed region (the camera is moved into it). Null when nothing is installed.
     */
    fun forMap(installed: List<InstalledRegion>, center: LatLon?, currentId: String? = null): InstalledRegion? {
        if (installed.isEmpty()) return null
        val sorted = installed.sortedBy { it.id }
        if (center == null) return sorted.firstOrNull { it.id == currentId } ?: sorted.first()
        val containing = sorted.filter { it.manifest.bounds.contains(center) }
        return containing.firstOrNull { it.id == currentId }
            ?: containing.minByOrNull { it.manifest.bounds.area() }
            ?: sorted.firstOrNull { it.id == currentId }
            ?: sorted.first()
    }

    /** The smallest installed region with routing data that contains both [from] and [to], or null. */
    fun forRoute(installed: List<InstalledRegion>, from: LatLon, to: LatLon): InstalledRegion? = installed
        .filter { it.hasFile(PackRole.Routing) && it.hasFile(PackRole.RoutingConfig) }
        .filter { it.manifest.bounds.contains(from) && it.manifest.bounds.contains(to) }
        .minByOrNull { it.manifest.bounds.area() }

    private fun RegionBounds.area() =
        (northEast.latitude - southWest.latitude) * (northEast.longitude - southWest.longitude)
}
