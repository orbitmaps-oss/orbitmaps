# Security policy

## Supported versions

Orbit Maps is pre-alpha. Once releases exist, only the **latest release** and the `main` branch
receive security fixes.

## Reporting a vulnerability

**Please do not open a public issue, discussion or pull request for security problems.**

Report privately through GitHub private vulnerability reporting:

➡ <https://github.com/orbitmaps-oss/orbitmaps/security/advisories/new>

(or **Security → Report a vulnerability** on the repository page). This is the only reporting
channel; the report is visible only to you and the maintainers.

Please include:
- affected version / commit, device and Android version;
- steps to reproduce and the impact you expect;
- **no real personal data** — don't include your actual location, searches or other people's data.
  Use made-up coordinates.

## What to expect

| Step                         | Target                 |
|------------------------------|------------------------|
| Acknowledgement              | within 7 days          |
| Initial assessment           | within 14 days         |
| Fix or mitigation plan       | within 90 days         |

We will keep you updated in the advisory, credit you if you wish, and publish the advisory once a
fix is released.

## Scope

In scope: the Android app, region-pack pipeline, map styles, and our Cloudflare Workers backend code
in this repository. Privacy problems (e.g. location, searches or IPs being logged or sent anywhere
unexpected) count as security issues.

Out of scope: third-party services and upstream projects (report those upstream — e.g. MapLibre,
Valhalla, OpenStreetMap), and errors in map data (fix them on
[openstreetmap.org](https://www.openstreetmap.org)).

Project lead: [@Ravikant97](https://github.com/Ravikant97).
