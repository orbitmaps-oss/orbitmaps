## What and why

<!-- What does this change, and why? Link the issue: "Fixes #123". -->

## Checklist

- [ ] Every commit is signed off (`git commit -s`, DCO). I agree my contribution is licensed under
      GPL-3.0-or-later with the [App Store additional permission](../LICENSE-ADDITIONAL-PERMISSION.md).
- [ ] New or changed behaviour has unit tests.
- [ ] User-visible text is in string resources, not hard-coded.
- [ ] `./gradlew assembleDebug testDebugUnitTest test lintDebug ktlintCheck` passes locally.

## Privacy and offline impact

- [ ] No analytics, ads, trackers, crash reporters, Google Play Services or Firebase.
- [ ] Nothing logs location, search queries, routes or IP addresses.
- [ ] Map, search and routing still work without a network.
- [ ] **No new permissions**, or each one is in `config/permissions-allowlist.txt` with a reason,
      explained here, and PRIVACY.md is updated:

## Dependencies and data

- [ ] **No new dependencies**, or each one is open source, listed in `docs/THIRD_PARTY.md` and
      `config/dependency-allowlist.txt`, with lockfiles and `gradle/verification-metadata.xml`
      updated.
- [ ] **No new data sources**, or each one is added to `docs/DATA_SOURCES.md`. Nothing is
      scraped from other map apps.
- [ ] No secrets, keystores, `google-services.json`, `.env` or `.dev.vars` files.
