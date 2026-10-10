// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import `in`.orbitmaps.app.routing.Polyline6
import `in`.orbitmaps.app.routing.RoutingException
import `in`.orbitmaps.core.model.LatLon
import kotlin.math.cos
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One step of a route, from Valhalla's maneuver list. [beginIndex] is where it starts on the route
 * line (an index into [NavRoute.shape]). The instructions are Valhalla's, in the requested language.
 */
data class Maneuver(
    val type: Int,
    val instruction: String,
    /** Spoken just before the maneuver, e.g. "Turn right onto MG Road." */
    val verbalPre: String?,
    /** Spoken early, e.g. "Turn right onto MG Road." used with a distance, after the previous step. */
    val verbalAlert: String?,
    val streetNames: List<String>,
    val lengthKm: Double,
    val timeSeconds: Double,
    val beginIndex: Int
) {
    val isDestination: Boolean get() = type in DESTINATION_TYPES

    companion object {
        /** Valhalla's kDestination, kDestinationRight and kDestinationLeft. */
        val DESTINATION_TYPES = setOf(4, 5, 6)
    }
}

/** A route ready to follow: its line, cumulative distances along it, and its steps. */
class NavRoute(val shape: List<LatLon>, val maneuvers: List<Maneuver>) {
    init {
        require(shape.size >= 2) { "a route needs at least two points" }
        require(maneuvers.isNotEmpty()) { "a route needs at least one maneuver" }
        require(maneuvers.all { it.beginIndex in shape.indices }) { "maneuver outside the route line" }
    }

    /** Distance in metres from the start to each point of [shape]. */
    val cumulativeM: DoubleArray = DoubleArray(shape.size).also { cum ->
        for (i in 1 until shape.size) cum[i] = cum[i - 1] + metres(shape[i - 1], shape[i])
    }

    val lengthM: Double get() = cumulativeM.last()

    val timeSeconds: Double get() = maneuvers.sumOf { it.timeSeconds }

    /** Index of the maneuver whose step contains route-line segment [segment]. */
    fun maneuverAt(segment: Int): Int {
        var index = 0
        for (i in maneuvers.indices) if (maneuvers[i].beginIndex <= segment) index = i
        return index
    }

    companion object {
        /** Reads Valhalla's native JSON route (all legs joined). */
        fun fromValhalla(response: String): NavRoute {
            val root = try {
                Json.parseToJsonElement(response).jsonObject
            } catch (e: SerializationException) {
                throw RoutingException("response is not JSON")
            } catch (e: IllegalArgumentException) {
                throw RoutingException("response is not a JSON object")
            }
            if ("error" in root) throw RoutingException(root["error"]?.jsonPrimitive?.contentOrNull ?: "routing error")
            val legs = (root["trip"]?.jsonObject?.get("legs") as? JsonArray)?.map { it.jsonObject }
                ?: throw RoutingException("response has no legs")
            val shape = mutableListOf<LatLon>()
            val maneuvers = mutableListOf<Maneuver>()
            for (leg in legs) {
                val legShape = leg["shape"]?.jsonPrimitive?.contentOrNull?.let {
                    try {
                        Polyline6.decode(it)
                    } catch (e: IllegalArgumentException) {
                        throw RoutingException("route shape is malformed")
                    }
                }.orEmpty()
                // Legs share their joining point; keep it once.
                val offset = if (shape.isNotEmpty() &&
                    legShape.firstOrNull() == shape.last()
                ) {
                    shape.size - 1
                } else {
                    shape.size
                }
                shape += if (offset == shape.size - 1) legShape.drop(1) else legShape
                (leg["maneuvers"] as? JsonArray).orEmpty().forEach { element ->
                    val m = element.jsonObject
                    maneuvers += Maneuver(
                        type = m.int("type"),
                        instruction = m.text("instruction").orEmpty(),
                        verbalPre = m.text("verbal_pre_transition_instruction"),
                        verbalAlert = m.text("verbal_transition_alert_instruction"),
                        streetNames = (m["street_names"] as? JsonArray)?.mapNotNull {
                            it.jsonPrimitive.contentOrNull
                        }.orEmpty(),
                        lengthKm = m["length"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        timeSeconds = m["time"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        beginIndex = offset + m.int("begin_shape_index")
                    )
                }
            }
            // A maneuver past the end of a decoded line would mean a malformed response.
            return try {
                NavRoute(shape, maneuvers)
            } catch (e: IllegalArgumentException) {
                throw RoutingException(e.message ?: "route is malformed")
            }
        }

        private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0

        private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
    }
}

/** Distance in metres. */
internal fun metres(a: LatLon, b: LatLon): Double = a.distanceKmTo(b) * 1000.0

/**
 * The point on segment [a]-[b] nearest to [p], as the fraction along the segment (0..1) and the
 * distance in metres. A local flat projection is exact enough for road segments.
 */
internal fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
    val metresPerDegLat = 111_320.0
    val metresPerDegLon = metresPerDegLat * cos(Math.toRadians(p.latitude))
    val ax = (a.longitude - p.longitude) * metresPerDegLon
    val ay = (a.latitude - p.latitude) * metresPerDegLat
    val bx = (b.longitude - p.longitude) * metresPerDegLon
    val by = (b.latitude - p.latitude) * metresPerDegLat
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    val x = ax + t * dx
    val y = ay + t * dy
    return t to kotlin.math.sqrt(x * x + y * y)
}
