# Orbit Maps

**A privacy-first, offline-first maps app for Android.**

Orbit Maps shows maps, searches places and plans routes entirely on your device, from region packs
you download once. No account, no tracking, no ads.

> **Status: pre-alpha.** The repository is being set up; there is nothing to install yet.

## Principles

- **Private.** No analytics, ads, trackers, Google Play Services or Firebase. We do not log your
  location, searches or IP address. See [PRIVACY.md](PRIVACY.md).
- **Offline.** Map, search and routing work with no network, from downloaded region packs.
- **Open.** Data from [OpenStreetMap](https://www.openstreetmap.org) and other open sources listed in
  [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md). Code is free software.

## Stack

Kotlin · Jetpack Compose · MapLibre Native (PMTiles) · valhalla-mobile · Ferrostar · SQLite FTS5 ·
Cloudflare Workers + D1 + R2. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Repository layout

| Path         | What                                                         |
|--------------|--------------------------------------------------------------|
| `app/`       | Android application (`in.orbitmaps.app`)                      |
| `core/`      | Shared Kotlin/Android library modules                         |
| `pipeline/`  | Region-pack build tooling and Cloudflare Workers backend      |
| `styles/`    | Map styles, sprites and glyph sources                         |
| `docs/`      | Architecture, data sources, third-party licences              |
| `fastlane/`  | Store / F-Droid metadata                                      |
| `config/`    | CI policy: dependency and permission allow-lists              |
| `scripts/`   | CI policy checks                                              |

## Building

Requirements: JDK 17 or newer (the JDK bundled with Android Studio works) and the Android SDK.

```sh
git clone https://github.com/orbitmaps-oss/orbitmaps.git
cd orbitmaps
./gradlew assembleDebug
./gradlew testDebugUnitTest test lintDebug ktlintCheck
```

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) (commits need a DCO
sign-off), the [Code of Conduct](CODE_OF_CONDUCT.md) and [GOVERNANCE.md](GOVERNANCE.md).
Security issues: report privately at
<https://github.com/orbitmaps-oss/orbitmaps/security/advisories/new> (see [SECURITY.md](SECURITY.md)).
Please don't open public issues for them.

Project lead: [@Ravikant97](https://github.com/Ravikant97) ·
Repository: <https://github.com/orbitmaps-oss/orbitmaps> ·
Issues: <https://github.com/orbitmaps-oss/orbitmaps/issues>

## Licence

- **Code:** [GPL-3.0-or-later](LICENSE), with an
  [App Store additional permission](LICENSE-ADDITIONAL-PERMISSION.md) so that Orbit Maps can be
  distributed through app stores. *(The wording has not yet had legal review.)*
- **Map data:** [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/). © OpenStreetMap contributors.
- **Third-party components:** see [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md).

Copyright © 2026 Ravikant and Orbit Maps contributors.