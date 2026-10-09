# Data sources

Orbit Maps uses **only** the data sources listed here. Adding a source requires a pull request to
this file first, stating its licence, required attribution and how it is used.
**Never scrape or copy** data, tiles, styles, icons or API responses from other map apps or
services (Google Maps, Apple Maps, HERE, Bing, etc.), even if they are publicly reachable.

| Source | Licence | Required attribution | Used for | Status |
|---|---|---|---|---|
| [OpenStreetMap](https://www.openstreetmap.org) | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) | "© OpenStreetMap contributors", linked to <https://www.openstreetmap.org/copyright> | Map tiles, search index, routing graph in region packs | Planned |
| Orbit Maps open APIs (Cloudflare Workers + D1 + R2, code in `pipeline/workers/`) | Code GPL-3.0-or-later; data ODbL (derived from OSM) | As for OSM | Region-pack catalogue and downloads | Planned |
| [Protomaps Basemap](https://docs.protomaps.com/basemaps/downloads) daily build (planet PMTiles, built from OSM and Natural Earth) | ODbL 1.0 (Produced Work of OSM) | "© OpenStreetMap contributors", as for OSM | Development sample region (`pipeline/sample_region/`), pinned to build `20260811` (tileset 4.15.1). Only a small extract is downloaded, and it is never committed | In use (debug builds only) |
| [Geofabrik OSM extracts](https://download.geofabrik.de) (`.osm.pbf` per region, a mirror of OpenStreetMap data) | ODbL 1.0 (OSM data) | "© OpenStreetMap contributors", as for OSM | Routing tiles for the development sample region (`pipeline/sample_region/build_sample_routing.py`): the dated India Western Zone extract `western-zone-260101.osm.pbf` (pinned with Geofabrik's MD5) is cut to the Panaji box and built into Valhalla tiles by a GitHub Actions workflow. Nothing is committed | In use (debug builds only) |
| [Natural Earth](https://www.naturalearthdata.com) | Public domain | None required (credit appreciated: "Made with Natural Earth") | Low-zoom world overview (coastlines, borders), included in the Protomaps Basemap build. **World places search index:** `ne_10m_populated_places.geojson` from [nvkelso/natural-earth-vector v5.1.2](https://github.com/nvkelso/natural-earth-vector/tree/v5.1.2/geojson) (about 7,300 cities and towns, names in 26 languages), pinned by git blob SHA-1 in `pipeline/sample_region/build_world_places.py` | In use (via Protomaps Basemap; world places index) |
| [Noto fonts](https://github.com/notofonts) | [SIL OFL 1.1](https://openfontlicense.org) | Licence text shipped with glyphs | Map label glyphs | Planned |

## Obligations

- **Attribution:** the map screen always shows "© OpenStreetMap contributors" and links to the
  copyright page. The About screen lists every source above.
- **ODbL share-alike:** region packs are Derivative Databases. We publish them, or the method to
  recreate them (the `pipeline/`), under ODbL.
- **Corrections** go upstream to OpenStreetMap, not into our packs.
