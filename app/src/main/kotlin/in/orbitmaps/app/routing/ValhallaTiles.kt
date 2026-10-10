// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import `in`.orbitmaps.core.model.LatLon
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** One Valhalla graph tile: hierarchy [level] (0 highways, 1 arterials, 2 local roads) and its [id]. */
data class GraphTile(val level: Int, val id: Int) {
    /** The tile's file under a Valhalla tile_dir, e.g. "2/000/818/660.gph". */
    val path: String get() = "$level/${ValhallaTiles.fileSuffix(level, id)}.gph"
}

/** A latitude/longitude box. */
data class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun padded(degrees: Double) = Box(
        (south - degrees).coerceAtLeast(-90.0),
        (west - degrees).coerceAtLeast(-180.0),
        (north + degrees).coerceAtMost(90.0),
        (east + degrees).coerceAtMost(180.0)
    )

    companion object {
        fun around(points: List<LatLon>): Box {
            require(points.isNotEmpty()) { "need at least one point" }
            return Box(
                points.minOf { it.latitude },
                points.minOf { it.longitude },
                points.maxOf { it.latitude },
                points.maxOf { it.longitude }
            )
        }
    }
}

/**
 * Valhalla's tiling: the world split into square tiles of 4°, 1° and 0.25° for hierarchy levels 0, 1
 * and 2 (baldr/tilehierarchy.h). Tile ids count row by row from (-90, -180). Pure functions, so the
 * download planning is unit-tested without the engine.
 */
object ValhallaTiles {
    val LEVEL_SIZES = mapOf(0 to 4.0, 1 to 1.0, 2 to 0.25)

    private fun size(level: Int) = requireNotNull(LEVEL_SIZES[level]) { "unknown level $level" }

    fun columns(level: Int) = (360.0 / size(level)).toInt()

    fun rows(level: Int) = (180.0 / size(level)).toInt()

    fun tileId(level: Int, point: LatLon): Int {
        val s = size(level)
        val row = min(floor((point.latitude + 90.0) / s).toInt(), rows(level) - 1)
        val column = min(floor((point.longitude + 180.0) / s).toInt(), columns(level) - 1)
        return row * columns(level) + column
    }

    /** Every tile of [level] that touches [box]. */
    fun tilesIn(level: Int, box: Box): Set<GraphTile> {
        val s = size(level)
        val cols = columns(level)
        val firstRow = max(0, floor((box.south + 90.0) / s).toInt())
        val lastRow = min(rows(level) - 1, floor((box.north + 90.0) / s).toInt())
        val firstCol = max(0, floor((box.west + 180.0) / s).toInt())
        val lastCol = min(cols - 1, floor((box.east + 180.0) / s).toInt())
        return buildSet {
            for (row in firstRow..lastRow) for (col in firstCol..lastCol) add(GraphTile(level, row * cols + col))
        }
    }

    /**
     * The id zero-padded to a multiple of three digits, wide enough for the level's last id, and split
     * into groups of three: GraphTile::FileSuffix in Valhalla.
     */
    fun fileSuffix(level: Int, id: Int): String {
        val count = columns(level) * rows(level)
        require(id in 0 until count) { "tile $id out of range for level $level" }
        var digits = floor(log10(count.toDouble())).toInt() + 1
        if (digits % 3 != 0) digits += 3 - digits % 3
        return id.toString().padStart(digits, '0').chunked(3).joinToString("/")
    }
}

/**
 * Which tiles a trip needs. Valhalla routes long trips on the highway level and only expands into
 * local roads near the start and destination, so planning needs far less than everything in between.
 */
object TripTiles {
    /** Highways across the whole trip, padded so a detour to a nearby motorway is possible. */
    private const val HIGHWAY_PADDING_DEGREES = 0.5

    /** Arterial roads around the start and destination (about 30 km). */
    private const val ARTERIAL_RADIUS_DEGREES = 0.3

    /** Local roads around the start and destination (about 5 km). */
    private const val LOCAL_RADIUS_DEGREES = 0.05

    /** Corridor kept for offline rerouting: local roads ~2 km and arterials ~10 km either side. */
    private const val CORRIDOR_LOCAL_DEGREES = 0.02
    private const val CORRIDOR_ARTERIAL_DEGREES = 0.1

    /** Tiles needed to calculate a route between [from] and [to]. */
    fun forPlanning(from: LatLon, to: LatLon): Set<GraphTile> = buildSet {
        addAll(ValhallaTiles.tilesIn(0, Box.around(listOf(from, to)).padded(HIGHWAY_PADDING_DEGREES)))
        for (end in listOf(from, to)) {
            addAll(ValhallaTiles.tilesIn(1, Box.around(listOf(end)).padded(ARTERIAL_RADIUS_DEGREES)))
            addAll(ValhallaTiles.tilesIn(2, Box.around(listOf(end)).padded(LOCAL_RADIUS_DEGREES)))
        }
    }

    /** Tiles along a calculated route's [shape], so the trip and rerouting work without signal. */
    fun forCorridor(shape: List<LatLon>): Set<GraphTile> = buildSet {
        for (point in shape) {
            val here = Box.around(listOf(point))
            addAll(ValhallaTiles.tilesIn(2, here.padded(CORRIDOR_LOCAL_DEGREES)))
            addAll(ValhallaTiles.tilesIn(1, here.padded(CORRIDOR_ARTERIAL_DEGREES)))
            add(GraphTile(0, ValhallaTiles.tileId(0, point)))
        }
    }
}

/** Decodes Valhalla's route shape: Google's polyline algorithm with 6 decimal places. */
object Polyline6 {
    private const val PRECISION = 1e6

    fun decode(encoded: String): List<LatLon> {
        val points = mutableListOf<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = readValue(encoded, index)
            val (dLon, afterLon) = readValue(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            LatLon.orNull(lat / PRECISION, lon / PRECISION)?.let(points::add)
        }
        return points
    }

    private fun readValue(encoded: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        while (true) {
            require(index < encoded.length) { "truncated polyline" }
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }
}
