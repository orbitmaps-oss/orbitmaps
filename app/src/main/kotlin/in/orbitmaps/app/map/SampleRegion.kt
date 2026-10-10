// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import `in`.orbitmaps.core.model.LatLon

/**
 * The development sample region: about 20 × 20 km around Panaji, Goa, extracted by
 * pipeline/sample_region/fetch_sample_region.py and bundled in debug builds only.
 */
object SampleRegion {
    /** Path inside the APK assets (debug builds only). */
    const val ASSET_PATH = "regions/panaji.pmtiles"

    /** Installed location, relative to `Context.filesDir`. */
    const val INSTALLED_PATH = "regions/panaji.pmtiles"

    const val INITIAL_ZOOM = 13.0

    val center = LatLon(15.4909, 73.8278)

    /** Same bbox as the extract (73.734,15.401,73.921,15.581). */
    val southWest = LatLon(15.401, 73.734)
    val northEast = LatLon(15.581, 73.921)

    fun contains(point: LatLon): Boolean = point.latitude in southWest.latitude..northEast.latitude &&
        point.longitude in southWest.longitude..northEast.longitude
}
