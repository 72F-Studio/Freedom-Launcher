# Kelvin

The design system for 72F apps (72F Gallery, HyperMind AI and what comes next). It sits in
the HyperOS / ColorOS / OriginOS school (grey canvas, white rounded groups, big titles,
full-colour settings tiles, everything rising from the bottom) and adds shader-driven glass
and soft controls. The glass is Kelvin's own material: ground glass with a fine grain, lit
from its bottom edge at the accent's colour temperature, bevelled outward at the edge, and
thawed clear under a warm finger. People choose how much glass they see with one setting,
on one stepless slider from near-opaque ground glass to fully transparent Clear, with
Frosted (0.3) as the accessible default.

Kelvin's components, shapes, shaders and physics are 72F's own. Glass is rendered through
[Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (Kyant, Apache 2.0), a maintained
liquid-glass engine: it records the backdrop with padding, keeps it aligned while glass moves
or deforms, and chains effects. Kelvin plugs its own thermal shader, rim light and press
physics into that pipeline. No Material components and no third-party UI kit.

## What is here

| Path | What |
| --- | --- |
| [GUIDELINES.md](GUIDELINES.md) | The brand book: glass, liquid controls, colour, type, layout, motion, icons, copy |
| [tokens.json](tokens.json) | Source of truth for colours (light/dark), glass levels, type, spacing, radii, shadows |
| [android/](android) | Gradle project: `:kelvin` (the Compose library) and `:catalog` (a demo app with every component) |
| [web/](web) | `tokens.css`, `kelvin.css` and `kelvin.js` for the website and store pages |
| [tools/kt-colors.js](tools/kt-colors.js) | Regenerates `KelvinColors.kt` from `tokens.json` |
| [licenses/](licenses) | Onest (SIL OFL 1.1) and Tabler Icons (MIT) notices |

## The glass engine (Android)

- `Modifier.kelvinBackdrop(backdrop)` marks the page content (Backdrop's `LayerBackdrop`).
- `Modifier.glass(backdrop, shape, thaw, elevated, motion)` draws a pane with Backdrop's
  `drawBackdrop` and Kelvin's optic shader (`OPTIC_AGSL` in `Glass.kt`). The pane is modelled
  as real glass, and every light effect comes from that model:
  - Snell's-law refraction at a curved rim (flat top, quarter-circle bevel), carried through
    the pane's thickness, so the rim bends the backdrop and the middle stays true;
  - dispersion: red, green and blue refract with slightly different indices;
  - Schlick Fresnel: the rim reflects the scene beyond it at grazing angles;
  - ground-glass frost (fixed random scatter), the thaw under a finger, the text contrast
    floor, and light carried in through the bottom edge and scattered by the frost.
  No highlights, rims or glows are painted on.
- Pebbles (buttons): pass `pebble = press` (a `GlassPress`) to `glass`, or use
  `Modifier.pebble(shape, color, press)` for a solid one plus `Modifier.pebbleMotion(press)`.
  Modelled on Kyant's AndroidLiquidGlass buttons: Backdrop's rim highlight (gentler in dark
  mode), a soft own-colour glow beneath vivid fills (a neutral shadow under pale ones; none under
  switch and slider knobs), vivid fills drawn like the stained orb, a 4dp swell and tanh pull on press, and a light bloom
  under the finger. `KelvinButton` (all kinds but Text), `KelvinIconButton` and `KelvinActionOrb`
  are pebbles.
- Other floating glass (the dock, search capsule, chips, pills, menus, sheets) carries the same rim
  highlight as the buttons; edge-to-edge bars and the dock's lens do not.
- `motion` takes a `GlassPress` layer block: the pane squashes and leans toward the finger
  while Backdrop keeps the backdrop aligned behind it.
- The Glass level (stepless, 0 near-opaque to 1 clear) resolves into coating, frost, thickness, bevel, index of refraction,
  dispersion, contrast floor and a `deform` factor for controls. Clear (1.0) has no coating,
  no frost, the thickest pane and no floor. High-contrast text forces an opaque pane, battery saver caps
  at Frosted, reduced motion turns deformation off.
- Android 12 gets blur and tint; Android 11 and below, and high-contrast text, get an opaque pane.

## Navigation

`KelvinDock` is a floating glass capsule with a droplet selection that slides between tabs and
stretches with speed; dragging lifts it into a clear glass pane over the icons, following the
finger on a stiff spring. `KelvinActionOrb` sits beside it for the screen's creation action.
(A thermometer nav bar was explored and parked; see the history.)

## Libraries and licences

| Dependency | Licence | Use |
| --- | --- | --- |
| AndroidX Compose foundation, UI, activity, core | Apache 2.0 | Platform toolkit |
| Backdrop (`io.github.kyant0:backdrop`) and its Shapes dependency | Apache 2.0 | Glass rendering pipeline |
| Onest font | SIL Open Font License 1.1 | Bundled typeface |
| Tabler Icons paths | MIT | Icons (paths unmodified) |

All are free for commercial use. Ship `licenses/` in each app's open-source notices screen.

## Building

Every push to `main` builds the catalog on GitHub Actions; download `kelvin-catalog-debug` from the run's artifacts to install it. Locally:

```
cd android
./gradlew :kelvin:assembleRelease :catalog:assembleDebug
```

Verified here: both modules compile against Backdrop 2.0.1 and the catalog APK builds (compileSdk 37, AGP 9.4.1,
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
