// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.app.net.Download
import `in`.orbitmaps.app.net.HttpFiles
import `in`.orbitmaps.app.net.OnlineData
import java.io.File
import java.io.IOException

/**
 * The list of downloadable regions: `index.json` on our static host, cached on the phone so the list
 * still shows offline. The request names only the file; it never carries the user's position.
 */
class RegionCatalog(private val cacheDir: File, private val baseUrl: String = OnlineData.REGIONS_URL) {
    sealed interface Result {
        data class Loaded(val entries: List<CatalogEntry>, val fromCache: Boolean) : Result

        /** Nothing cached and the server can't be reached (or its list is unreadable). */
        data object Unavailable : Result
    }

    private val cached get() = File(cacheDir, "index.json")

    /** Blocking: call off the main thread. [allowNetwork] false reads the cached copy only. */
    fun load(allowNetwork: Boolean): Result {
        if (allowNetwork) {
            val fresh = File(cacheDir, "index.new.json")
            if (HttpFiles.download("$baseUrl/index.json", fresh) is Download.Saved) {
                val entries = parse(fresh)
                if (entries != null) {
                    cached.delete()
                    fresh.renameTo(cached)
                    return Result.Loaded(entries, fromCache = false)
                }
                fresh.delete()
            }
        }
        val entries = if (cached.isFile) parse(cached) else null
        return if (entries != null) Result.Loaded(entries, fromCache = true) else Result.Unavailable
    }

    /** The manifest of [entry] with its exact text (stored beside the files), or null. Blocking. */
    fun manifest(entry: CatalogEntry): Pair<RegionManifest, String>? {
        val file = File(cacheDir, "${entry.id}.manifest.json")
        if (HttpFiles.download("$baseUrl/${entry.id}/manifest.json", file) !is Download.Saved) return null
        return try {
            val text = file.readText()
            val manifest = RegionJson.parseManifest(text)
            if (manifest.id != entry.id) null else manifest to text
        } catch (e: ManifestException) {
            null
        } catch (e: IOException) {
            null
        } finally {
            file.delete()
        }
    }

    private fun parse(file: File): List<CatalogEntry>? = try {
        RegionJson.parseIndex(file.readText())
    } catch (e: ManifestException) {
        null
    } catch (e: IOException) {
        null
    }
}
