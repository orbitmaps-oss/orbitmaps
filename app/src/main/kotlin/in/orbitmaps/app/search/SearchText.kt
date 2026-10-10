// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import androidx.annotation.StringRes
import `in`.orbitmaps.app.R
import java.util.Locale
import kotlin.math.ln

/** The search chips (window 1 and 4), as sets of OSM categories ("key=value") from the index. */
enum class CategoryGroup(@StringRes val label: Int, val exact: Set<String>, val keyPrefixes: Set<String> = emptySet()) {
    Food(
        R.string.category_food,
        setOf(
            "amenity=restaurant", "amenity=cafe", "amenity=fast_food", "amenity=food_court", "amenity=bar",
            "amenity=pub", "amenity=ice_cream", "shop=bakery", "shop=confectionery"
        )
    ),
    Fuel(R.string.category_fuel, setOf("amenity=fuel")),
    Ev(R.string.category_ev, setOf("amenity=charging_station")),
    Trails(
        R.string.category_trails,
        setOf(
            "highway=path",
            "highway=footway",
            "highway=track",
            "highway=bridleway",
            "leisure=nature_reserve",
            "natural=peak",
            "tourism=viewpoint"
        )
    ),
    Hospital(
        R.string.category_hospital,
        setOf("amenity=hospital", "amenity=clinic", "amenity=doctors", "amenity=pharmacy"),
        setOf("healthcare")
    ),
    Shop(R.string.category_shop, emptySet(), setOf("shop"));

    fun matches(category: String): Boolean = category in exact || category.substringBefore('=') in keyPrefixes

    /** A SQL condition on the `category` column and its arguments, e.g. `(category IN (?, ?))`. */
    fun sqlCondition(): Pair<String, List<String>> {
        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (exact.isNotEmpty()) {
            parts += "category IN (${exact.joinToString(", ") { "?" }})"
            args += exact.sorted()
        }
        keyPrefixes.sorted().forEach { key ->
            parts += "category LIKE ?"
            args += "$key=%"
        }
        return "(${parts.joinToString(" OR ")})" to args
    }
}

/** Turns what the user typed into an FTS5 query, and ranks what the index returns. */
object SearchText {
    private const val MAX_TOKENS = 6

    /**
     * Every word must match, each as a prefix (so results appear while typing). Words are quoted, so
     * FTS5 syntax in the input ("OR", "-", "*", quotes) is treated as text. Returns null for no words.
     */
    fun ftsQuery(input: String): String? {
        val tokens = foldSpelling(input)
            .split(Regex("[^\\p{L}\\p{M}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .map { it.lowercase(Locale.ROOT) }
            .take(MAX_TOKENS)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"${it.replace("\"", "\"\"")}\"*" }
    }

    /**
     * Devanagari: writes a nasal consonant + virama before another consonant (म्ब) as the anusvara
     * (ंब), so "मुम्बई" and "मुंबई" search the same. The index stores names folded the same way
     * (fold_spelling in pipeline/sample_region/build_sample_search.py).
     */
    fun foldSpelling(text: String): String = DEVANAGARI_NASAL_CLUSTER.replace(text, "\u0902")

    private val DEVANAGARI_NASAL_CLUSTER = Regex("[\u0919\u091E\u0923\u0928\u092E]\u094D(?=[\u0915-\u0939])")

    /**
     * Higher is better. Combines FTS5's bm25 (negative; more negative is a better text match), how
     * important the place is (0 to 100, from the index), how far it is, and whether the name starts
     * with or equals the query.
     */
    fun score(name: String, query: String, bm25: Double, importance: Int, distanceKm: Double): Double {
        val normalisedName = name.trim().lowercase()
        val normalisedQuery = query.trim().lowercase()
        val nameBonus = when {
            normalisedQuery.isEmpty() -> 0.0
            normalisedName == normalisedQuery -> EXACT_BONUS
            normalisedName.startsWith(normalisedQuery) -> PREFIX_BONUS
            else -> 0.0
        }
        return -bm25 + importance / IMPORTANCE_DIVISOR + nameBonus - ln(1 + distanceKm) * DISTANCE_WEIGHT
    }

    /** The label shown under a result, from its OSM category. */
    @StringRes
    fun categoryLabel(category: String): Int {
        CategoryGroup.entries.firstOrNull { it.matches(category) }?.let { return it.label }
        return when (category.substringBefore('=')) {
            "place" -> R.string.category_place
            "highway" -> R.string.category_street
            "railway", "aeroway", "public_transport" -> R.string.category_transport
            "natural", "leisure" -> R.string.category_nature
            else -> R.string.category_poi
        }
    }

    private const val EXACT_BONUS = 4.0
    private const val PREFIX_BONUS = 2.0
    private const val IMPORTANCE_DIVISOR = 20.0
    private const val DISTANCE_WEIGHT = 1.5
}
