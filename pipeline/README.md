# Region-pack pipeline

> Placeholder. No tooling has been chosen yet.

This directory will hold the tooling that turns OpenStreetMap extracts into Orbit Maps
**region packs** (see [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)):

```
OSM extract (.osm.pbf)
  ├─► vector tiles  ──► PMTiles archive        (map display, MapLibre Native)
  ├─► search index  ──► SQLite + FTS5 database (offline search)
  └─► routing graph ──► Valhalla tile archive  (offline routing, valhalla-mobile)
      + manifest.json (version, bounds, SHA-256 checksums, licences)
```

## Rules

- Input data comes only from sources in [docs/DATA_SOURCES.md](../docs/DATA_SOURCES.md).
- Every tool used must be open source and listed in [docs/THIRD_PARTY.md](../docs/THIRD_PARTY.md).
- Output is ODbL: packs carry "© OpenStreetMap contributors" and the ODbL notice.
- Generated output (`pipeline/out/`, `*.pmtiles`, `*.osm.pbf`) is never committed.
- Builds must be reproducible from this directory, which is how we meet ODbL share-alike.

## Backend

[`workers/`](workers/README.md) holds the Cloudflare Workers + D1 + R2 code that serves the pack
catalogue and downloads.
