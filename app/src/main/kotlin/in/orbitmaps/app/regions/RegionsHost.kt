// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import androidx.compose.runtime.Immutable

/** What the Offline regions page shows and the actions it can take, passed down as one value. */
@Immutable
class RegionsHost(
    val ui: RegionsUi = RegionsUi(),
    val onRefresh: () -> Unit = {},
    val onDownload: (id: String) -> Unit = {},
    val onCancel: (id: String) -> Unit = {},
    val onDelete: (id: String) -> Unit = {}
) {
    companion object {
        val None = RegionsHost()
    }
}
