// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.search

import `in`.orbitmaps.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test
    fun everyWordBecomesAQuotedPrefix() {
        assertEquals("\"rua\"* \"de\"* \"our\"*", SearchText.ftsQuery("  Rua de our"))
    }

    @Test
    fun punctuationSplitsWordsAndFts5SyntaxIsTreatedAsText() {
        assertEquals("\"st\"* \"inez\"*", SearchText.ftsQuery("St. Inez"))
        assertEquals("\"cafe\"* \"or\"* \"bar\"*", SearchText.ftsQuery("cafe OR bar"))
        assertEquals("\"a\"* \"b\"*", SearchText.ftsQuery("a\" -b*"))
        assertEquals("\"near\"*", SearchText.ftsQuery("NEAR("))
    }

    @Test
    fun accentsAndOtherScriptsAreKept() {
        assertEquals("\"ourém\"*", SearchText.ftsQuery("Ourém"))
        assertEquals("\"पणजी\"*", SearchText.ftsQuery("पणजी"))
        assertEquals("\"12\"* \"b\"*", SearchText.ftsQuery("12-B"))
    }

    @Test
    fun emptyInputGivesNoQuery() {
        assertNull(SearchText.ftsQuery(""))
        assertNull(SearchText.ftsQuery("  ,.;  "))
    }

    @Test
    fun longInputIsCappedAtSixWords() {
        assertEquals(6, SearchText.ftsQuery("a b c d e f g h")!!.split(" ").size)
    }

    @Test
    fun betterTextMatchScoresHigher() {
        assertTrue(score(bm25 = -8.0) > score(bm25 = -2.0))
    }

    @Test
    fun closerScoresHigher() {
        assertTrue(score(distanceKm = 0.5) > score(distanceKm = 20.0))
    }

    @Test
    fun moreImportantScoresHigher() {
        assertTrue(score(importance = 100) > score(importance = 20))
    }

    @Test
    fun exactAndPrefixNamesScoreHigher() {
        val exact = SearchText.score("Panaji", "panaji", -2.0, 30, 1.0)
        val prefix = SearchText.score("Panaji Market", "panaji", -2.0, 30, 1.0)
        val inside = SearchText.score("Old Panaji Road", "panaji", -2.0, 30, 1.0)
        assertTrue(exact > prefix)
        assertTrue(prefix > inside)
    }

    @Test
    fun aTownBeatsAFarStreetWithTheSameName() {
        val town = SearchText.score("Porvorim", "porvorim", -3.0, importance = 90, distanceKm = 6.0)
        val street = SearchText.score("Porvorim Road", "porvorim", -3.0, importance = 20, distanceKm = 1.0)
        assertTrue(town > street)
    }

    private fun score(bm25: Double = -3.0, importance: Int = 30, distanceKm: Double = 1.0) =
        SearchText.score("Some place", "some", bm25, importance, distanceKm)

    @Test
    fun categoryGroupsMatchTheirOsmCategories() {
        assertTrue(CategoryGroup.Food.matches("amenity=cafe"))
        assertTrue(CategoryGroup.Fuel.matches("amenity=fuel"))
        assertTrue(CategoryGroup.Ev.matches("amenity=charging_station"))
        assertTrue(CategoryGroup.Trails.matches("highway=path"))
        assertTrue(CategoryGroup.Hospital.matches("healthcare=dentist"))
        assertTrue(CategoryGroup.Shop.matches("shop=books"))
        assertFalse(CategoryGroup.Food.matches("amenity=fuel"))
        assertFalse(CategoryGroup.Shop.matches("amenity=marketplace"))
    }

    @Test
    fun groupSqlHasOnePlaceholderPerArgument() {
        CategoryGroup.entries.forEach { group ->
            val (sql, args) = group.sqlCondition()
            assertEquals(group.name, sql.count { it == '?' }, args.size)
            assertTrue(group.name, sql.startsWith("(") && sql.endsWith(")"))
        }
        val (sql, args) = CategoryGroup.Hospital.sqlCondition()
        assertEquals("(category IN (?, ?, ?, ?) OR category LIKE ?)", sql)
        assertEquals("healthcare=%", args.last())
    }

    @Test
    fun categoryLabelsUseTheGroupOrAGeneralKind() {
        assertEquals(R.string.category_food, SearchText.categoryLabel("amenity=restaurant"))
        assertEquals(R.string.category_shop, SearchText.categoryLabel("shop=books"))
        assertEquals(R.string.category_place, SearchText.categoryLabel("place=village"))
        assertEquals(R.string.category_street, SearchText.categoryLabel("highway=residential"))
        assertEquals(R.string.category_transport, SearchText.categoryLabel("railway=station"))
        assertEquals(R.string.category_nature, SearchText.categoryLabel("natural=beach"))
        assertEquals(R.string.category_poi, SearchText.categoryLabel("tourism=museum"))
    }
}
