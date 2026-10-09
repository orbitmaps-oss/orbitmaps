# SPDX-License-Identifier: GPL-3.0-or-later

import sqlite3
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import build_sample_search as bss
import build_world_places as bwp
import fetch_sample_region as fsr


def city(**props):
    return {"type": "Feature", "geometry": {"type": "Point", "coordinates": [72.8777, 19.076]}, "properties": props}


MUMBAI = city(
    NAME="Mumbai", NAMEASCII="Mumbai", NAMEALT="Bombay|Bambai", name_en="Mumbai", name_hi="मुंबई", name_mr="मुंबई",
    FEATURECLA="Admin-1 capital", SCALERANK=0, POP_MAX=18978000, ADM0NAME="India", ADM1NAME="Maharashtra", ne_id=1159150867,
)


class ParsingTest(unittest.TestCase):
    def test_git_blob_sha1_matches_git(self):
        # `git hash-object` of a file containing "hello\n".
        self.assertEqual("ce013625030ba8dba906f756967f9e9ca394464a", bwp.git_blob_sha1(b"hello\n"))

    def test_wrong_size_or_hash_is_rejected(self):
        with self.assertRaises(bwp.ChecksumError):
            bwp.verify_source(b"tampered")

    def test_source_is_pinned_to_a_release_with_a_hash(self):
        self.assertIn("/v5.1.2/", bwp.SOURCE_URL)
        self.assertRegex(bwp.SOURCE_GIT_SHA1, r"^[0-9a-f]{40}$")

    def test_names_include_languages_and_alternatives_once(self):
        props = bwp.lower_keys(MUMBAI["properties"])
        self.assertEqual(("Mumbai", "मुंबई", "Bombay", "Bambai"), bwp.names_of(props))

    def test_city_place_has_detail_category_and_high_importance(self):
        place = bwp.place_from_feature(MUMBAI, 0)
        self.assertEqual("Mumbai", place.name)
        self.assertIsNone(place.name_en)
        self.assertEqual("Maharashtra, India", place.detail)
        self.assertEqual("place=city", place.category)
        self.assertEqual(100, place.importance)
        self.assertEqual((19.076, 72.8777), (place.lat, place.lon))
        self.assertEqual("ne:1159150867", place.osm)

    def test_small_places_are_towns_with_lower_importance(self):
        place = bwp.place_from_feature(city(NAME="Mapusa", FEATURECLA="Populated place", SCALERANK=8, POP_MAX=40000, ADM0NAME="India"), 7)
        self.assertEqual("place=town", place.category)
        self.assertEqual(60, place.importance)
        self.assertEqual("India", place.detail)
        self.assertEqual("ne:7", place.osm)

    def test_features_without_name_or_location_are_skipped(self):
        self.assertIsNone(bwp.place_from_feature(city(SCALERANK=1), 0))
        self.assertIsNone(bwp.place_from_feature({"geometry": None, "properties": {"NAME": "Nowhere"}}, 0))

    def test_places_are_sorted_most_important_first(self):
        small = city(NAME="Mapusa", SCALERANK=8)
        places = bwp.places_from_geojson({"features": [small, MUMBAI]})
        self.assertEqual(["Mumbai", "Mapusa"], [p.name for p in places])


class IndexTest(unittest.TestCase):
    def test_world_index_is_searchable_with_the_region_schema(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "world.sqlite"
            places = bwp.places_from_geojson({"features": [MUMBAI]})
            meta = bss.index_meta(1, "a" * 64, datetime(2026, 10, 9, tzinfo=timezone.utc), region="world")
            bss.build_index(places, dest, meta)
            db = sqlite3.connect(dest)
            try:
                hit = db.execute(
                    "SELECT p.name, p.detail FROM places_fts JOIN places p ON p.id = places_fts.rowid WHERE places_fts MATCH ?",
                    ('"bomb"*',),
                ).fetchall()
                self.assertEqual([("Mumbai", "Maharashtra, India")], hit)
                self.assertEqual("world", dict(db.execute("SELECT key, value FROM meta"))["region"])
            finally:
                db.close()

    def test_output_is_gitignored(self):
        self.assertEqual("out", bwp.INDEX.relative_to(fsr.REPO_ROOT / "pipeline").parts[0])


if __name__ == "__main__":
    unittest.main()
