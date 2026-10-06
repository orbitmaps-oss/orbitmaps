# Third-party components

Every dependency of Orbit Maps (libraries, Gradle plugins, GitHub Actions, pipeline tools, fonts)
is listed here with its licence. Each one must also be in
[`config/dependency-allowlist.txt`](../config/dependency-allowlist.txt). CI checks that every
allow-list entry appears in this file (by the `group` or `group:artifact` in the **Coordinates**
column).

Allowed licences are open source, compatible with GPL-3.0, and not GPL-only: Apache-2.0, MIT,
BSD-2-Clause, BSD-3-Clause, ISC, MPL-2.0, Zlib, Unicode, public domain/CC0, and OFL-1.1 for
fonts. **Anything else: stop and ask.**

## In use

| Component | Coordinates | Licence | Why |
|---|---|---|---|
| *(none yet)* | | | |

## Planned (not yet added)

| Component | Licence | URL | Note |
|---|---|---|---|
| MapLibre Native (Android) | BSD-2-Clause | https://github.com/maplibre/maplibre-native | Map rendering, PMTiles support |
| PMTiles | BSD-3-Clause | https://github.com/protomaps/PMTiles | Tile archive format |
| Ferrostar | BSD-3-Clause | https://github.com/stadiamaps/ferrostar | Navigation core and UI |
| Valhalla | MIT | https://github.com/valhalla/valhalla | Routing engine |
| valhalla-mobile | **to verify when added** | https://github.com/Rallista/valhalla-mobile | Valhalla bindings for Android |
| SQLite (FTS5) | Public domain | https://sqlite.org | Offline search |
