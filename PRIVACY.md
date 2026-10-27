# Privacy policy

Orbit Maps is built so that we **cannot** know who you are, what you search for, or where you go.

## What we collect

**Nothing.** There is no account, and the app contains no analytics, advertising, tracking,
crash-reporting, Google Play Services or Firebase code.

## What stays on your device

- Your location — used only on the device to show your position, rank nearby results and start
  routes. It is read from Android's own location service (no Google Play Services), only while the
  app is open, after you allow it the first time you tap *Show my location*; approximate location is
  enough. It is never sent, stored or logged, and it doesn't decide which map files are fetched (the
  map's centre does).
- Downloaded region packs (map, search and routing data).
- Search history, saved places and settings.

All of it can be deleted from within the app or by clearing the app's data. None of it is uploaded.
Android cloud backup is configured to exclude this data.

## Network use

Orbit Maps draws the map and plans routes on your phone. Our servers only hand out **static files**,
the same for everyone, from `data.orbitmaps.in` (Cloudflare R2). The one other server the app contacts
is OpenStreetMap's public geocoder, `nominatim.openstreetmap.org`, for **online place search** (see
below). Nothing else is contacted ([`config/network-hosts.txt`](config/network-hosts.txt), checked by
CI) and only HTTPS is used.

| What the app fetches | When | What the request reveals |
|---|---|---|
| **Map tiles** for the area on screen | While you look at an area you haven't downloaded (switch: *Browse undownloaded areas*, on by default) | Which map squares are being viewed. Not who, and not your position |
| **Routing tiles** for a trip | When you plan a route outside downloaded regions | The rough corridor of the trip (squares of about 25 km along roads, larger elsewhere). Never the start, destination or route itself; the route is calculated on the phone |
| **Place search** (Nominatim, run by the OpenStreetMap Foundation) | When you search and *Browse undownloaded areas* is on (it is by default) and you are online | **The text you type** and the **centre of the map** you are looking at, sent to OpenStreetMap. Never your GPS position, account or device ID. OpenStreetMap's servers may log IP addresses under [their privacy policy](https://osmfoundation.org/wiki/Privacy_Policy); we do not receive that data. If the request fails or you are offline, the same search runs on your phone over downloaded data |
| **Search shards** for an area | When you search in an area you haven't downloaded | Which area is searched. Never what you type; matching happens on the phone |
| **Region list** (`regions/index.json`) | When you open *Offline regions* while online | That the list was fetched. The same file for everyone; it never carries your position |
| **Region packs** and their updates | When you tap *Download* on a region | Which region and version. Resumed downloads ask for the rest of the same file |

Requests carry **no account, device ID, advertising ID, cookie or location**, only the name of
the file. The app asks for a generic name ("OrbitMaps") instead of Android's default, which
includes your phone model. Fetched tiles and the corridor of a started trip are kept on the phone so the trip
continues without signal; they are deleted automatically after a while unless you save the area.

Turn off *Browse undownloaded areas* in **Privacy and settings** to use only downloaded data. Searches
then never leave your phone.

Our servers do **not** log IP addresses or request contents, and we don't run analytics on them.
Cloudflare, as our hosting provider, necessarily processes IP addresses to deliver traffic; we do
not receive or keep them. Online features we add later (for example adding places or reporting
hazards) will be documented here before release, and none of them will send your routes
or GPS position.

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
