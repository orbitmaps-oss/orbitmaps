# Map styles

> Placeholder. The first style arrives with the MapLibre integration.

This directory will hold the MapLibre style JSON, sprite sources (SVG icons) and glyph build
configuration used by the app.

## Rules

- Styles are written for our own tile schema, produced by `pipeline/`.
- Never copy styles, icons or sprites from other map apps or services.
- Icons must be original or under an open licence compatible with GPL-3.0 (e.g. CC0, MIT,
  Apache-2.0, OFL for icon fonts), and recorded in [docs/THIRD_PARTY.md](../docs/THIRD_PARTY.md).
- Fonts for glyphs: Noto (SIL OFL 1.1). Ship the OFL text with the glyphs.
- Every style shows "© OpenStreetMap contributors".

Licence: GPL-3.0-or-later unless a file says otherwise.
