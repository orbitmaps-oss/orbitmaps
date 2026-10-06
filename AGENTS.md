# AGENTS.md — rules for AI agents and contributors

Orbit Maps is a public, open-source, **privacy-first, offline-first** maps app for Android, used worldwide.
These rules are binding for every change, human or AI. If a task conflicts with them, stop and ask.

## 1. Privacy first
- The main build must not contain analytics, ads, trackers, crash-reporting SDKs, Google Play Services or Firebase.
  Banned dependency groups include (see `config/forbidden-dependencies.txt`):
  `com.google.android.gms`, `com.google.firebase`, `com.google.android.play`, `com.google.android.ump`,
  and any analytics, ads, attribution or crash SDK (e.g. Sentry, Crashlytics, Bugsnag, AppsFlyer, Adjust, Mixpanel, Amplitude, Segment).
- Never log location (coordinates, addresses, geohashes), search queries, routes, or IP addresses —
  not in Android `Log`, not in server logs, not in exceptions or test output committed to the repo.
- Location, history and saved places stay on the device. No silent network calls.
- Server code (Cloudflare Workers) must not store or log client IPs or request contents.

## 2. Offline first
- Map display, search and routing must work from downloaded **region packs** with no network.
- Network is only used for downloading or updating region packs and calling our own open APIs, and the app must degrade gracefully without it.

## 3. Data sources
- Use data only from OpenStreetMap, our own open APIs, and sources listed in `docs/DATA_SOURCES.md`.
- **Never scrape** or copy data, tiles, styles or APIs from other map apps or services (Google, Apple, HERE, etc.).
- Adding a data source requires a PR to `docs/DATA_SOURCES.md` first, with its licence and attribution text.

## 4. Licensing
- App code: **GPL-3.0-or-later** with the App Store additional permission in `LICENSE-ADDITIONAL-PERMISSION.md`.
  New source files start with `// SPDX-License-Identifier: GPL-3.0-or-later`.
- Map/search data (region packs): **ODbL**. Always show "© OpenStreetMap contributors".
- **Every new dependency** (library, Gradle plugin, GitHub Action, pipeline tool) must be:
  1. open source, with a licence compatible with GPL-3.0 and **not GPL-only** (third-party GPL code cannot carry our App Store permission) — Apache-2.0, MIT, BSD, ISC, MPL-2.0, public domain/CC0 are fine;
  2. added to `docs/THIRD_PARTY.md` (name, version, licence, URL, why);
  3. added to `config/dependency-allowlist.txt`.
- **If a dependency is not open source, or its licence is unclear, stop and ask. Do not add it.**

## 5. Stack
- Android: Kotlin, Jetpack Compose, minSdk 26.
- Map rendering: MapLibre Native with PMTiles.
- Routing: valhalla-mobile (on-device Valhalla), navigation UI/logic: Ferrostar.
- Search: SQLite FTS5.
- Backend: Cloudflare Workers + D1 + R2 (in `pipeline/workers/`).
- Package / namespace: `in.orbitmaps.app`.

## 6. Quality
- Every feature has unit tests. Bug fixes come with a regression test.
- All user-visible strings live in resources (`res/values/strings.xml`), never hard-coded.
- No new Android permission without explaining why: add it to `config/permissions-allowlist.txt` with a reason, and mention it in the PR. CI fails otherwise.
- Keep `./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck` green.

## 7. Safety
- Never commit secrets, API tokens, keystores (`*.jks`, `*.keystore`), `keystore.properties`, `google-services.json`, or `.env` / `.dev.vars` files.
- **Never push, deploy, or touch live servers.** No `git push`, no `wrangler deploy`/`publish`, no writes to production D1/R2, no release uploads. A human does these.
- Don't modify CI policy files (`config/`, `scripts/check_*.py`, `.github/workflows/`) to make a failing check pass without explicit approval.

## 8. Workflow
- Work on a branch, never directly on `main`.
- Small, focused commits, each signed off (`git commit -s`, DCO).
- Explain what you are going to change and why **before** editing.

## Commands
```sh
./gradlew assembleDebug                 # build
./gradlew testDebugUnitTest test        # unit tests (Android + JVM modules)
./gradlew lintDebug ktlintCheck         # lint
./gradlew dependencies --write-locks    # after changing dependencies, then commit lockfiles
python scripts/check_dependencies.py    # dependency allow-list
python scripts/check_forbidden_files.py # no secrets / keystores tracked
python -m unittest discover -s scripts -p "test_*.py"
```
