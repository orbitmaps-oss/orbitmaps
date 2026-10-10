# Hosting the world data (`data.orbitmaps.in`)

The app computes everything on the phone; this host only serves **static files** that are the same
for everyone. A maintainer builds and uploads them. Agents and CI never upload or deploy
(AGENTS.md section 7).

## Layout

All files live in one Cloudflare R2 bucket, served at `https://data.orbitmaps.in/` through R2's
custom-domain feature. Paths are versioned (`v1/…`), so a new layout never breaks older apps.

| Path | What | Built with | Used by |
|---|---|---|---|
| `v1/tiles/world.pmtiles` | The world map, one PMTiles archive | A dated Protomaps planet build | `OnlineData.WORLD_TILES_URL`: MapLibre streams the tiles in view with HTTP range requests |
| `v1/routing/valhalla-3.6.3/valhalla.json` | Valhalla config generated with the tiles | `valhalla_build_config` | `TripRouting`: fetched once, paths rewritten on the phone |
| `v1/routing/valhalla-3.6.3/{0,1,2}/…/….gph` | Valhalla graph tiles, one file per tile | `valhalla_build_tiles` | `TileStore`: fetched per trip, kept along started trips |
| `v1/search/v2/2/…/….sqlite` | Area search shards, one per 0.25° cell | `build_search_shards.py` | `ShardSearch`: fetched around the map centre |

The routing folder name carries the Valhalla version: **it must match the Valhalla inside
valhalla-mobile** (3.6.3 for valhalla-mobile 0.6.3). When valhalla-mobile is upgraded, build a new
folder (e.g. `valhalla-3.9.1/`) and keep the old one until no supported app uses it. The search
folder carries the index schema version (`v2`, `SCHEMA_VERSION` in `build_sample_search.py`).

## R2 and Cloudflare settings

1. Create a bucket (e.g. `orbitmaps-data`) and connect the custom domain `data.orbitmaps.in`
   (Settings → Custom Domains). Keep the `r2.dev` public URL **off**.
2. Turn **off** request logging and analytics that record IPs where you can (Logpush off,
   Workers Logs off, no Web Analytics on this hostname), as PRIVACY.md promises.
3. Add a cache rule for `data.orbitmaps.in/v1/*`: cache everything, long edge TTL. Files never
   change in place; a new build gets a new name or folder.
4. Set a spending alert before any paid plan. R2 has **no egress fees**; storage is about
   $0.015 per GB-month, and operations are billed per million (check current pricing).
5. HTTPS only (the app refuses plain http). No CORS is needed for the app.

## Staying at zero cost

Everything below is chosen so the running cost is ₹0 while the project is small, and a few cents
when it grows. Prices are from memory: check Cloudflare's current R2 pricing before relying on them.

| Free allowance (per month) | Limit | How we stay inside |
|---|---|---|
| R2 storage | 10 GB | Ship India first and budget the files (below); never the whole planet at once |
| R2 reads (Class B) | 10 million | A cache rule on `data.orbitmaps.in`: cached answers don't reach R2, so most tile requests are free |
| R2 writes (Class A) | 1 million | Build rarely (monthly) and upload only changed files |
| R2 download traffic | unlimited, no egress fees | Nothing to do |
| Builds | GitHub Actions is free for public repositories | Build by zone on free runners; rent a machine only for the full-country routing build, if ever |
| Servers | none | Everything is static files; the phone does the computing |

Going over 10 GB is not a cliff: extra storage is about $0.015 per GB-month, so 20 GB is roughly
15 cents a month.

### A 10 GB budget for the Indian subcontinent (estimates, measure on the first build)

| File | Target | How to stay inside it |
|---|---|---|
| World map (`v1/tiles/world.pmtiles`) | at most 4 GB | Run the `pmtiles extract --dry-run` first; use `--maxzoom=14` or `13` if it is bigger. The app draws deeper zoom levels by enlarging the last one |
| Routing tiles (`v1/routing/...`) | at most 3 GB | Build India (and neighbours later) by zone; tiles are separate files, so zones can be added one by one |
| Search shards (`v1/search/v2/...`) | at most 1 GB | Start with the pilot areas only; elsewhere the bundled world index of cities answers |
| Headroom | 2 GB | Old versions kept for rollback, test files |

### On the phone

The app keeps its downloaded routing tiles under about 400 MB (the least recently used go first,
never the tiles of the trip being driven), and map tiles are cached by MapLibre within its own
limit, so using the app doesn't fill the phone either.

## 1. World map tiles

Pick one dated planet build whose **tileset version matches the bundled style**
(`@protomaps/basemaps` in `pipeline/style/`, tileset 4.x; see
<https://build-metadata.protomaps.dev/builds.json>) and cut out the area you serve. Never
hot-link Protomaps' build server from the app.

For the Indian subcontinent (India, Pakistan, Nepal, Bhutan, Bangladesh, Sri Lanka), straight from
the pinned build, downloading only the tiles in the box:

```sh
pmtiles extract https://build.protomaps.com/20260811.pmtiles india.pmtiles   --bbox=60.5,5.5,98.0,37.5 --maxzoom=15 --dry-run   # prints the size first
pmtiles extract https://build.protomaps.com/20260811.pmtiles india.pmtiles   --bbox=60.5,5.5,98.0,37.5 --maxzoom=15
pmtiles verify india.pmtiles
rclone copyto india.pmtiles r2:orbitmaps-data/v1/tiles/world.pmtiles --s3-chunk-size 64M --progress
```

The dashboard's upload button is limited to a few hundred MB, so large files go through rclone with
an R2 API token scoped to this bucket (keep the token out of the repository and chats). The
whole planet is about 120 GB (estimate); extend the box later, within the budget above.

Record the build date in the release notes. Updating means uploading a newer build under the same
name only when the tileset version still matches the style; otherwise update the style first.

## 2. Routing tiles (needs a big machine)

The planet graph needs roughly **64 GB of RAM and 300 GB of disk for several hours** (estimate);
free GitHub runners can't do it. Rent a machine for the build day or use a donated one. Smaller
areas (one country) work the same way on smaller machines.

```sh
curl -fLO https://planet.openstreetmap.org/pbf/planet-latest.osm.pbf
mkdir -p build && mv planet-latest.osm.pbf build/
docker run --rm -v "$PWD/build:/data" --entrypoint valhalla_build_config \
  ghcr.io/valhalla/valhalla:3.6.3-amd64@sha256:0cf1520c6a38b8a7e13a1931541e0ab6e9e42b64b4ca014293b6b8373d493160 \
  --mjolnir-tile-dir /data/tiles --mjolnir-admin /data/admins.sqlite \
  --mjolnir-timezone /data/timezones.sqlite > build/valhalla.json
docker run --rm -v "$PWD/build:/data" --entrypoint valhalla_build_admins \
  ghcr.io/valhalla/valhalla:3.6.3-amd64@sha256:0cf1520c6a38b8a7e13a1931541e0ab6e9e42b64b4ca014293b6b8373d493160 \
  -c /data/valhalla.json /data/planet-latest.osm.pbf
docker run --rm -v "$PWD/build:/data" --entrypoint valhalla_build_tiles \
  ghcr.io/valhalla/valhalla:3.6.3-amd64@sha256:0cf1520c6a38b8a7e13a1931541e0ab6e9e42b64b4ca014293b6b8373d493160 \
  -c /data/valhalla.json /data/planet-latest.osm.pbf
rclone copy build/tiles r2:orbitmaps-data/v1/routing/valhalla-3.6.3/ --transfers 64
rclone copyto build/valhalla.json r2:orbitmaps-data/v1/routing/valhalla-3.6.3/valhalla.json
```

The image is the one pinned in `pipeline/sample_region/build_sample_routing.py`. Admin areas make
routes respect country rules (driving side, border crossings); the sample build skips them, the
world build should not.

## 3. Search shards

```sh
python pipeline/sample_region/build_search_shards.py build/planet-latest.osm.pbf out/search-v2
rclone copy out/search-v2 r2:orbitmaps-data/v1/search/v2/ --transfers 64
```

Needs osmium-tool and Python with FTS5. Same machine as the routing build; it writes one small
file per populated 0.25° cell.

## Updating

- **Monthly**: rebuild routing tiles and search shards from a fresh planet file, upload over the
  same paths (tiles are self-contained; a phone mixing old and new tiles for a day is harmless).
- **When the style changes**: upload a matching world map build.
- **When valhalla-mobile changes Valhalla version**: new routing folder, keep the old one.

Each upload is a maintainer action, recorded in the release notes with the build date and the
source file's checksum. The output is ODbL; `pipeline/` is the method to recreate it.

## Rough storage (estimates, check after the first build)

| Data | Size |
|---|---|
| World map tiles | ~120 GB |
| Routing tiles, planet | ~80–100 GB |
| Search shards, planet | ~40–80 GB |
| **Total** | **~250–300 GB, about $4–5 a month on R2** |
