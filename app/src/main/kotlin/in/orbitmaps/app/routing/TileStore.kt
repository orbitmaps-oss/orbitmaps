// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import `in`.orbitmaps.app.net.Download
import `in`.orbitmaps.app.net.HttpFiles
import java.io.File

/** What [TileStore.ensure] did. [failed] tiles could not be fetched (offline, server error). */
data class FetchReport(val downloaded: Int, val alreadyHere: Int, val absent: Int, val failed: Int, val bytes: Long) {
    val complete: Boolean get() = failed == 0
}

/**
 * Files on Valhalla's tile grid in a folder, downloaded on demand from [baseUrl]: graph tiles in
 * Valhalla's tile_dir layout ([extension] "gph"), or search shards ("sqlite") on the same grid.
 *
 * Requests carry only the file path ([HttpFiles]). Files that don't exist on the server (open sea) are
 * remembered for [ABSENT_DAYS], so they aren't asked for again. Nothing is logged.
 */
class TileStore(
    val dir: File,
    private val baseUrl: String,
    private val extension: String = "gph",
    private val clock: () -> Long = System::currentTimeMillis
) {
    private fun relativePath(tile: GraphTile) =
        "${tile.level}/${ValhallaTiles.fileSuffix(tile.level, tile.id)}.$extension"

    fun file(tile: GraphTile) = File(dir, relativePath(tile))

    private fun absentMarker(tile: GraphTile) = File(dir, "${relativePath(tile)}.absent")

    fun has(tile: GraphTile): Boolean = file(tile).isFile

    private fun knownAbsent(tile: GraphTile): Boolean {
        val marker = absentMarker(tile)
        return marker.isFile && clock() - marker.lastModified() < ABSENT_DAYS * DAY_MS
    }

    /** Makes sure every tile in [tiles] is here or known to be absent. Blocking: call off the main thread. */
    fun ensure(tiles: Collection<GraphTile>): FetchReport {
        var downloaded = 0
        var here = 0
        var absent = 0
        var failed = 0
        var bytes = 0L
        for (tile in tiles.sortedWith(compareBy({ it.level }, { it.id }))) {
            when {
                has(tile) -> {
                    here++
                    file(tile).setLastModified(clock()) // recently used: keep it in cleanup
                }
                knownAbsent(tile) -> absent++
                else -> when (val result = fetch(tile)) {
                    is Fetch.Saved -> {
                        downloaded++
                        bytes += result.bytes
                    }
                    Fetch.NotFound -> absent++
                    Fetch.Failed -> failed++
                }
            }
        }
        return FetchReport(downloaded, here, absent, failed, bytes)
    }

    private fun fetch(tile: GraphTile): Fetch =
        when (val result = HttpFiles.download("$baseUrl/${relativePath(tile)}", file(tile))) {
            is Download.Saved -> {
                absentMarker(tile).delete()
                Fetch.Saved(result.bytes)
            }
            Download.NotFound -> {
                absentMarker(tile).apply { parentFile?.mkdirs() }.writeText("")
                Fetch.NotFound
            }
            Download.Failed -> Fetch.Failed
        }

    private sealed interface Fetch {
        data class Saved(val bytes: Long) : Fetch

        data object NotFound : Fetch

        data object Failed : Fetch
    }

    /** Total size of the stored tiles. */
    fun sizeBytes(): Long = tileFiles().sumOf { it.length() }

    /**
     * Deletes the least recently used tiles until the store is under [maxBytes], never touching
     * [keep] (the tiles of saved or active trips). Returns how many tiles were deleted.
     */
    fun cleanUp(maxBytes: Long, keep: Set<GraphTile> = emptySet()): Int {
        val keepPaths = keep.map { file(it).absolutePath }.toSet()
        var total = sizeBytes()
        var deleted = 0
        for (file in tileFiles().sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (file.absolutePath in keepPaths) continue
            val size = file.length()
            if (file.delete()) {
                total -= size
                deleted++
            }
        }
        return deleted
    }

    private fun tileFiles(): List<File> = dir.walkTopDown().filter {
        it.isFile && it.name.endsWith(".$extension")
    }.toList()

    companion object {
        const val ABSENT_DAYS = 30L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
