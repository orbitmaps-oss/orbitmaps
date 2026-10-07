// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Turns the bundled style template (styles/bundled/map/style-light.json) into a fully offline style. */
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

        val style = Json.parseToJsonElement(template).jsonObject
        require(style["version"]?.jsonPrimitive?.content == "8") { "style must be version 8" }
        for (key in listOf("glyphs", "sprite")) {
            val value = (style[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(value != null && value.startsWith(ASSET_SCHEME)) { "$key must be an $ASSET_SCHEME URL" }
        }
        val sources = style["sources"] as? JsonObject
        val source = sources?.get(SOURCE_ID) as? JsonObject
        requireNotNull(source) { "style has no '$SOURCE_ID' source" }

        val newSource = JsonObject(source + ("url" to JsonPrimitive("pmtiles://file://$path")))
        val result = JsonObject(style + ("sources" to JsonObject(sources + (SOURCE_ID to newSource))))
        requireNoRemoteUrl(result)
        return result.toString()
    }

    private fun requireNoRemoteUrl(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                require(!REMOTE_URL.containsMatchIn(key)) { "style contains an http(s) URL" }
                requireNoRemoteUrl(value)
            }
            is JsonArray -> element.forEach(::requireNoRemoteUrl)
            is JsonPrimitive -> require(!(element.isString && REMOTE_URL.containsMatchIn(element.content))) {
                "style contains an http(s) URL"
            }
        }
    }
}
