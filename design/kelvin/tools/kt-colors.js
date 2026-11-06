// Generates android/kelvin/src/main/kotlin/studio72f/kelvin/KelvinColors.kt from tokens.json.
// Usage (from the repository root): node tools/kt-colors.js $PWD/tokens.json > android/kelvin/src/main/kotlin/studio72f/kelvin/KelvinColors.kt
const T=require(process.argv[2]);const v=(t,th)=>{let x=typeof t.value==="string"?t.value:t.value[th];if(x.startsWith("{"))x=v(m[x.slice(1,-1)],th);return x;};
const m=Object.fromEntries(T.color.tokens.map(t=>[t.name,t]));
const kname=n=>n.replace(/-([a-z0-9])/g,(_,c)=>c.toUpperCase());
const hex=h=>{h=h.replace("#","");h=h.length===8?h.slice(6)+h.slice(0,6):"ff"+h;return "Color(0x"+h.toUpperCase()+")"};
const pick=["canvas","group","raised","sunken","hairline","ink","ink-2","ink-3","accent","on-accent","accent-soft","on-accent-soft","horizon","glass-tint","glass-lit","lift","glass-heat","glass-edge","glass-ink-2","scrim","media","positive","positive-soft","caution","caution-soft","critical","critical-soft","info","info-soft"];
const fields=pick.map(n=>`    val ${kname(n)}: Color,`).join("\n");
const block=th=>pick.map(n=>`    ${kname(n)} = ${hex(v(m[n],th))},`).join("\n");
const presets=["glacier","fjord","fern","dusk","rose","apricot","ruby","sage"].map(k=>`    ${k[0].toUpperCase()+k.slice(1)}(\n        light = AccentFamily(${hex(v(m["dial-"+k],"light"))}, Color(0xFFFFFFFF), ${hex(v(m["dial-"+k+"-soft"],"light"))}, ${hex(v(m["dial-"+k+"-on-soft"],"light"))}),\n        dark = AccentFamily(${hex(v(m["dial-"+k],"dark"))}, Color(0xFFFFFFFF), ${hex(v(m["dial-"+k+"-soft"],"dark"))}, ${hex(v(m["dial-"+k+"-on-soft"],"dark"))}),\n    ),`).join("\n");
const tiles=["blue","orange","green","red","violet","teal","grey"].map(k=>`    val ${k[0].toUpperCase()+k.slice(1)} = ${hex(v(m["tile-"+k],"light"))}`).join("\n");
console.log(`// Generated from tokens.json by tools/kt-colors.js. Edit the tokens, not this file.
package studio72f.kelvin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Every Kelvin colour role for one theme. See tokens.json for where each is used. */
@Immutable
data class KelvinColors(
${fields}
)

val KelvinLight = KelvinColors(
${block("light")}
)

val KelvinDark = KelvinColors(
${block("dark")}
)

/** One accent dial preset for one theme: fill, content on it, soft fill, content on that. */
@Immutable
data class AccentFamily(val accent: Color, val onAccent: Color, val soft: Color, val onSoft: Color)

/** Accent dial presets besides Ember (the default) and Graphite (derived from ink). */
enum class AccentPreset(val light: AccentFamily, val dark: AccentFamily) {
${presets}
}

/** Settings icon tiles: same in both themes, white glyph on top (3:1). */
object KelvinTiles {
${tiles}
}`);
