# Map glyphs

MapLibre draws labels with signed-distance-field glyphs, split into ranges of 256 code points
(`{fontstack}/{start}-{end}.pbf`). The app bundles a subset in
[`styles/bundled/map/glyphs/`](../styles/bundled/map/glyphs/), taken from
`protomaps/basemaps-assets` at a pinned commit (files and SHA-256 in
[THIRD_PARTY.md](THIRD_PARTY.md#bundled-map-assets-shipped-in-the-apk)). Fonts: Noto Sans, SIL OFL 1.1.

## What ships now

The fontstacks are the ones the bundled style uses: **Noto Sans Regular, Medium and Italic**.
Each fontstack ships all four of these ranges:

| Range | Covers |
|---|---|
| `0-255` | Basic Latin, Latin-1 |
| `256-511` | Latin Extended-A/B |
| `2304-2559` | Devanagari (Noto Sans Italic has no Devanagari glyphs, so its file is empty upstream) |
| `8192-8447` | General punctuation, currency symbols |

Total size of glyphs, sprites and style: about 1.34 MB (budget 1.5 MB).

## Not yet covered

Arabic, Cyrillic, Greek, CJK, Tamil, Malayalam, Bengali and the other Indic scripts must be added
before the global release, probably shipped inside region packs so each pack carries the scripts
it needs. Until then, labels in a missing range don't render. CJK uses MapLibre's local ideograph
font fallback (`localIdeographFontFamily`), which draws from the device's system fonts.
