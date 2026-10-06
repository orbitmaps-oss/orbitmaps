// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app

/** Links required by the data licences in docs/DATA_SOURCES.md. */
object Attribution {
    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
    const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
    const val SOURCE_CODE_URL = "https://github.com/orbitmaps-oss/orbitmaps"

    val all: List<String> = listOf(OSM_COPYRIGHT_URL, ODBL_URL, SOURCE_CODE_URL)
}
