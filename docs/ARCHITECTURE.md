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
