// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStyleTest {
    private val stylesDir = File(requireNotNull(System.getProperty("orbitmaps.stylesDir")) { "run through Gradle" })
    private val template = File(stylesDir, OfflineStyle.ASSET_PATH).readText()
    private val region = File("/data/user/0/in.orbitmaps.app/files/regions/panaji.pmtiles")

    private fun styled(json: String = template): JsonObject {
        val output = OfflineStyle.offlineStyleJson(json, region)
        return Json.parseToJsonElement(output).jsonObject
    }

    private fun layers(style: JsonObject) = style.getValue("layers").jsonArray.map { it.jsonObject }

    @Test
    fun bundledTemplateIsAStyleV8() {
        val style = Json.parseToJsonElement(template).jsonObject
        assertEquals("8", style.getValue("version").jsonPrimitive.content)
        assertTrue(layers(style).isNotEmpty())
    }

    @Test
    fun sourceUrlPointsAtTheInstalledRegion() {
        val source = styled().getValue("sources").jsonObject.getValue(OfflineStyle.SOURCE_ID).jsonObject
        assertEquals(
            "pmtiles://file:///data/user/0/in.orbitmaps.app/files/regions/panaji.pmtiles",
            source.getValue("url").jsonPrimitive.content
        )
        assertEquals("vector", source.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun glyphsAndSpriteAreBundledAssets() {
        val style = styled()
        assertEquals("asset://map/glyphs/{fontstack}/{range}.pbf", style.getValue("glyphs").jsonPrimitive.content)
        assertEquals("asset://map/sprites/light", style.getValue("sprite").jsonPrimitive.content)
    }

    @Test
    fun everyLayerUsesTheRegionSource() {
        val style = styled()
        assertEquals(setOf(OfflineStyle.SOURCE_ID), style.getValue("sources").jsonObject.keys)
        layers(style).filter { it.getValue("type").jsonPrimitive.content != "background" }.forEach { layer ->
            assertEquals(layer["id"].toString(), OfflineStyle.SOURCE_ID, layer.getValue("source").jsonPrimitive.content)
        }
    }

    @Test
    fun outputHasNoRemoteUrl() {
        val output = OfflineStyle.offlineStyleJson(template, region)
        assertFalse(Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(output))
    }

    @Test
    fun remoteUrlInALayerIsRejected() {
        val source = "\"source\": \"protomaps\""
        val bad = template.replaceFirst(source, "$source, \"metadata\": \"https://example.org\"")
        assertTrue(bad != template)
        assertThrows(IllegalArgumentException::class.java) { styled(bad) }
    }

    @Test
    fun remoteGlyphsAreRejected() {
        val bad = template.replace("asset://map/glyphs/", "https://example.org/glyphs/")
        assertThrows(IllegalArgumentException::class.java) { styled(bad) }
    }

    @Test
    fun remoteSpriteIsRejected() {
        val bad = template.replace("asset://map/sprites/light", "http://example.org/sprites/light")
        assertThrows(IllegalArgumentException::class.java) { styled(bad) }
    }

    @Test
    fun relativeRegionPathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OfflineStyle.offlineStyleJson(template, File("regions/panaji.pmtiles"))
        }
    }

    @Test
    fun everyFontstackHasBundledGlyphs() {
        val fontstacks = layers(styled())
            .mapNotNull { it["layout"]?.jsonObject?.get("text-font") }
            .flatMap(::fontstacks)
            .toSet()
        assertEquals(setOf("Noto Sans Regular", "Noto Sans Medium", "Noto Sans Italic"), fontstacks)
        for (fontstack in fontstacks) {
            for (range in listOf("0-255", "256-511", "2304-2559", "8192-8447")) {
                assertTrue("$fontstack/$range", File(stylesDir, "map/glyphs/$fontstack/$range.pbf").isFile)
            }
        }
    }

    @Test
    fun spritesExistAtOneAndTwoTimes() {
        for (name in listOf("light.json", "light.png", "light@2x.json", "light@2x.png")) {
            assertTrue(name, File(stylesDir, "map/sprites/$name").isFile)
        }
    }

    /** Fontstacks in a text-font value: a plain array of names, or ["literal", [names]] inside an expression. */
    private fun fontstacks(value: JsonElement): List<String> {
        if (value !is JsonArray) return emptyList()
        val first = (value.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when {
            first == "literal" -> listOf(value[1].jsonArray.joinToString(",") { it.jsonPrimitive.content })
            first != null && first !in EXPRESSION_OPERATORS && value.all { it is JsonPrimitive && it.isString } ->
                listOf(value.joinToString(",") { it.jsonPrimitive.content })
            else -> value.flatMap(::fontstacks)
        }
    }

    private companion object {
        val EXPRESSION_OPERATORS = setOf("case", "step", "match", "coalesce", "get", "<=", "<", ">=", ">", "==", "!=")
    }
}
