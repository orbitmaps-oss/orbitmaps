# NLnet application draft (NGI Zero Commons Fund)

Draft for the maintainer to review and submit. Fields mirror NLnet's form. Everything marked
**TODO** needs a fact only the maintainer has; nothing there has been guessed. Check NLnet's current
call page for the exact deadline, limits and fields before submitting.

## Contact and project

- **Your name / organisation:** TODO
- **Email / phone / country:** TODO
- **Project name:** Orbit Maps
- **Website / repository:** https://github.com/orbitmaps-oss/orbitmaps (data host: https://data.orbitmaps.in)
- **Licence:** app code GPL-3.0-or-later (with an App Store additional permission, see
  `LICENSE-ADDITIONAL-PERMISSION.md`); map and search data ODbL.

## Abstract (aim for under 1200 characters)

Orbit Maps is an open-source, privacy-first maps app for Android that computes everything on the
phone. Drawing the map, searching places and routing with turn-by-turn voice guidance run locally
on OpenStreetMap data; our servers only hand out static files that are identical for every user, so
there is no account, no tracking and no query ever leaves the device. Downloaded region packs work
fully offline, and a trip's map tiles are saved automatically when navigation starts. The first
target is the Indian subcontinent, where data is expensive, connectivity is patchy and the dominant
map apps are proprietary. We ask for support to turn the working prototype into a releasable app:
reliable region packs for India, hardened offline routing and navigation, accessibility, and the
public build pipeline that anyone can run to reproduce the data.

## Why this matters (relevance to the call)

- A navigation app that works without sending a user's position, searches or routes to anyone is
  rare. Most open alternatives still rely on a hosted geocoder or router.
- Static-file-only hosting makes the service cheap enough to run at near-zero cost, so it can stay
  free and independent of any single company.
- Everything is reproducible: the pipeline in `pipeline/` rebuilds map, routing and search data from
  a public OSM extract.

## What exists today

- Android app (Kotlin, Jetpack Compose, minSdk 26): MapLibre Native map with PMTiles, on-device
  routing with Valhalla (valhalla-mobile), SQLite FTS5 search, our own navigation logic and voice
  prompts (see `docs/decisions/0001-own-navigation-logic.md`).
- Online-by-default streamed map with offline regions; routes fetch only the routing tiles along the
  trip, then are kept so the trip continues without signal.
- Region packs: documented format, builder, resumable verified downloads, and use of installed packs
  by map, search and routing.
- A CI policy gate that blocks analytics/Play Services/Firebase dependencies, unlisted network
  hosts, new permissions without a reason, and committed secrets.
- Hosting plan and cost budget in `docs/HOSTING.md`.

## Budget and work packages

Requested amount: **TODO** (NLnet funds roughly EUR 5,000 to 50,000; state your hourly rate and
hours per package). Suggested packages, to be sized by the maintainer:

1. **India data pipeline** — reproducible builds of the world map, routing tiles and search shards
   for the subcontinent; region packs for the major states; automated monthly refresh on free CI
   runners; size and cost measurements published.
2. **Search quality for Indian addresses** — transliteration and multi-script names (Devanagari,
   Tamil, Bengali and others), localities and landmarks, ranking tests.
3. **Navigation hardening** — rerouting, lane and junction prompts, voice in Indian languages via
   the system text-to-speech, field tests on low-end devices, battery and memory budgets.
4. **Accessibility and localisation** — TalkBack support, large text, Hindi and at least two more
   languages in the string resources.
5. **Release engineering** — reproducible signed builds, F-Droid inclusion and metadata, the
   privacy documentation kept in step with the code.
6. **Security and privacy review** — independent review of the download verification, the network
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
- Searching large regions on-device without a server: shard layout, FTS5 index size, ranking.
- Making downloads safe on poor networks: resumable, verified, atomic install.
- Staying inside a near-zero hosting budget as usage grows (cache rules, size trimming, free-tier
  limits).

## Ecosystem and who benefits

Users in India and other low-bandwidth regions; other open projects that can reuse the region-pack
format, the pipeline and the policy checks; OSM contributors, since better downstream use of the
data increases the incentive to improve it. Everything is published under free licences so others
can fork, host their own data or build another region.

## Other funding and sustainability

- **Other funding:** TODO (state none, or list it).
- **Running costs:** designed to fit free tiers (see `docs/HOSTING.md`); donations or sponsorship
  can raise limits later. TODO: confirm there is no commercial plan or state it openly.

## Attachments to prepare

- Link to the repository and `PRIVACY.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/HOSTING.md`.
- Short screen recording of: first launch, a search, a route with voice, and a region download.
- A one-page architecture diagram (map / search / routing all on the phone, static files only on
  the server).
