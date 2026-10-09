# Region-pack pipeline

> Early stage. Only the development sample region and the bundled style exist so far.

This directory will hold the tooling that turns OpenStreetMap extracts into Orbit Maps
**region packs** (see [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)):

```
OSM extract (.osm.pbf)
  ├─► vector tiles  ──► PMTiles archive        (map display, MapLibre Native)
  ├─► search index  ──► SQLite + FTS5 database (offline search)
  └─► routing graph ──► Valhalla tile archive  (offline routing, valhalla-mobile)
      + manifest.json (version, bounds, SHA-256 checksums, licences)
```

## Sample region (`sample_region/`)

A roughly 20 × 20 km PMTiles extract around Panaji, Goa, cut from a pinned Protomaps Basemap
build. Debug builds bundle it, and the app copies it into app storage on first launch.

```sh
python -I pipeline/sample_region/fetch_sample_region.py
```

- Downloads the **pmtiles CLI 1.31.2** for your OS (Windows, Linux or macOS, x86_64 or arm64) into
  `pipeline/out/tools/` and checks the archive's **SHA-256 before unpacking it**. It doesn't
  install anything or change `PATH`. On Windows, a normal `python` from python.org or the
  Microsoft Store is enough.
- Runs `pmtiles extract` against the pinned build **`20260811`** (tileset 4.15.1) for bbox
  `73.734,15.401,73.921,15.581`, zoom 0–15. Then runs `pmtiles verify` and checks the result
  against the pinned SHA-256. The output is about 4.6 MB, written to
  `pipeline/out/sample-region/assets/regions/panaji.pmtiles` (gitignored).
- **Why that build:** Protomaps keeps the builds from the last week plus the newest build of
  each patch version. 20260811 is the only 4.15.1 build, so it stays available. If it is ever
  removed, pin the newest build of another patch version from
  <https://build-metadata.protomaps.dev/builds.json>, run with `--update-expected` and commit
  the new hash.
- Tests: `python -m unittest discover -s pipeline/sample_region -p "test_*.py"`.

### Sample routing tiles and search index

Valhalla routing tiles and an SQLite FTS5 search index for the same box. The tile builder needs
Linux, so both run in GitHub Actions rather than on developer machines:

1. On GitHub: **Actions → Sample region data → Run workflow**.
2. Download the `panaji-region-data` artifact from the run and unzip it into
   `pipeline/out/sample-region/`. That gives `assets/regions/panaji-routing.tar` and
   `assets/regions/panaji-routing.json` (the Valhalla config), `assets/regions/panaji-search.sqlite`
   (the search index; all three bundled in debug builds) and `routing-manifest.json`.

`sample_region/build_sample_routing.py` downloads the dated extract
`western-zone-260101.osm.pbf` from Geofabrik and checks Geofabrik's MD5, cuts the box with
**osmium**, and builds and packs the tiles with **Valhalla 3.6.3** (Docker image pinned by
digest, run with no network). That must be the same Valhalla version as the one inside
valhalla-mobile, or the app can't read the tiles. The manifest records the versions, the source
and tile SHA-256 and the ODbL licence. It also runs on Linux or WSL with Docker and
`osmium-tool` installed.

`sample_region/build_sample_search.py` then keeps every named object in the same extract, gives
each one all its OSM names (every language, alternative and old names), a category, a point and an
importance rank, merges street segments per name and area, and writes `places` plus a contentless
FTS5 table (see "Search index format" below) with licence and attribution in `meta`.

### World places index

Cities and towns everywhere, so search finds "Mumbai" or "Paris" before any region is downloaded.
Needs only Python, so it runs on any machine:

```sh
python pipeline/sample_region/build_world_places.py
```

It downloads Natural Earth's `ne_10m_populated_places.geojson` (public domain, release v5.1.2,
about 19 MB), checks its **git blob SHA-1 and size before parsing**, and writes
`pipeline/out/world/assets/places/world-places.sqlite` (about 3.3 MB, 2 MB in the APK): every
place with all its names (26 languages plus alternatives such as "Bombay"), "region, country"
as detail, and an importance from Natural Earth's scale rank and capital status. Every build
bundles it when present.

### Search index format (shared)

Both indexes use the schema in `build_sample_search.py` (version 2): `places` (name, English name,
OSM-style category, point, importance, detail), a contentless FTS5 table `places_fts` and `meta`.
The FTS5 tokenizer is `unicode61 remove_diacritics 2 categories 'L* N* Co M*'`: accents are
folded ("ourem" finds "Ourém"), and combining marks stay inside words, which Indic scripts need
(with the default tokenizer "मुंब" matched any name containing म and ब). Devanagari names are
also indexed with nasal clusters written as anusvara ("मुम्बई" also as "मुंबई"); the app folds
queries the same way, so both common spellings work.

### Area search shards

`sample_region/build_search_shards.py INPUT.osm.pbf [OUT]` splits any extract (up to the planet)
into one search index per 0.25° cell on Valhalla's level-2 grid, for search anywhere. Building and
uploading the world data (map, routing tiles, shards) is described in
[docs/HOSTING.md](../docs/HOSTING.md).

## Bundled style (`style/`)

Generates `styles/bundled/map/style-light.json` from the pinned `@protomaps/basemaps` package
(version and integrity hash pinned in `package-lock.json`). Run from the repository root:

```sh
npm ci --ignore-scripts --prefix pipeline/style
node pipeline/style/generate_style.mjs
```

The output is deterministic, so a re-run must leave `style-light.json` unchanged. Glyphs and
sprites are not generated: they are copied from `protomaps/basemaps-assets` at a pinned commit
(see [docs/THIRD_PARTY.md](../docs/THIRD_PARTY.md) and [docs/GLYPHS.md](../docs/GLYPHS.md)).

## Rules

- Input data comes only from sources in [docs/DATA_SOURCES.md](../docs/DATA_SOURCES.md).
- Every tool used must be open source, listed in [docs/THIRD_PARTY.md](../docs/THIRD_PARTY.md)
  and in the `[pipeline]` section of `config/dependency-allowlist.txt`.
- Pin every input: tool versions with SHA-256, dated data builds (never "latest").
- Output is ODbL: packs carry "© OpenStreetMap contributors" and the ODbL notice.
- Generated output (`pipeline/out/`, `*.pmtiles`, `*.osm.pbf`) is never committed.
- Builds must be reproducible from this directory, which is how we meet ODbL share-alike.

## Backend

[`workers/`](workers/README.md) holds the Cloudflare Workers + D1 + R2 code that serves the pack
catalogue and downloads.