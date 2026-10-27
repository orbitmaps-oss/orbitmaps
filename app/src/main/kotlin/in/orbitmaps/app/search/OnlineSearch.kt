// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import `in`.orbitmaps.app.net.HttpFiles
import `in`.orbitmaps.core.model.LatLon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A place from online search. [label] is the short form for lists, [fullName] OpenStreetMap's whole address. */
data class OnlinePlace(val label: String, val fullName: String, val location: LatLon)

/**
 * Online place search over OpenStreetMap's public Nominatim (the one third-party service the app
 * uses, see PRIVACY.md). A request carries the typed text and a box around the map centre, never the
 * GPS position, and no cookies or identifiers. Search runs only when the user submits (Nominatim's
 * usage policy forbids autocomplete) and at most once a second. Nothing is logged or kept.
 */
object OnlineSearch {
    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"
    private const val TIMEOUT_MS = 5_000
    private const val MAX_BODY_CHARS = 512 * 1024
    private const val MIN_INTERVAL_MS = 1_000L
    const val LIMIT = 5

    /** Half the side of the box around the map centre that results are preferred in, in degrees (about 25 km). */
    private const val BOX_DEGREES = 0.25

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var lastRequestAt = 0L

    /** Nominatim's `viewbox` (left, top, right, bottom) around [center], kept inside the world. */
    fun viewbox(center: LatLon, degrees: Double = BOX_DEGREES): String {
        val left = (center.longitude - degrees).coerceAtLeast(LatLon.MIN_LONGITUDE)
        val right = (center.longitude + degrees).coerceAtMost(LatLon.MAX_LONGITUDE)
        val top = (center.latitude + degrees).coerceAtMost(LatLon.MAX_LATITUDE)
        val bottom = (center.latitude - degrees).coerceAtLeast(LatLon.MIN_LATITUDE)
        return "$left,$top,$right,$bottom"
    }

    /** The request URL: text, the box around [center] (only results inside it when [bounded]), JSON, [LIMIT] hits. */
    fun url(text: String, center: LatLon, bounded: Boolean): String =
        "$ENDPOINT?q=${URLEncoder.encode(text, "UTF-8")}&viewbox=${viewbox(center)}" +
            (if (bounded) "&bounded=1" else "") + "&format=json&limit=$LIMIT&addressdetails=1"

    /** Parses Nominatim's JSON reply; entries without a valid position are skipped, bad JSON gives an empty list. */
    fun parse(body: String): List<OnlinePlace> {
        val array = try {
            json.parseToJsonElement(body) as? JsonArray
        } catch (e: SerializationException) {
            null
        } ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            // Nominatim sends coordinates as strings; a number's content parses the same way.
            val latitude = item["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val longitude = item["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val location = LatLon.orNull(latitude ?: return@mapNotNull null, longitude ?: return@mapNotNull null)
                ?: return@mapNotNull null
            val full = item["display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            OnlinePlace(minimalLabel(full), full, location)
        }
    }

    /**
     * Shortens an OpenStreetMap address ("Name, Street, Suburb, City, State, 403001, India") to the name
     * plus one area: a house number keeps the next two parts, otherwise the last part before the final
     * two (postcode and country) that has no digits, usually the state. A leading Plus Code is dropped.
     */
    fun minimalLabel(fullAddress: String): String {
        val parts = fullAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return fullAddress
        val start = if (parts[0].contains("+") && parts[0].length < 12) 1 else 0
        if (parts.size <= start + 1) return parts.getOrElse(start) { fullAddress }
        val first = parts[start]
        if (first.any { it.isDigit() } && first.length < 6) {
            return parts.drop(start).take(if (parts.size > start + 2) 3 else 2).joinToString(", ")
        }
        for (i in (parts.size - 3) downTo (start + 1)) {
            if (parts[i].none { it.isDigit() }) return "$first, ${parts[i]}"
        }
        return "$first, ${parts[start + 1]}"
    }

    private val latLon = Regex("(?::|@|q=|ll=)?([-+]?\\d+\\.\\d+)[,%2C\\s]+([-+]?\\d+\\.\\d+)")
    private val mapLinkLatLon = Regex("!3d([-+]?\\d+\\.\\d+)!4d([-+]?\\d+\\.\\d+)")

    /**
     * A position written in [text]: "15.4909, 73.8278", "@15.49,73.82" or a pasted map link that holds one.
     * Done on the phone, so pasting coordinates never needs a request. Links are not opened.
     */
    fun coordinatesIn(text: String): LatLon? {
        for (regex in listOf(latLon, mapLinkLatLon)) {
            val match = regex.find(text) ?: continue
            val latitude = match.groupValues[1].toDoubleOrNull() ?: continue
            val longitude = match.groupValues[2].toDoubleOrNull() ?: continue
            LatLon.orNull(latitude, longitude)?.let { return it }
        }
        return null
    }

    /**
     * Searches near [center]: first only inside the box around it, and if that finds nothing, anywhere
     * with the box as the preferred area. Returns null when the request failed (offline, timeout, server
     * error), so the caller can fall back to the offline search. Blocking: call off the main thread.
     */
    fun search(text: String, center: LatLon): List<OnlinePlace>? {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val local = fetch(url(query, center, bounded = true)) ?: return null
        if (local.isNotEmpty()) return local
        return fetch(url(query, center, bounded = false))
    }

    private fun fetch(url: String): List<OnlinePlace>? {
        awaitTurn()
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", HttpFiles.USER_AGENT)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(8 * 1024)
                    val text = StringBuilder()
                    while (text.length < MAX_BODY_CHARS) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        text.append(buffer, 0, read)
                    }
                    text.toString()
                }
                parse(body)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            null
        }
    }

    /** Keeps requests at least [MIN_INTERVAL_MS] apart, as Nominatim's usage policy requires. */
    private fun awaitTurn() {
        val wait = synchronized(lock) {
            val now = System.currentTimeMillis()
            val at = maxOf(now, lastRequestAt + MIN_INTERVAL_MS)
            lastRequestAt = at
            at - now
        }
        if (wait > 0) Thread.sleep(wait)
    }
}
