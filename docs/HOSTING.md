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

## 1. World map tiles

Pick one dated planet build whose **tileset version matches the bundled style**
(`@protomaps/basemaps` in `pipeline/style/`, tileset 4.x; see
<https://build-metadata.protomaps.dev/builds.json>), download it once and upload it. Never
hot-link Protomaps' build server from the app.

```sh
# about 120 GB (estimate); any machine with the disk space
curl -fLO https://build.protomaps.com/YYYYMMDD.pmtiles
pmtiles verify YYYYMMDD.pmtiles
rclone copyto YYYYMMDD.pmtiles r2:orbitmaps-data/v1/tiles/world.pmtiles --s3-upload-cutoff 100M
```

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
