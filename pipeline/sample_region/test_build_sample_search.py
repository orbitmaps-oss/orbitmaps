# SPDX-License-Identifier: GPL-3.0-or-later

import json
import sqlite3
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import build_sample_routing as bsr
import build_sample_search as bss
import fetch_sample_region as fsr


def feature(osm, geometry, **tags):
    return {"type": "Feature", "id": osm, "geometry": geometry, "properties": tags}


def point(lon, lat):
    return {"type": "Point", "coordinates": [lon, lat]}


class NamesAndCategoriesTest(unittest.TestCase):
    def test_names_include_every_language_and_alternative_without_duplicates(self):
        tags = {"name": "Panaji", "name:en": "Panaji", "name:hi": "पणजी", "alt_name": "Panjim;Pangim", "old_name": "panjim"}
        self.assertEqual(("Panaji", "Panjim", "Pangim", "पणजी"), bss.names_of(tags))

    def test_category_uses_the_first_meaningful_key(self):
        self.assertEqual("amenity=restaurant", bss.category_of({"amenity": "restaurant", "building": "yes"}))
        self.assertEqual("place=town", bss.category_of({"place": "town", "amenity": "townhall"}))
        self.assertEqual("highway=residential", bss.category_of({"highway": "residential"}))

    def test_minor_features_are_left_out(self):
        self.assertIsNone(bss.category_of({"railway": "rail"}))
        self.assertIsNone(bss.category_of({"natural": "tree"}))
        self.assertIsNone(bss.category_of({"highway": "bus_stop_typo"}))
        self.assertIsNone(bss.category_of({"building": "yes"}))
        self.assertEqual("railway=station", bss.category_of({"railway": "station"}))

    def test_towns_outrank_shops_and_shops_outrank_streets(self):
        town = bss.importance_of("place=town")
        shop = bss.importance_of("shop=bakery")
        street = bss.importance_of("highway=residential")
        self.assertGreater(town, shop)
        self.assertGreater(shop, street)
        self.assertGreater(bss.importance_of("highway=primary"), street)


class GeometryTest(unittest.TestCase):
    def test_point(self):
        self.assertEqual((15.4989, 73.8278), bss.representative_point(point(73.8278, 15.4989)))

    def test_line_uses_its_middle_vertex(self):
        line = {"type": "LineString", "coordinates": [[73.0, 15.0], [73.5, 15.5], [74.0, 16.0]]}
        self.assertEqual((15.5, 73.5), bss.representative_point(line))

    def test_polygon_uses_the_mean_of_its_outer_ring(self):
        square = {"type": "Polygon", "coordinates": [[[73.0, 15.0], [74.0, 15.0], [74.0, 16.0], [73.0, 16.0], [73.0, 15.0]]]}
        self.assertEqual((15.5, 73.5), bss.representative_point(square))

    def test_unknown_or_empty_geometry_is_skipped(self):
        self.assertIsNone(bss.representative_point({"type": "GeometryCollection", "coordinates": [1]}))
        self.assertIsNone(bss.representative_point({}))


class PlacesTest(unittest.TestCase):
    def test_feature_becomes_a_place(self):
        place = bss.place_from_feature(feature("n1", point(73.8, 15.5), name="Café Bhosle", **{"name:en": "Cafe Bhosle", "amenity": "cafe"}))
        self.assertEqual("n1", place.osm)
        self.assertEqual("Café Bhosle", place.name)
        self.assertEqual("Cafe Bhosle", place.name_en)
        self.assertEqual("amenity=cafe", place.category)
        self.assertEqual((15.5, 73.8), (place.lat, place.lon))

    def test_features_without_name_or_category_are_skipped(self):
        self.assertIsNone(bss.place_from_feature(feature("n1", point(73.8, 15.5), amenity="cafe")))
        self.assertIsNone(bss.place_from_feature(feature("n2", point(73.8, 15.5), name="House", building="yes")))

    def test_streets_merge_per_name_and_area_but_pois_never_merge(self):
        def street(osm, lat, lon, name="MG Road"):
            return bss.Place(osm, name, None, (name,), "highway=primary", lat, lon, 30)

        cafe = bss.Place("n9", "MG Road Cafe", None, ("MG Road Cafe",), "amenity=cafe", 15.5, 73.8, 30)
        merged = bss.merge_streets(
            [street("w1", 15.5001, 73.8001), street("w2", 15.5002, 73.8002), street("w3", 15.6, 73.9), street("w4", 15.5, 73.8, "Rua de Ourem"), cafe, cafe]
        )
        self.assertEqual(["w1", "w3", "w4", "n9", "n9"], [p.osm for p in merged])

    def test_reads_geojson_text_sequences_with_or_without_record_separators(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "x.geojsonseq"
            path.write_text("\x1e" + json.dumps({"id": "n1"}) + "\n\n" + json.dumps({"id": "n2"}) + "\n", encoding="utf-8")
            self.assertEqual(["n1", "n2"], [f["id"] for f in bss.read_features(path)])


class IndexTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.db_path = Path(self._tmp.name) / "search.sqlite"
        places = [
            bss.Place("n1", "Panaji", None, ("Panaji", "Panjim", "पणजी"), "place=city", 15.4989, 73.8278, 100),
            bss.Place("n2", "Café Bhosle", "Cafe Bhosle", ("Café Bhosle", "Cafe Bhosle"), "amenity=cafe", 15.49, 73.82, 30),
            bss.Place("w3", "Rua de Ourém", None, ("Rua de Ourém",), "highway=residential", 15.50, 73.83, 20),
        ]
        bss.build_index(places, self.db_path, bss.index_meta(3, "a" * 64, datetime(2026, 10, 9, tzinfo=timezone.utc)))
        self.db = sqlite3.connect(self.db_path)

    def tearDown(self):
        self.db.close()
        self._tmp.cleanup()

    def match(self, query):
        rows = self.db.execute(
            "SELECT p.osm FROM places_fts JOIN places p ON p.id = places_fts.rowid "
            "WHERE places_fts MATCH ? ORDER BY bm25(places_fts), p.id",
            (query,),
        )
        return [row[0] for row in rows]

    def test_prefix_search_works_as_the_user_types(self):
        self.assertEqual(["n1"], self.match('"pan"*'))
        self.assertEqual(["n1"], self.match('"panj"*'))

    def test_accents_are_ignored(self):
        self.assertEqual(["n2"], self.match('"cafe"*'))
        self.assertEqual(["w3"], self.match('"ourem"*'))

    def test_names_in_other_scripts_are_found(self):
        self.assertEqual(["n1"], self.match('"पणजी"*'))

    def test_every_word_must_match(self):
        self.assertEqual(["w3"], self.match('"rua"* "our"*'))
        self.assertEqual([], self.match('"rua"* "panaji"*'))

    def test_places_table_holds_what_the_app_shows(self):
        row = self.db.execute("SELECT name, name_en, category, lat, lon, importance FROM places WHERE osm = 'n2'").fetchone()
        self.assertEqual(("Café Bhosle", "Cafe Bhosle", "amenity=cafe", 15.49, 73.82, 30), row)

    def test_meta_records_schema_licence_and_attribution(self):
        meta = dict(self.db.execute("SELECT key, value FROM meta"))
        self.assertEqual(str(bss.SCHEMA_VERSION), meta["schema_version"])
        self.assertEqual("ODbL-1.0", meta["licence"])
        self.assertEqual("© OpenStreetMap contributors", meta["attribution"])
        self.assertEqual("3", meta["place_count"])
        self.assertEqual("2026-10-09T00:00:00+00:00", meta["built_at"])

    def test_no_partial_file_is_left_behind(self):
        self.assertEqual(["search.sqlite"], [p.name for p in self.db_path.parent.iterdir()])


class OutputTest(unittest.TestCase):
    def test_index_is_gitignored_bundled_next_to_the_other_region_files(self):
        self.assertEqual(fsr.REGION_FILE.parent, bss.INDEX.parent)
        self.assertEqual("out", bss.INDEX.relative_to(fsr.REPO_ROOT / "pipeline").parts[0])
        self.assertEqual(bsr.SAMPLE_DIR, bss.WORK_DIR.parent)


if __name__ == "__main__":
    unittest.main()
