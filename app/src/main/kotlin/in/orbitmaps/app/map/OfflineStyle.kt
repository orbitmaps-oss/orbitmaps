// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import `in`.orbitmaps.app.net.OnlineData
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the bundled style template (styles/bundled/map/style-light.json) into a style for the installed
 * region (fully offline) or for our streamed world tiles. Glyphs and sprites are always bundled.
 */
object OfflineStyle {
    const val ASSET_PATH = "map/style-light.json"
    const val SOURCE_ID = "protomaps"

    private const val ASSET_SCHEME = "asset://"
    private val REMOTE_URL = Regex("https?://", RegexOption.IGNORE_CASE)

    /**
     * Points the vector source at [regionFile] and checks that nothing in the style can reach the network.
     *
     * @throws IllegalArgumentException if the template isn't a v8 style with an asset:// glyphs and sprite
     *   URL and a [SOURCE_ID] source, or if any string in it contains an http(s) URL.
     */
    fun offlineStyleJson(template: String, regionFile: File): String {
        val path = regionFile.invariantSeparatorsPath
        require(path.startsWith("/")) { "region file path must be absolute" }

        val style = validatedTemplate(template)
        val sources = style.getValue("sources").jsonObject
        val source = sources.getValue(SOURCE_ID).jsonObject

        val newSource = JsonObject(source + ("url" to JsonPrimitive("pmtiles://file://$path")))
        val result = JsonObject(style + ("sources" to JsonObject(sources + (SOURCE_ID to newSource))))
        requireNoRemoteUrl(result)
        return result.toString()
    }

    /**
     * Points the vector source at our streamed world tiles ([tilesUrl], which must be one of our own
     * files, see [OnlineData]). Glyphs and sprites stay bundled; [tilesUrl] is the only remote URL.
     *
     * @throws IllegalArgumentException as [offlineStyleJson], or if [tilesUrl] isn't ours.
     */
    fun streamingStyleJson(template: String, tilesUrl: String): String {
        require(OnlineData.isOurs(tilesUrl) && tilesUrl.endsWith(".pmtiles")) { "tiles URL must be our PMTiles file" }
        val style = validatedTemplate(template)
        val sources = style.getValue("sources").jsonObject
        val source = sources.getValue(SOURCE_ID).jsonObject
        val newSource = JsonObject(source + ("url" to JsonPrimitive("pmtiles://$tilesUrl")))
        val result = JsonObject(style + ("sources" to JsonObject(sources + (SOURCE_ID to newSource))))
        requireNoRemoteUrl(result, allowed = "pmtiles://$tilesUrl")
        return result.toString()
    }

    private fun validatedTemplate(template: String): JsonObject {
        val style = Json.parseToJsonElement(template).jsonObject
        require(style["version"]?.jsonPrimitive?.content == "8") { "style must be version 8" }
        for (key in listOf("glyphs", "sprite")) {
            val value = (style[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(value != null && value.startsWith(ASSET_SCHEME)) { "$key must be an $ASSET_SCHEME URL" }
        }
        requireNotNull((style["sources"] as? JsonObject)?.get(SOURCE_ID) as? JsonObject) {
            "style has no '$SOURCE_ID' source"
        }
        return style
    }

    private fun requireNoRemoteUrl(element: JsonElement, allowed: String? = null) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                require(!REMOTE_URL.containsMatchIn(key)) { "style contains an http(s) URL" }
                requireNoRemoteUrl(value, allowed)
            }
            is JsonArray -> element.forEach { requireNoRemoteUrl(it, allowed) }
            is JsonPrimitive -> require(
                !(element.isString && element.content != allowed && REMOTE_URL.containsMatchIn(element.content))
            ) {
                "style contains an http(s) URL"
            }
        }
    }
}
