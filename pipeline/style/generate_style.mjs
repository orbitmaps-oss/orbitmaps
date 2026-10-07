// SPDX-License-Identifier: GPL-3.0-or-later
//
// Generates styles/bundled/map/style-light.json from the pinned @protomaps/basemaps package.
// The style is fully offline: glyphs and sprites point at APK assets (asset://), and the tile
// source is a placeholder that the app replaces with the installed region file
// (pmtiles://file:///...). See pipeline/README.md.
//
// Usage (from the repository root):
//   npm ci --ignore-scripts --prefix pipeline/style
//   node pipeline/style/generate_style.mjs

import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { layers, namedFlavor } from "@protomaps/basemaps";

const SOURCE = "protomaps";
const FLAVOR = "light";
const LANG = "en";

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "..", "..", "styles", "bundled", "map", "style-light.json");

const style = {
  version: 8,
  name: "Orbit Maps Day (Protomaps light)",
  glyphs: "asset://map/glyphs/{fontstack}/{range}.pbf",
  sprite: "asset://map/sprites/light",
  sources: {
    [SOURCE]: {
      type: "vector",
      url: "pmtiles://file:///REGION_NOT_INSTALLED",
      attribution: "© OpenStreetMap contributors",
    },
  },
  layers: layers(SOURCE, namedFlavor(FLAVOR), { lang: LANG }),
};

const text = JSON.stringify(style, null, 2);
if (/https?:\/\//.test(text)) {
  throw new Error("generated style contains an http(s) URL; the bundled style must be offline");
}
writeFileSync(out, `${text}\n`, "utf8");
console.log(`wrote ${out} (${style.layers.length} layers)`);