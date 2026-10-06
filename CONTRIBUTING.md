# Contributing to Orbit Maps

Thanks for helping! Repository: <https://github.com/orbitmaps-oss/orbitmaps>.
Please read [AGENTS.md](AGENTS.md) — its rules apply to every contribution — and follow our
[Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- For anything larger than a small fix, open an issue first so we can agree on the approach.
- **Security or privacy vulnerabilities:** don't open a public issue — report privately at
  <https://github.com/orbitmaps-oss/orbitmaps/security/advisories/new> (see [SECURITY.md](SECURITY.md)).
- **Wrong map data** (a missing road, a wrong name): fix it on
  [openstreetmap.org](https://www.openstreetmap.org); it will reach Orbit Maps with the next region packs.

## Workflow

1. Fork, then create a branch from `main` (`feature/…`, `fix/…`).
2. Make small, focused commits.
3. **Sign off every commit** (`git commit -s`), see below.
4. Run the checks locally:
   ```sh
   ./gradlew assembleDebug testDebugUnitTest test lintDebug ktlintCheck
   python scripts/check_dependencies.py
   python scripts/check_forbidden_files.py
   ```
5. Open a pull request and fill in the template.

## Developer Certificate of Origin (DCO) and licence grant

All commits must carry a `Signed-off-by:` line (added by `git commit -s`) certifying the
[Developer Certificate of Origin 1.1](https://developercertificate.org/).

**By signing off a commit you also agree that your contribution is licensed under the
GPL-3.0-or-later together with the App Store additional permission in
[LICENSE-ADDITIONAL-PERMISSION.md](LICENSE-ADDITIONAL-PERMISSION.md).** A section 7 additional
permission only holds if every copyright holder grants it, so we cannot accept contributions
without this grant.

## Rules for code

- **Tests:** every feature has unit tests; every bug fix has a regression test.
- **Strings:** all user-visible text goes in `res/values/strings.xml`. Translations are welcome.
- **Permissions:** don't add an Android permission without a reason. Add it to
  `config/permissions-allowlist.txt` with the reason and explain it in the PR; CI fails otherwise.
- **Privacy:** never log location, search queries, routes or IP addresses. No analytics, ads,
  trackers, crash reporters, Google Play Services or Firebase.
- **Licence headers:** new source files start with `// SPDX-License-Identifier: GPL-3.0-or-later`.
- **Style:** ktlint and Android Lint must pass.

## Rules for dependencies

Every new dependency (library, Gradle plugin, GitHub Action, pipeline tool) must:

1. be open source with a licence compatible with GPL-3.0 and **not GPL-only** — Apache-2.0, MIT,
   BSD, ISC, MPL-2.0, public domain/CC0 are fine. Third-party GPL code can't carry our App Store
   permission, so we can't use it in the app;
2. be listed in [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md) with its licence;
3. be added to [config/dependency-allowlist.txt](config/dependency-allowlist.txt);
4. have updated lockfiles and verification metadata:
   ```sh
   ./gradlew dependencies --write-locks --write-verification-metadata sha256
   ```

CI fails if a dependency outside the allow-list is added.

## Rules for data

Only data from OpenStreetMap, our own open APIs and the sources in
[docs/DATA_SOURCES.md](docs/DATA_SOURCES.md). Never scrape or copy from other map apps or services.
To add a data source, open a PR updating `docs/DATA_SOURCES.md` first.

## Never commit

Secrets, API tokens, keystores, `keystore.properties`, `google-services.json`, `.env` or
`.dev.vars` files, or IDE folders such as `.idea/`.

## Questions

Open an issue or discussion at <https://github.com/orbitmaps-oss/orbitmaps>, or mention the project
lead, [@Ravikant97](https://github.com/Ravikant97).