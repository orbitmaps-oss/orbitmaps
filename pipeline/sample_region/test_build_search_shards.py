# SPDX-License-Identifier: GPL-3.0-or-later

import sqlite3
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import build_sample_search as bss
import build_search_shards as shards


def place(osm, name, lat, lon, category="amenity=cafe"):
    return bss.Place(osm, name, None, (name,), category, lat, lon, bss.importance_of(category))


class ShardTest(unittest.TestCase):
    def test_cells_use_valhallas_level_2_grid(self):
        # Same numbers as ValhallaTilesTest: 52.1N 5.1E is row 568, column 740.
        self.assertEqual(568 * 1440 + 740, shards.cell_id(52.1, 5.1))
        self.assertEqual(0, shards.cell_id(-90.0, -180.0))
        self.assertEqual(1440 * 720 - 1, shards.cell_id(90.0, 180.0))

    def test_paths_match_valhalla_tile_paths(self):
        self.assertEqual("2/000/818/660.sqlite", shards.cell_path(818660))
        self.assertEqual("2/001/036/799.sqlite", shards.cell_path(1440 * 720 - 1))

    def test_places_are_bucketed_by_cell(self):
        cells = shards.bucket([
            place("n1", "Cafe A", 15.49, 73.82),
            place("n2", "Cafe B", 15.40, 73.80),
            place("n3", "Cafe C", 19.07, 72.88),
        ])
        self.assertEqual(2, len(cells))
        self.assertEqual(["n1", "n2"], [p.osm for p in cells[shards.cell_id(15.49, 73.82)]])

    def test_each_shard_is_a_searchable_index_with_merged_streets(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            cells = shards.bucket([
                place("n1", "Café Bhosle", 15.49, 73.82),
                place("w2", "MG Road", 15.4901, 73.8201, "highway=primary"),
                place("w3", "MG Road", 15.4902, 73.8202, "highway=primary"),
                place("n4", "Gateway Café", 18.92, 72.83),
            ])
            count = shards.write_shards(cells, out, "a" * 64, datetime(2026, 10, 9, tzinfo=timezone.utc))
            self.assertEqual(2, count)
            goa = out / shards.cell_path(shards.cell_id(15.49, 73.82))
            db = sqlite3.connect(goa)
            try:
                names = [r[0] for r in db.execute(
                    "SELECT p.name FROM places_fts JOIN places p ON p.id = places_fts.rowid WHERE places_fts MATCH ?",
                    ('"cafe"*',),
                )]
                self.assertEqual(["Café Bhosle"], names)
                self.assertEqual(2, db.execute("SELECT count(*) FROM places").fetchone()[0])
                meta = dict(db.execute("SELECT key, value FROM meta"))
                self.assertEqual(str(bss.SCHEMA_VERSION), meta["schema_version"])
                self.assertEqual("ODbL-1.0", meta["licence"])
            finally:
                db.close()

    def test_usage_without_input(self):
        self.assertEqual(2, shards.main(["build_search_shards.py"]))


if __name__ == "__main__":
    unittest.main()
