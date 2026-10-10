// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.routing

import `in`.orbitmaps.core.model.LatLon
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaJsonTest {
    /** Shaped like valhalla_build_config output: build paths under /data/, other values untouched. */
    private val template = """
        {
          "mjolnir": {
            "tile_dir": "/data/tiles",
            "tile_extract": "/data/tiles.tar",
            "admin": "/data/admins.sqlite",
            "timezone": "/data/timezones.sqlite",
            "traffic_extract": "/data/traffic.tar",
            "concurrency": 4,
            "hierarchy": true
          },
          "loki": { "actions": ["route", "locate"], "use_connectivity": true },
          "service_limits": { "auto": { "max_distance": 5000000.0 } },
          "additional_data": { "elevation": "/data/elevation/" },
          "odin": { "markup_formatter": { "markup_enabled": false } }
        }
    """.trimIndent()

    private val tar = File("/phone/files/regions/panaji-routing.tar")
    private val dataDir = File("/phone/files/regions/panaji-routing")

    private fun config(): JsonObject =
        Json.parseToJsonElement(ValhallaJson.deviceConfig(template, tar, dataDir)).jsonObject

    @Test
    fun tileExtractPointsAtTheInstalledTar() {
        val mjolnir = config().getValue("mjolnir").jsonObject
        assertEquals(tar.absolutePath, mjolnir.getValue("tile_extract").jsonPrimitive.content)
    }

    @Test
    fun otherBuildPathsMoveUnderTheDeviceDataDir() {
        val config = config()
        val mjolnir = config.getValue("mjolnir").jsonObject
        val prefix = dataDir.absolutePath + "/"
        assertEquals(prefix + "tiles", mjolnir.getValue("tile_dir").jsonPrimitive.content)
        assertEquals(prefix + "admins.sqlite", mjolnir.getValue("admin").jsonPrimitive.content)
        assertEquals(prefix + "traffic.tar", mjolnir.getValue("traffic_extract").jsonPrimitive.content)
        val elevation = config.getValue("additional_data").jsonObject.getValue("elevation").jsonPrimitive.content
        assertEquals(prefix + "elevation/", elevation)
        assertFalse(config.toString().contains("\"/data/"))
    }

    @Test
    fun everythingElseIsKeptAsIs() {
        val config = config()
        assertEquals(4, config.getValue("mjolnir").jsonObject.getValue("concurrency").jsonPrimitive.content.toInt())
        assertEquals(
            listOf("route", "locate"),
            config.getValue("loki").jsonObject.getValue("actions").jsonArray.map {
                it.jsonPrimitive.content
            }
        )
        assertEquals(
            5_000_000.0,
            config.getValue(
                "service_limits"
            ).jsonObject.getValue("auto").jsonObject.getValue("max_distance").jsonPrimitive.double,
            0.0
        )
        assertEquals(setOf("mjolnir", "loki", "service_limits", "additional_data", "odin"), config.keys)
    }

    @Test
    fun configWithoutMjolnirIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ValhallaJson.deviceConfig("{}", tar, dataDir) }
    }

    @Test
    fun routeRequestHasBothPointsCostingAndKilometres() {
        val request = Json.parseToJsonElement(
            ValhallaJson.routeRequest(LatLon(15.4989, 73.8278), LatLon(15.5395, 73.8135), Costing.Bike)
        ).jsonObject
        val locations = request.getValue("locations").jsonArray.map { it.jsonObject }
        assertEquals(15.4989, locations[0].getValue("lat").jsonPrimitive.double, 0.0)
        assertEquals(73.8278, locations[0].getValue("lon").jsonPrimitive.double, 0.0)
        assertEquals(15.5395, locations[1].getValue("lat").jsonPrimitive.double, 0.0)
        assertEquals("bicycle", request.getValue("costing").jsonPrimitive.content)
        assertEquals(
            "kilometers",
            request.getValue("directions_options").jsonObject.getValue("units").jsonPrimitive.content
        )
    }

    @Test
    fun theLanguageIsRequestedWhenGivenAndOtherwiseLeftOut() {
        val withLanguage = Json.parseToJsonElement(
            ValhallaJson.routeRequest(LatLon(0.0, 0.0), LatLon(1.0, 1.0), language = "hi-IN")
        ).jsonObject.getValue("directions_options").jsonObject
        assertEquals("hi-IN", withLanguage.getValue("language").jsonPrimitive.content)
        val without = Json.parseToJsonElement(ValhallaJson.routeRequest(LatLon(0.0, 0.0), LatLon(1.0, 1.0)))
            .jsonObject.getValue("directions_options").jsonObject
        assertFalse("language" in without)
    }

    @Test
    fun theFullReplyIsKeptForNavigation() {
        val response = """{"trip":{"legs":[],"summary":{"length":1.0,"time":60.0}}}"""
        assertEquals(response, ValhallaJson.parseRoute(response).json)
    }

    @Test
    fun defaultCostingIsCar() {
        assertTrue(ValhallaJson.routeRequest(LatLon(0.0, 0.0), LatLon(1.0, 1.0)).contains("\"costing\":\"auto\""))
    }

    @Test
    fun parsesTheTripSummaryAndCountsManeuvers() {
        val response = """
            {"trip":{"status":0,"units":"kilometers",
              "legs":[{"maneuvers":[{"type":1},{"type":10},{"type":4}],"summary":{"length":5.1,"time":600.5}}],
              "summary":{"length":5.123,"time":612.4}}}
        """.trimIndent()
        assertEquals(
            RouteSummary(lengthKm = 5.123, timeSeconds = 612.4, maneuvers = 3),
            ValhallaJson.parseRoute(response).copy(json = "")
        )
    }

    @Test
    fun errorRepliesBecomeRoutingExceptions() {
        val error = """
            {"error_code":171,"error":"No suitable edges near location","status_code":400,"status":"Bad Request"}
        """.trimIndent()
        val thrown = assertThrows(RoutingException::class.java) { ValhallaJson.parseRoute(error) }
        assertEquals("171: No suitable edges near location", thrown.message)
    }

    @Test
    fun replyWithoutTripOrSummaryIsAnError() {
        assertThrows(RoutingException::class.java) { ValhallaJson.parseRoute("{}") }
        assertThrows(RoutingException::class.java) { ValhallaJson.parseRoute("""{"trip":{"legs":[]}}""") }
    }

    @Test
    fun tileDirConfigReadsAFolderInsteadOfATar() {
        val tileDir = File("/phone/files/routing/valhalla-3.6.3/tiles")
        val config = Json.parseToJsonElement(ValhallaJson.tileDirConfig(template, tileDir, dataDir)).jsonObject
        val mjolnir = config.getValue("mjolnir").jsonObject
        assertEquals(tileDir.absolutePath, mjolnir.getValue("tile_dir").jsonPrimitive.content)
        assertFalse("tile_extract" in mjolnir)
        assertFalse("traffic_extract" in mjolnir)
        assertEquals(dataDir.absolutePath + "/admins.sqlite", mjolnir.getValue("admin").jsonPrimitive.content)
        assertFalse(config.toString().contains("\"/data/"))
    }

    @Test
    fun routeShapeIsDecodedAcrossLegs() {
        val response = """
            {"trip":{"legs":[{"maneuvers":[{"type":1}],"shape":"_p~iF~ps|U"},{"maneuvers":[],"shape":"_ulLnnqC"}],
              "summary":{"length":1.0,"time":60.0}}}
        """.trimIndent()
        val shape = ValhallaJson.parseRoute(response).shape
        assertEquals(2, shape.size)
        assertEquals(3.85, shape[0].latitude, 1e-9)
    }

    @Test
    fun malformedShapeIsARoutingError() {
        val response = """{"trip":{"legs":[{"shape":"_p~iF~ps|U_"}],"summary":{"length":1.0,"time":60.0}}}"""
        assertThrows(RoutingException::class.java) { ValhallaJson.parseRoute(response) }
    }
}
