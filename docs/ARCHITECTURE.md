# Architecture

> Pre-alpha overview. Modules are added only when the features that need them arrive.

## Region packs (offline-first)

A **region pack** is everything the app needs for one area, with no network:

| Part | Format | Used by |
|---|---|---|
| Map tiles | PMTiles (vector) | MapLibre Native |
| Search index | SQLite database with an FTS5 table | `core/search` |
| Routing tiles | Valhalla tile archive | valhalla-mobile, with Ferrostar for navigation |
| Manifest | JSON (version, bounds, checksums, licences) | `core/regionpack` |

Packs are built by `pipeline/` from OpenStreetMap extracts and served from Cloudflare R2. A
Cloudflare Worker (backed by D1) serves the pack catalogue. Downloads carry no user data, and the
server keeps no IP or request logs.

## Offline map (current step)

Until region-pack downloads exist, the app shows one development region:

1. `pipeline/sample_region/` extracts a small PMTiles file around Panaji from a pinned Protomaps
   build. **Debug** builds bundle it as the asset `regions/panaji.pmtiles` (stored uncompressed).
   Release builds don't include it and show a "no region installed" message.
2. On launch, `RegionInstaller` copies the asset to `filesDir/regions/panaji.pmtiles`. It writes to a
   `.tmp` file, syncs it, checks the PMTiles v3 header, and then renames it atomically. A marker file
   (app update time + size) means the copy is skipped on later launches, and repeated after an update.
3. `OfflineStyle` loads the bundled style (`styles/bundled/map/style-light.json`) and points its
   vector source at `pmtiles://file://<installed path>`. Glyphs and sprites are `asset://` URLs, and
   any `http(s)://` string in the style is rejected.
4. `MainActivity` turns MapLibre's logging off (log lines can contain tile coordinates) and puts it in
   offline mode. The manifest removes INTERNET and the other permissions MapLibre's library manifest
   asks for, so the OS blocks every socket. "© OpenStreetMap contributors" is always shown,
   bottom-start, inside the safe drawing area.

The map code lives in `app/.../map/` for now and moves to `core/map/` when a second user appears.

## Modules

```
app/                  Android app: Compose UI, navigation, DI wiring (in.orbitmaps.app)
core/model/           Pure Kotlin domain types (no Android dependencies)
core/map/             (planned) MapLibre + PMTiles integration
core/search/          (planned) FTS5 search over the pack's index
core/routing/         (planned) valhalla-mobile + Ferrostar
core/regionpack/      (planned) download, verify, install, update packs
pipeline/             (planned) pack build tooling; pipeline/workers/ = Cloudflare backend
styles/               map style JSON, sprites, glyph sources
```

Dependency direction: `app` → `core/*` → `core/model`. `core/model` stays plain Kotlin/JVM so it
can be tested quickly and shared with tooling.

## Privacy by construction

- No Google Play Services: location comes from the platform `LocationManager`.
- No network on the map, search or routing paths. Only `core/regionpack` talks to the network.
- CI enforces the dependency allow-list, the ban on tracking and Google dependencies, and the
  permission allow-list.
