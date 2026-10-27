# NLnet application draft (NGI Zero Commons Fund)

Draft for the maintainer to review and submit. Fields mirror NLnet's form. Everything marked
**TODO** needs a fact only the maintainer has; nothing there has been guessed. Check NLnet's current
call page for the exact deadline, limits and fields before submitting.

## Contact and project

- **Your name / organisation:** Ravikant Jangid (individual)
- **Email / phone / country:** enter these in NLnet's form only (India); this file is public.
- **Project name:** Orbit Maps
- **Website / repository:** https://github.com/orbitmaps-oss/orbitmaps (data host: https://data.orbitmaps.in)
- **Licence:** app code GPL-3.0-or-later (with an App Store additional permission, see
  `LICENSE-ADDITIONAL-PERMISSION.md`); map and search data ODbL.

## Abstract (aim for under 1200 characters)

Orbit Maps is an open-source, privacy-first maps app for Android that computes everything on the
phone. Drawing the map, searching places and routing with turn-by-turn voice guidance run locally
on OpenStreetMap data; our servers only hand out static files that are identical for every user, so
there is no account, no tracking and no route or position ever leaves the device. Searching as you
type happens on the phone; only when the user taps "Search online" do the typed text and the map
centre go to OpenStreetMap's public geocoder (optional, never the GPS position). Downloaded region
packs work fully offline, and a trip's map tiles are saved automatically when navigation starts. The first
target is the Indian subcontinent, where data is expensive, connectivity is patchy and the dominant
map apps are proprietary. We ask for support to turn the working prototype into a releasable app:
reliable region packs for India, hardened offline routing and navigation, accessibility, and the
public build pipeline that anyone can run to reproduce the data.

## Why this matters (relevance to the call)

- A navigation app that works without sending a user's position or routes to anyone is rare. Most
  open alternatives still rely on a hosted geocoder or router. Ours answers searches on the phone as
  you type; an optional online search, only on request, sends the typed text and the map centre to
  OpenStreetMap's public Nominatim and falls back to the phone when offline.
- Static-file-only hosting makes the service cheap enough to run at near-zero cost, so it can stay
  free and independent of any single company.
- Everything is reproducible: the pipeline in `pipeline/` rebuilds map, routing and search data from
  a public OSM extract.

## What exists today

- Android app (Kotlin, Jetpack Compose, minSdk 26): MapLibre Native map with PMTiles, on-device
  routing with Valhalla (valhalla-mobile), SQLite FTS5 search (plus optional online search via Nominatim), our own navigation logic and voice
  prompts (see `docs/decisions/0001-own-navigation-logic.md`).
- Online-by-default streamed map with offline regions; routes fetch only the routing tiles along the
  trip, then are kept so the trip continues without signal.
- Region packs: documented format, builder, resumable verified downloads, and use of installed packs
  by map, search and routing.
- A CI policy gate that blocks analytics/Play Services/Firebase dependencies, unlisted network
  hosts, new permissions without a reason, and committed secrets.
- Hosting plan and cost budget in `docs/HOSTING.md`.

## Budget and work packages

Requested amount: **EUR 29,000** = 520 hours at EUR 50/hour (EUR 26,000) plus a fixed EUR 3,000
for an independent security review. Hours are estimates; adjust to what the maintainer can commit
to (about six months part-time) and check NLnet's current rate guidance. Packages:

1. **India data pipeline** (120 h) — reproducible builds of the world map, routing tiles and search shards
   for the subcontinent; region packs for the major states; automated monthly refresh on free CI
   runners; size and cost measurements published.
2. **Search quality for Indian addresses** (100 h) — transliteration and multi-script names (Devanagari,
   Tamil, Bengali and others), localities and landmarks, ranking tests.
3. **Navigation hardening** (140 h) — rerouting, lane and junction prompts, voice in Indian languages via
   the system text-to-speech, field tests on low-end devices, battery and memory budgets.
4. **Accessibility and localisation** (60 h) — TalkBack support, large text, Hindi and at least two more
   languages in the string resources.
5. **Release engineering** (60 h) — reproducible signed builds, F-Droid inclusion and metadata, the
   privacy documentation kept in step with the code.
6. **Self-hostable place search** (40 h) — the optional online search currently uses OpenStreetMap's public
   Nominatim, which is not meant for heavy use. We document and script a Nominatim deployment for the
   Indian subcontinent that anyone can run, with no request logging or IP storage, point the app at our
   own instance (an HTTPS host added to `config/network-hosts.txt` and `PRIVACY.md` first), and measure
   its memory, disk and monthly cost. The app keeps working without it: typing searches on the phone, and
   online search falls back to the phone when the server is unreachable.
7. **Security and privacy review** (fixed EUR 3,000) — independent review of the download verification, the network
   host policy and the absence of identifiers.

## Comparison with existing efforts

| Project | Similar | Different |
|---|---|---|
| OsmAnd | Offline maps, OSM data | Larger and more complex; no streamed-then-saved trip model; different privacy posture around optional online services |
| Organic Maps / CoMaps | Offline, privacy-focused, OSM | Own vector format; we use standard PMTiles and Valhalla so data pipelines can be shared and reused |
| Google Maps and similar | Features users expect | Closed data, accounts, location history; none of it reproducible |

TODO: confirm each row against the projects' current state before submitting; state respectfully
what we reuse and contribute back (OSM data quality, PMTiles/Valhalla fixes, the pack format).

## Technical challenges

- Keeping routing correct and fast on a phone with limited memory (memory-mapped tile tar, one
  engine per region, tile corridor fetching along a trip).
- Searching large regions on-device, without a server by default: shard layout, FTS5 index size, ranking.
- Keeping the optional online search within OpenStreetMap's Nominatim usage policy (one request per
  submitted search, rate-limited) and private: only the typed text and the map centre are sent.
- Making downloads safe on poor networks: resumable, verified, atomic install.
- Staying inside a near-zero hosting budget as usage grows (cache rules, size trimming, free-tier
  limits).

## Ecosystem and who benefits

Users in India and other low-bandwidth regions; other open projects that can reuse the region-pack
format, the pipeline and the policy checks; OSM contributors, since better downstream use of the
data increases the incentive to improve it. Everything is published under free licences so others
can fork, host their own data or build another region.

## Other funding and sustainability

- **Other funding:** none; the project is self-funded.
- **Running costs:** designed to fit free tiers (see `docs/HOSTING.md`); donations or sponsorship
  can raise limits later. TODO: confirm there is no commercial plan or state it openly.

## Attachments to prepare

- Link to the repository and `PRIVACY.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/HOSTING.md`.
- Short screen recording of: first launch, a search, a route with voice, and a region download.
- A one-page architecture diagram (map / routing / offline search all on the phone, static files
  from our server, and the optional online search to OpenStreetMap only on request).
