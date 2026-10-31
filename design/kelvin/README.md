# Kelvin

The design system for 72F apps (72F Gallery, HyperMind AI and what comes next). It sits in
the HyperOS / ColorOS / OriginOS school (grey canvas, white rounded groups, big titles,
full-colour settings tiles, everything rising from the bottom) and adds shader-driven glass
and soft controls. The glass is Kelvin's own material: ground glass with a fine grain, lit
from its bottom edge at the accent's colour temperature, bevelled outward at the edge, and
thawed clear under a warm finger. People choose how much glass they see with one setting,
on one stepless slider from near-opaque ground glass to fully transparent Clear, with
Frosted (0.3) as the accessible default.

Kelvin's components, shapes and physics are 72F's own. Liquid glass on Android is rendered by
[Vitro](https://github.com/itshedimisawi/vitro) (itshedimisawi, Apache 2.0), Liquid Glass
calibrated side by side against iOS 26: rim refraction, highlights, blur, light and dark
materials and touch response. Frost, the veil and glass in sheets and dialogs run on
[Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (Kyant, Apache 2.0), with Kelvin's own
frost shader. No Material components and no third-party UI kit.

## What is here

| Path | What |
| --- | --- |
| [GUIDELINES.md](GUIDELINES.md) | The brand book: glass, liquid controls, colour, type, layout, motion, icons, copy |
| [tokens.json](tokens.json) | Source of truth for colours (light/dark), glass levels, type, spacing, radii, shadows |
| [android/](android) | Gradle project: `:kelvin` (the Compose library) and `:catalog` (a demo app with every component) |
| [web/](web) | `tokens.css`, `kelvin.css` and `kelvin.js` for the website and store pages |
| [tools/kt-colors.js](tools/kt-colors.js) | Regenerates `KelvinColors.kt` from `tokens.json` |
| [licenses/](licenses) | Vitro and Backdrop (Apache 2.0), Onest (SIL OFL 1.1) and Tabler Icons (MIT) notices |

## The glass engine (Android)

- `Modifier.kelvinBackdrop(backdrop)` marks the page content: Backdrop's `LayerBackdrop`
  always, and Vitro's `glassBackdrop` too while the texture is Liquid.
- `Modifier.glass(backdrop, shape, content, elevated, motion, pebble)` draws a pane. Liquid
  glass goes through Vitro's `glassEffect`: Kelvin picks the light or dark material from its
  own theme, sets the rim's bend from the Glass setting (`refraction`) and the blur, veil and
  coat from Transparency (the Glass level), stains coloured controls in their vivid colour and
  keeps its shadows. Vitro approximates the shader before Android 13. Frost runs on Backdrop's
  `drawBackdrop` with Kelvin's `FROST_AGSL`. Sheets, dialogs and menus open in a window of their
  own, which Vitro cannot see out of, so there Liquid glass falls back to Backdrop: a deep blur
  under the pane's coat. `thaw` no longer clears anything.
- Pebbles (buttons): pass `pebble = press` (a `GlassPress`) to `glass`, or use
  `Modifier.pebble(shape, color, press)` for a solid one plus `Modifier.pebbleMotion(press)`.
  Liquid glass pebbles are interactive Vitro glass: they swell, glow and stretch under the
  finger as iOS glass does. Given a backdrop, every filled button is glass: `KelvinButton`
  (Primary, Tinted, Secondary, Danger), `KelvinIconButton` (Raised, Accent), `KelvinActionOrb`
  and the chosen `kelvinPill`. Vivid fills are stained glass: what shows through is darkened and
  the stain pushed past Vitro's 85% tint cap, so it lands on the fill over a light page and
  deepens over dark content. Toward Clear the stain thins but is pushed harder, and what shows
  through is saturated further, so a see-through CTA stays as rich as the fill. Soft and grey
  fills are glass in their own tone. Without a backdrop
  they stay solid pebbles. Solid and Frost pebbles keep Kelvin's own finish: a soft own-colour
  glow beneath vivid fills (a neutral shadow under pale ones; none under switch and slider
  knobs), a 4dp swell and tanh pull on press, and a light bloom under the finger. `KelvinButton` (all kinds but Text), `KelvinIconButton` and `KelvinActionOrb`
  are pebbles.
- Other floating glass (the dock, search capsule, chips, pills, menus, sheets) carries Vitro's rim
  highlight like the buttons; edge-to-edge bars and the dock's lens do not.
- `motion` moves the pane (a menu growing from its opener).
- Two settings: **Glass** (`KelvinTheme(refraction)`, `RefractionLevel`, 0 Subtle to 1 Strong)
  is how hard Liquid glass's rim bends the page (an 8dp rim bending 16dp up to a 24dp rim bending
  64dp; Vitro's own calibration sits a little under the 0.5 default). **Transparency**
  (`KelvinTheme(glass)`, the Glass level, stepless 0 near-opaque to 1 clear) resolves into the
  coat, blur, veil, contrast floor and a `deform` factor for controls; Frost has its own
  transparency and no refraction. High-contrast text forces an opaque pane, battery saver caps
  Transparency at Frosted, reduced motion turns deformation off.

## Navigation

`KelvinDock` is a floating glass capsule with a droplet selection that slides between tabs and
stretches with speed; dragging lifts it into a clear glass pane over the icons, following the
finger on a stiff spring. `KelvinActionOrb` sits beside it for the screen's creation action.
(A thermometer nav bar was explored and parked; see the history.)

## Libraries and licences

| Dependency | Licence | Use |
| --- | --- | --- |
| AndroidX Compose foundation, UI, activity, core | Apache 2.0 | Platform toolkit |
| Vitro (`io.github.itshedimisawi:vitro`) | Apache 2.0 | Liquid glass rendering |
| Backdrop (`io.github.kyant0:backdrop`) and its Shapes dependency | Apache 2.0 | Frost, the veil and glass in overlay windows |
| Onest font | SIL Open Font License 1.1 | Bundled typeface |
| Tabler Icons paths | MIT | Icons (paths unmodified) |

All are free for commercial use. Ship `licenses/` in each app's open-source notices screen.

## Building

Every push to `main` builds the catalog on GitHub Actions; download `kelvin-catalog-debug` from the run's artifacts to install it. Locally:

```
cd android
./gradlew :kelvin:assembleRelease :catalog:assembleDebug
```

Verified here: both modules compile against Backdrop 2.0.1 and Vitro 0.1.0 and the catalog APK builds (compileSdk 37, AGP 9.4.1,
Kotlin 2.4.20, Compose BOM 2026.09.00, matching HyperMind). The AGSL shader was compiled and
rendered with Skia's SkSL compiler. Not yet verified: running on a device. The catalog is
the place to tune the shader and springs on real hardware before apps adopt it.

## Where the ideas came from

| Idea | Source | Kelvin's version |
| --- | --- | --- |
| Grey canvas, white groups, raised circle buttons, text-tab filters | HyperOS (via HyperMind) | Own components, cooler canvas, true-black dark |
| Bottom navigation with a selection pill | 72F Gallery, ColorOS 16, HyperOS 3 | A thermometer: the selected tab is a bead of accent liquid in a floating glass tube |
| Notched album covers | 72F Gallery | System-wide tile signature with a glass badge |
| Settings tiles, bottom dialogs, thick sliders, light hero numerals | HyperOS, ColorOS, OriginOS | Fixed topic-to-colour map across 72F apps |
| Glass and shader-driven controls | The industry's move to liquid glass | The principle only: ground, horizon-lit, bevelled, thawing glass with soft controls, a user-chosen level up to fully Clear, and a contrast floor |
| Bold unread rows with avatars | Yandex Mail mobile | Initials on accent-dial colours, signal dot |
| Ember accent | The 72F icon's sun | Each app has its own default on the dial (HyperMind Apricot, 72F Gallery Glacier, AppGone Ruby, Rediom Sage), nine presets plus monochrome |

## Adopting Kelvin in an app

1. Include the library from this repository, for example as a Git submodule at
   `kelvin/` with `include(":kelvin")` and
   `project(":kelvin").projectDir = file("kelvin/android/kelvin")`.
2. Wrap the root in `KelvinTheme(dark, accent, glass, reduceMotion)`; store `glass` and
   `reduceMotion` in Appearance settings with `KelvinGlassControl` and `KelvinSwitch`.
3. Put `Modifier.kelvinBackdrop(page)` on each screen's scrolling content and place
   `KelvinDock`, `KelvinCompactBar`, sheets and dialogs over it.
4. Move screens across one at a time, settings first. HyperMind drops Miuix as screens move;
   72F Gallery drops Material 3 the same way.
