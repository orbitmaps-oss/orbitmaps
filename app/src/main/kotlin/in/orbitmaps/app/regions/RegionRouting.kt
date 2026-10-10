// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.app.routing.ValhallaJson
import java.io.File
import java.io.IOException

/** Valhalla config for an installed region pack. */
object RegionRouting {
    /**
     * Writes `valhalla.json` into the pack's folder, pointing Valhalla at the pack's `routing.tar`, and
     * returns it; null when the pack has no routing data or its config is unusable. The file goes
     * away with the pack when it is deleted.
     */
    fun configFor(region: InstalledRegion): File? {
        if (!region.hasFile(PackRole.Routing) || !region.hasFile(PackRole.RoutingConfig)) return null
        return try {
            val text = ValhallaJson.deviceConfig(
                region.file(PackRole.RoutingConfig).readText(),
                tileExtract = region.file(PackRole.Routing),
                dataDir = File(region.dir, "valhalla-data")
            )
            File(region.dir, "valhalla.json").also { it.writeText(text) }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
