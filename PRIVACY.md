# Privacy policy

Orbit Maps is built so that we **cannot** know where you are, what you search for, or where you go.

## What we collect

**Nothing.** There is no account, and the app contains no analytics, advertising, tracking,
crash-reporting, Google Play Services or Firebase code.

## What stays on your device

- Your location — used only on the device to show your position and for navigation.
- Downloaded region packs (map, search and routing data).
- Search history, saved places and settings.

All of it can be deleted from within the app or by clearing the app's data. None of it is uploaded.
Android cloud backup is configured to exclude this data.

## Network use

The app works offline. It connects to the network only when you choose to:

- **Download or update a region pack** — from our servers (Cloudflare R2 / Workers). The request
  contains only which pack and version is wanted, never your location.
- **Use an online feature that you explicitly turn on**, if any are added later; each will be
  documented here before release.

Our servers do **not** log IP addresses or request contents, and we don't run analytics on them.
Cloudflare, as our hosting provider, necessarily processes IP addresses to deliver traffic; we do
not receive or keep them.

## Permissions

Every Android permission the app requests is listed, with the reason, in
[`config/permissions-allowlist.txt`](config/permissions-allowlist.txt). CI fails if a permission is
added without being listed there.

## Verify it yourself

The source is public. Our CI rejects tracking and Google/Firebase dependencies
([`config/forbidden-dependencies.txt`](config/forbidden-dependencies.txt)) and any dependency not on
the allow-list.

## Changes and contact

Changes to this policy are made in public pull requests to
<https://github.com/orbitmaps-oss/orbitmaps>. Privacy concerns can be raised in a GitHub issue, or
privately at <https://github.com/orbitmaps-oss/orbitmaps/security/advisories/new>.
