// Generates android/kelvin-views/src/main/res/values/kelvin_colors.xml from tokens.json: Kelvin's colours as Android
// colour resources, for screens still built from Views. Same tokens as KelvinColors.kt (tools/kt-colors.js).
// Usage (from the repository root):
//   node tools/xml-colors.js $PWD/tokens.json > android/kelvin-views/src/main/res/values/kelvin_colors.xml
//
// Every role comes twice, kelvin_light_<role> and kelvin_dark_<role>. Coloured text on a soft fill (on-accent-soft, a
// dial's on-soft, status ink) also comes as <role>_text: the same hue moved in OKLab lightness until it meets 4.5:1 on
// its fill, which is what legibleOn() does at runtime in Compose. Views cannot run it, so it is baked in here.
const T = require(process.argv[2]);
const m = Object.fromEntries(T.color.tokens.map(t => [t.name, t]));
const v = (t, th) => { let x = typeof t.value === "string" ? t.value : t.value[th]; if (x.startsWith("{")) x = v(m[x.slice(1, -1)], th); return x; };
const res = n => n.replace(/-/g, "_");
const argb = h => { h = h.replace("#", "").toUpperCase(); return "#" + (h.length === 8 ? h.slice(6) + h.slice(0, 6) : h); };

// sRGB <-> OKLab, and WCAG contrast.
const lin = c => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const gam = c => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);
const rgb = h => [0, 2, 4].map(i => parseInt(h.replace("#", "").slice(i, i + 2), 16) / 255);
const hex = c => "#" + c.map(x => Math.round(Math.min(1, Math.max(0, x)) * 255).toString(16).padStart(2, "0")).join("").toUpperCase();
const toLab = h => {
  const [r, g, b] = rgb(h).map(lin);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const mm = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [0.2104542553 * l + 0.793617785 * mm - 0.0040720468 * s, 1.9779984951 * l - 2.428592205 * mm + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * mm - 0.808675766 * s];
};
const fromLab = ([L, a, b]) => {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3, mm = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3, s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return hex([4.0767416621 * l - 3.3077115913 * mm + 0.2309699292 * s, -1.2684380046 * l + 2.6097574011 * mm - 0.3413193965 * s, -0.0041960863 * l - 0.7034186147 * mm + 1.707614701 * s].map(gam));
};
const lum = h => { const [r, g, b] = rgb(h).map(lin); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
/** The colour's hue and chroma, moved only in lightness (away from the ground) until it meets [ratio]. */
const legibleOn = (fg, ground, ratio = 4.5) => {
  if (contrast(fg, ground) >= ratio) return fg;
  const lab = toLab(fg), darker = lum(ground) > 0.18;
  for (let i = 1; i <= 100; i++) {
    const L = darker ? lab[0] - i * 0.01 : lab[0] + i * 0.01;
    const c = fromLab([L, lab[1], lab[2]]);
    if (contrast(c, ground) >= ratio) return c;
  }
  return darker ? "#000000" : "#FFFFFF";
};

const roles = ["canvas", "group", "raised", "sunken", "hairline", "ink", "ink-2", "ink-3", "accent", "on-accent", "accent-soft", "on-accent-soft", "glass-tint", "glass-lit", "lift", "glass-heat", "glass-ink-2", "scrim", "positive", "positive-soft", "caution", "caution-soft", "critical", "critical-soft", "info", "info-soft"];
const dials = ["glacier", "fjord", "fern", "dusk", "rose", "apricot", "ruby", "sage"];
const tiles = ["blue", "orange", "green", "red", "violet", "teal", "grey"];
const lines = [];
const color = (name, value, note) => lines.push(`    <color name="${name}">${argb(value)}</color>${note ? `  <!-- ${note} -->` : ""}`);
for (const th of ["light", "dark"]) {
  lines.push("", `    <!-- ${th === "light" ? "Light: paper-white canvas, pale grey wells" : "Dark: true black canvas, near-black wells"} -->`);
  for (const r of roles) color(`kelvin_${th}_${res(r)}`, v(m[r], th));
  // Neutral glyphs, as KIcon draws them: ink lightened toward the page, as far as still holds 3:1 on the group grey.
  { const ink = toLab(v(m["ink"], th)), cv = toLab(v(m["canvas"], th)); const mix = fromLab(ink.map((x, i) => x + (cv[i] - x) * 0.6));
    color(`kelvin_${th}_icon`, legibleOn(mix, v(m["group"], th), 3), "neutral glyphs: light, not loud (3:1 on group)"); }
  color(`kelvin_${th}_on_accent_soft_text`, legibleOn(v(m["on-accent-soft"], th), v(m["accent-soft"], th)), "on-accent-soft, 4.5:1 on accent-soft");
  for (const s of ["positive", "caution", "critical", "info"]) color(`kelvin_${th}_${s}_text`, legibleOn(v(m[s], th), v(m[s + "-soft"], th)), `${s} ink, 4.5:1 on ${s}-soft`);
  lines.push(`    <!-- ${th}: the accent dial. Swap the whole family together. -->`);
  for (const d of dials) {
    color(`kelvin_${th}_${d}`, v(m["dial-" + d], th));
    color(`kelvin_${th}_${d}_soft`, v(m["dial-" + d + "-soft"], th));
    color(`kelvin_${th}_${d}_on_soft`, v(m["dial-" + d + "-on-soft"], th));
    color(`kelvin_${th}_${d}_on_soft_text`, legibleOn(v(m["dial-" + d + "-on-soft"], th), v(m["dial-" + d + "-soft"], th)), "4.5:1 on its soft fill");
  }
}
lines.push("", "    <!-- Settings tiles: the same in both themes, a white glyph on top -->");
for (const t of tiles) color(`kelvin_tile_${t}`, v(m["tile-" + t], "light"));

console.log(`<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from tokens.json by tools/xml-colors.js. Edit the tokens, not this file. -->
<resources>${lines.join("\n")}
</resources>`);
