// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.regions

import `in`.orbitmaps.core.model.LatLon
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A region pack's files. The file names are fixed, so a manifest can't point the app at other paths. */
enum class PackRole(val key: String, val fileName: String) {
    Map("map", "map.pmtiles"),
    Routing("routing", "routing.tar"),
    RoutingConfig("routing_config", "routing.json"),
    Search("search", "search.sqlite")
}

data class PackFile(val role: PackRole, val size: Long, val sha256: String)

data class RegionBounds(val southWest: LatLon, val northEast: LatLon) {
    val center: LatLon
        get() = LatLon(
            (southWest.latitude + northEast.latitude) / 2,
            (southWest.longitude + northEast.longitude) / 2
        )

    fun contains(point: LatLon): Boolean = point.latitude in southWest.latitude..northEast.latitude &&
        point.longitude in southWest.longitude..northEast.longitude
}

/** What the catalogue says about one available region. */
data class CatalogEntry(
    val id: String,
    val name: String,
    val bounds: RegionBounds,
    val sizeBytes: Long,
    val built: String
)

/** A pack's manifest: the files with their sizes and checksums, the bounds and the licence. */
data class RegionManifest(
    val id: String,
    val name: String,
    val built: String,
    val bounds: RegionBounds,
    val licence: String,
    val attribution: String,
    val files: List<PackFile>
) {
    val totalBytes: Long get() = files.sumOf { it.size }

    fun file(role: PackRole): PackFile? = files.firstOrNull { it.role == role }
}

/** The catalogue or a manifest is damaged, from a newer format, or tries something it shouldn't. */
class ManifestException(message: String) : Exception(message)

/** Reads the JSON written by pipeline/sample_region/build_region_pack.py. Strict on purpose. */
object RegionJson {
    const val FORMAT_VERSION = 1
    private val ID = Regex("^[a-z0-9][a-z0-9-]{0,40}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private const val MAX_NAME = 80

    fun parseIndex(text: String): List<CatalogEntry> {
        val root = parse(text)
        checkVersion(root)
        val regions = root["regions"] as? JsonArray ?: throw ManifestException("catalogue has no regions")
        return regions.map { element ->
            val entry = element as? JsonObject ?: throw ManifestException("catalogue entry is not an object")
            val id = id(entry)
            // The manifest always lives next to its files; anything else is refused.
            if (entry.string("manifest") != "$id/manifest.json") throw ManifestException("unexpected manifest path")
            CatalogEntry(
                id = id,
                name = name(entry),
                bounds = bounds(entry),
                sizeBytes = entry.long("size").also { if (it <= 0) throw ManifestException("size must be positive") },
                built = entry.string("built")
            )
        }
    }

    fun parseManifest(text: String): RegionManifest {
        val root = parse(text)
        checkVersion(root)
        val filesJson = root["files"] as? JsonArray ?: throw ManifestException("manifest has no files")
        val files = filesJson.map { element ->
            val file = element as? JsonObject ?: throw ManifestException("file entry is not an object")
            val role = PackRole.entries.firstOrNull { it.key == file.string("role") }
                ?: throw ManifestException("unknown file role")
            if (file.string("name") != role.fileName) throw ManifestException("unexpected file name for ${role.key}")
            val size = file.long("size")
            if (size <= 0) throw ManifestException("file size must be positive")
            val sha = file.string("sha256")
            if (!SHA256.matches(sha)) throw ManifestException("bad checksum")
            PackFile(role, size, sha)
        }
        if (files.map { it.role }.toSet().size != files.size) throw ManifestException("duplicate file role")
        if (files.none { it.role == PackRole.Map }) throw ManifestException("manifest has no map")
        val licence = root.string("licence")
        if (licence.isBlank()) throw ManifestException("manifest has no licence")
        return RegionManifest(
            id = id(root),
            name = name(root),
            built = root.string("built"),
            bounds = bounds(root),
            licence = licence,
            attribution = root.string("attribution"),
            files = files
        )
    }

    private fun parse(text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        throw ManifestException("not valid JSON")
    } catch (e: IllegalArgumentException) {
        throw ManifestException("not a JSON object")
    }

    private fun checkVersion(root: JsonObject) {
        val version = root["version"]?.jsonPrimitive?.intOrNull
        if (version != FORMAT_VERSION) throw ManifestException("unsupported format version $version")
    }

    private fun id(obj: JsonObject): String {
        val id = obj.string("id")
        if (!ID.matches(id)) throw ManifestException("bad region id")
        return id
    }

    private fun name(obj: JsonObject): String {
        val name = obj.string("name").trim()
        if (name.isEmpty() || name.length > MAX_NAME) throw ManifestException("bad region name")
        return name
    }

    private fun bounds(obj: JsonObject): RegionBounds {
        val box = (obj["bbox"] as? JsonArray)?.map { it.jsonPrimitive.doubleOrNull }
        if (box == null || box.size != 4 || box.any { it == null }) throw ManifestException("bad bounds")
        val (west, south, east, north) = box.map { it!! }
        val sw = LatLon.orNull(south, west)
        val ne = LatLon.orNull(north, east)
        if (sw == null || ne == null || west >= east || south >= north) throw ManifestException("bad bounds")
        return RegionBounds(sw, ne)
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: throw ManifestException("missing $key")

    private fun JsonObject.long(key: String): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: throw ManifestException("missing $key")
}
