# Kelvin guidelines

Kelvin is the design system for every app under the 72F name. It belongs to the school of HyperOS, ColorOS and OriginOS (a light grey canvas, white rounded groups, big left-aligned titles, full-colour settings tiles, everything rising from the bottom) and adds what is next: shader-driven glass and controls that stretch, heat up and settle like soft matter. Kelvin's glass is its own material, not a copy of anyone's: ground glass with a fine grain, lit from its bottom edge at the accent's colour temperature, with a bevel that bends the edge outward and frost that thaws under a warm finger. How much glass a person sees is their choice, on one stepless slider from near-opaque to fully Clear; the default is the accessible one. Two signatures are 72F's alone: the **Horizon** glow behind top-level titles and the **notch** cut into media tiles around their badge. Kelvin's components, shapes, shaders and physics are 72F's own; glass is rendered through Backdrop, an open-source (Apache 2.0) liquid glass engine.

## Principles

1. **Grey canvas, white groups, glass on top.** Content lives in solid `group` cards on `canvas`. Glass is only for chrome over content: the dock, the compact title bar, sheets, dialogs, toasts and badges.
2. **One signal colour.** `accent` marks the one thing to act on or the current state. Everything else is `ink`, greys and glass.
3. **Controls are matter.** A pressed glass button squashes and leans toward your finger, a switch knob heats up and pulls like taffy, the dock's droplet follows your finger and stretches with speed. Everything springs back and can be caught mid-flight.
4. **The person sets the glass.** Glass, stretch and wobble scale with one setting, on one stepless slider from near-opaque to fully Clear. Frosted is the default; the system's accessibility settings always win.
5. **Thumb first.** Navigation floats at the bottom in a glass dock with the action orb beside it. Dialogs and sheets rise from the bottom.
6. **Say it plainly.** Every state says what is happening and what to do next.

## Glass

Kelvin glass is modelled, not painted. Everything light does on it comes from geometry and optics in the shader; Kelvin draws no highlights, rims, glints or glows.

- **A real pane.** Each glass element is a pane with a thickness and a rounded rim (flat on top, curving down to the edge as a quarter circle). The view through it is refracted by Snell's law at the curved surface and carried through the thickness, so the rim bends the backdrop inward the way a thick glass edge does, and the flat middle stays true.
- **Dispersion.** Red, green and blue refract with slightly different indices, so strong bends split colour at the rim, as real glass does.
- **Fresnel.** At grazing angles the rim reflects the scene just beyond it (Schlick's approximation). That reflection is the only edge highlight.
- **Frost.** Ground glass diffuses the backdrop into a smooth, saturated blur (colour survives, as on HyperOS and ColorOS) with only a faint fixed grain that stays on the pane while content moves behind it. Toward the opaque end, light glass over dark content stays milky white rather than turning grey. A finger's warmth thaws it (`thaw-radius`), and it fogs back over after release.
- **Carried light.** Light entering through the bottom edge travels up the pane and is scattered out by the frost, tinted `glass-heat` (the accent's colour temperature). It fades as the glass clears, because clear glass does not scatter.
- **The Glass setting** (Settings > Appearance > Glass) is one stepless slider from 0 to 1, labelled Opaque at the left end and Clear at the right; nothing snaps. The left end is near-opaque ground glass (a 94% coat over heavy frost), never a flat pane. Toward Clear the coat thins to nothing, the frost clears, and the pane gets thicker with a wider rim, so refraction grows to its extreme. `glass-frosted` (0.3) is the default; `glass-glass` and `glass-liquid` are reference points on the range, not stops. Coloured controls keep their stain to the end (84% in the middle at Clear), so a CTA still reads as one.
- **Clear** is fully transparent: no coating, no frost, the thickest pane and no contrast floor. Show the warning "Clear glass has no contrast protection. Text over photos may be hard to read." beside it.
- **Contrast floor.** Up to Liquid, glass that carries text never lets the backdrop take text below 4.5:1: on light glass the shader lifts anything darker than `glass-floor-light` toward `glass-tint`; on dark glass it pulls anything brighter than `glass-floor-dark` down. Text on glass is `ink`; secondary text is `glass-ink-2`, never `ink-2`.
- **Accessibility always wins.** Android's high-contrast text and the web's "increase contrast" and "reduce transparency" force an opaque pane. Battery saver caps glass at Frosted. Reduced motion stops all deformation.
- **The veil.** Every title bar sits on the veil, top-level or not: the large title with its search and filters, a detail screen's back-and-title bar, the compact bar that fades in on scroll. The page runs the full height of the screen beneath it (`KelvinVeiledScreen`, with the page padding its scrolling top by `LocalVeilInset`). Top chrome that spans the screen is never a pane. It sits on a veil: the page blurs and washes into `canvas` toward the status bar and fades to nothing a `space-8` below the chrome, with no edge. The pane with a refracting rim is for floating chrome only. Text set straight on the veil (the large title) follows what shows through it: `ink` over a light page, white once dark content scrolls under a thin veil, crossing with a short colour fade.
- **Stained glass.** Coloured controls over media (a primary button on a photo) are glass with the colour in its body. Its opacity follows Beer-Lambert absorption along the path through the pane: dense in the thick middle, so `on-accent` keeps its contrast, thin at the rim, where the page refracts through. The stain is the vivid version of the colour (OKLab lightness lifted, chroma pushed to the edge of sRGB), as bright as ColorOS and vivo fills, and the light that passes through it lands on the page as a soft shadow in the same colour. Coloured controls inside groups stay solid; there is nothing behind them to bend.
- No glass on glass: a sheet over the dock hides the dock.
- Platform fallbacks: Android 13+ runs the full optic shader; Android 12 gets blur and tint; older Android and high-contrast text get an opaque pane with a hairline edge. On the web, Chromium refracts the rim through an SVG displacement filter; Safari and Firefox get blur and tint. Thaw is Android-only.

- **What is glass.** Glass is for chrome that floats over content: the dock, header controls on the veil (circle buttons, the search capsule, filter pills, resting ones lit glass and the chosen one accent), menus that grow out of their opener, the selection action bar, controls and captions over photos, the AI composer and its suggestions. Cards, rows, stat tiles and buttons inside groups stay solid; there is nothing behind them to bend.
- **Windows.** Sheets and dialogs that live in their own window cannot see the page, so the system blurs everything behind the window (Android 12+, cross-window blur on) by the Glass level's frost, and the panel is `glass-tint` at the level's coat, never thinner than half.
- **Never glass on its own page.** A page that glass refracts must not contain glass that refracts it; content recorded into a page backdrop sees no page.

## Soft controls

- Every control's deformation scales with the Glass level: light at Frosted, full at Liquid and Clear, none with reduced motion.
- **Pebbles.** Buttons are liquid glass, after Kyant's AndroidLiquidGlass: a flat face true to its colour, a fine rim light brightest on the two corners facing the 45-degree light and away from it, a faint shine and inner glow along the top, a soft inner shadow along the bottom, and beneath it a glow in the button's own colour if it is vivid, or a faint neutral shadow if it is pale or grey (a pale glow reads as haze on a coloured card). A vivid fill is drawn in its vivid version, and the action orb beside the dock is the same solid accent pebble as the primary button, so a call to action never differs from it in colour. Nothing reflects on the face. In dark mode the finish is gentler still on grey buttons, whose top glow otherwise reads as a lit edge. Pressed, the button swells by about 4dp, is pulled toward the finger and stretches along the pull, and light blooms where it is touched (less on pale fills, where light would wash them white). Glass buttons also bend the page behind them through a steep, tucked rim. In dark mode the rim light is gentler. Dialogs, sheets, menus and toasts are liquid glass panes, capped at the Glass mark (0.55): past it the slider clears bars and controls further but never the panes that carry reading text. They show the page through a heavy frost and a milky coat of at least 86% (no contrast floor; the pane is large and evenly coated), so the dock or a bright photo behind never shows through as a smear, bending at a thick rim with a touch of dispersion, with the same rim light and a soft shadow. The dock, the search capsule, chips, pills, menus and badges carry the same rim light as glass buttons, so every floating pane reads as one material; only edge-to-edge bars and the dock's clear lens go without. Switch and slider knobs are white pebbles with a soft drop shadow and no glow. Text buttons stay flat.
- **No outlines.** No drawn hairline goes round glass, chips or badges. The only edge light is the liquid-glass rim light, brightest on the two corners facing toward and away from the light, the same on every floating pane; other edges come from refraction, shadow and fill contrast.
- **Press**: buttons swell under the finger (see Pebbles); other elements squash like warm wax (a little wider and lower) while held and spring back with a small wobble. Glass buttons also lean toward the finger as it moves, the pull easing off so they never leave their place, and thaw where they are touched; the backdrop stays aligned behind them.
- **Switch**: held or dragged, the knob lifts, widens toward where it is going, and pulls like taffy past either end before springing home. A flick picks the side it was thrown toward, however short.
- **Slider**: while dragged the knob lifts, swells and stretches with speed. Past either end the whole slider stretches toward the finger and snaps back on release.
- **Dock**: the selection is a solid accent droplet that slides between tabs and stretches with its speed. Dragging along the dock lifts it into a clear glass pane over the icons, refracting them, following the finger on a stiff spring and landing on the tab underneath.
- **Sheets and dialogs** follow the finger down and stretch when pulled up past their height. A flick down dismisses them however short it was.
- **The rubber band.** Anything pulled past its end (a switch knob, a slider, a sheet pulled up) follows one curve, `c·x·d / (d + c·x)` with c = 0.55 (`KelvinMotion.rubberBand`): about half the finger's travel at first, less and less after, never more than `d`.
- **Release carries speed.** When the finger lets go, the spring starts at the finger's speed, never from rest, so nothing stops dead for a frame on release.

## Colour

- Build screens from `canvas` → `group` → `raised`. Text is `ink`; values, summaries and timestamps are `ink-2`; placeholders and disabled glyphs are `ink-3`.
- Light canvas is a cool light grey; dark canvas is true black with near-black groups.
- Use `accent` for the primary button, switches and radios when on, the selected dock tab, links and text buttons, progress, the focus ring and the "72F" half of a wordmark. Put `on-accent` on accent fills. `accent-soft` with `on-accent-soft` is for tinted buttons, the chosen row in a picker, and accent notices.
- **Bright in both themes.** Accent and status colours are as vivid as ColorOS and vivo fills: in light mode they sit around 2.2–2.6:1 on the canvas, a deliberate small step under WCAG's 3:1 for UI components, traded for colour; in dark they clear 3:1 comfortably. They are never the only carrier of meaning (status always has an icon or word) and never carry body text. Use them for fills, icons, controls and semibold labels; running text stays `ink` and `ink-2` at 4.5:1 or more. Warm hues are never darkened toward brown to gain contrast.
- **Filters and tabs: resting pills look like the buttons, the chosen one is the accent.** Every resting filter pill or tab is the same material as the buttons around it, with `ink` text at 500; the chosen one is a solid `accent` pebble with `on-accent` text at 600, the same fill as the primary button, so a filter, the dock and a call to action never disagree on the colour:
  - over content (header filters, tabs on the veil), resting pills are lit glass: a glass pebble coated in `glass-lit`, which in light mode is the glass buttons' own white glass and in dark mode a step lighter than the glass;
  - on the page, resting pills are `lift` pills (white on `shadow-raise` in light mode, a step lighter than `sunken` in dark); inside a group card in light mode, where white would vanish, they are a well of `canvas`;
  - a segmented control (two to four views of the same content) is a grey track whose thumb is the same `accent` pebble;
  - in the dock (the bottom navigation) the selection is a solid `accent` droplet with the icon and label in `on-accent`, so the current tab stays plain at every Glass level up to Clear; it lifts into a clear lens while dragged.
  Glass is never put inside glass to mark a selection.
- **Settings tiles** are full-colour 32px squircles with a white glyph: `tile-blue`, `tile-orange`, `tile-green`, `tile-red`, `tile-violet`, `tile-teal`, `tile-grey`, chosen by topic (each token lists its topics), the same topic on the same colour in every 72F app.
- **Accent dial**: Ember (`accent`), Glacier, Fjord, Fern, Dusk, Rose, Apricot, Ruby, Sage (`dial-*` with `-soft` and `-on-soft`) and Graphite (monochrome). Swap the whole family together.
- **One accent per app.** Every 72F app shares the system and the dial, but each has its own default accent, chosen for what the app is, so the apps are told apart at a glance: HyperMind is Apricot, 72F Gallery is Glacier (the blue of its icon), AppGone is Ruby and Rediom is Sage (a calm reading green); each new app picks the dial colour that fits it. Each app offers the whole dial in Appearance, its own colour first; the wordmark's brand half and the app icon follow the default. A "Match wallpaper" option may take the wallpaper's hue and rebuild the family at the same lightness; neutrals never change.
- Status colours `positive`, `caution`, `critical`, `info` (with `-soft`) only mark status, always with an icon or a word. Destructive actions are `critical` crimson, never orange `accent`.
- **The Horizon**: a radial wash of `horizon` behind the large title on top-level screens, fading into `canvas` before the first group.
- `ink`, `ink-2` and `glass-ink-2` meet 4.5:1 on the grounds their notes name, in both themes; accent and status colours trade a little contrast for vividness in light mode (see Bright in both themes). The focus ring is a 2px `accent` outline offset 2px.

## Typography

- Set everything in **Onest** (SIL Open Font License 1.1), bundled at 300–700, falling back to the system sans.
- Scale: `display` 34/40 bold for the large title; `headline` 26/32 for detail and sheet titles; `title` 20/26 for section headers; `row-title` 16/22 medium; `label` 14/20 semibold; `body` 15/22; `summary` 13/18 in `ink-2`; `caption` 12/16; `micro` 11/14 for dock labels; `numeral` 34/40 for stat figures; `hero` 64/64 light for one big value per screen.
- Group headers are `summary` at 600 in `ink-2`, sentence case. Unread items set their title at 600 and show the signal dot.
- Sentence case everywhere; tabular numerals where digits line up.

## Layout, shape and depth

- 4dp grid: `space-1` 4 · `space-2` 8 · `space-3` 12 · `space-4` 16 · `space-5` 20 · `space-6` 24 · `space-8` 32. Gutter `space-4`; gap between groups `space-6`; rows at least `space-14`; touch targets at least `space-12`.
- **Top-level screen**: status bar → large title with up to two circle actions → search capsule → tabs → groups → the floating glass dock with the selection droplet, with the accent orb beside it for the screen's creation action. **Detail screen**: circle back button, then the `headline` title, on the veil; on scroll the compact bar fades in on the veil with the title centred.
- Continuous (G2) corners on every rounded surface, Kelvin's own curve with 60% smoothing: `radius-xs` 8 (icon tiles), `radius-sm` 12, `radius-md` 16 (fields, notices), `radius-lg` 24 (groups), `radius-tile` 22, `radius-xl` 32 (sheets, dialogs), `radius-full` (buttons, chips, the dock, the orb).
- **The notch**: a media tile may carry one round glass badge in its bottom-end corner, with the cover cut away around it by a `space-1` moat.
- **Groups are stacks of cards.** Each item in a group (a row, a control, a picker) is its own `group` card; cards are 2dp apart so the canvas shows through, the group's outer corners are `radius-lg` (24) and the corners where cards meet are 6. The gap does a divider's job, so no hairlines inside groups. `KelvinGroup` does this for whatever it holds.
- **Carousels** (a sideways row of chips, pills or swatches) never cut their contents off. They run to the edge of their card or the screen with their padding inside the scroll, fade out at whichever end has more to scroll to (`Modifier.carouselFade`), and leave room above and below for a chosen pill's glow (`Modifier.glowRoom`).
- **No Material, Miuix or other kits on screen.** Every control, sheet, dialog, chip, slider, switch, progress indicator, divider and snackbar is the Kelvin one (`KelvinSlider`, `KelvinSwitch`, `KelvinSpinner`, `KelvinDivider`, `KelvinToast`...). Apps may keep Material's theme plumbing underneath for libraries that read it, never its components.
- **Tips** (`KelvinTip`): a one-time pointer to something on the screen, shown once and dismissed for good. A card inset like the dock and always clear of it, the topic's icon on an `accent-soft` disc, a semibold title, a sentence or two in `ink-2`, and a tinted "Got it" at the end. Glass over content, else `raised` on a soft shadow. One tip per screen at a time; never a full-width slab, never under the dock.
- Groups never cast shadows. Raised circles use `shadow-raise`; the dock, orb and toasts `shadow-float`; sheets and dialogs `shadow-sheet`.

## Motion

| Spring | Damping | Stiffness | Use |
| --- | --- | --- | --- |
| snappy | 0.82 | 700 | Press, lift, switch knob |
| spatial | 0.76 | 420 | Things that move: the droplet, sheets, pages, tile to viewer |
| gentle | 0.90 | 260 | Things that change: size, colour, the compact bar |
| wobble | 0.55 | 400 | A liquid control settling after release |

- Animate with springs; the only timed animation is a 120ms fade for content swapping in place.
- Scroll containers overscroll with an elastic stretch. Opening a tile grows it into the viewer from where it sits. Predictive back scales the page to 0.92 and rounds it to `radius-xl`.
- Reduced motion replaces every spring with a snap and turns off deformation.

## Iconography

- **Tabler Icons** outline (MIT), stroke 1.5 on the 24 grid: 24 in the dock and circles, 20 white in settings tiles, 22 in rows, 18 in buttons. Icons take the colour of the text beside them; no emoji.
- The **signal dot** is an 8px `accent` circle for unread, new or updated.

## Voice and copy

- Write from the person's side: "Your photos are backed up", not "Sync job completed". Buttons are verbs: "Back up now", "Delete 3 photos". Dialogs pair "Cancel" with the exact action.
- Every state names what is happening and the next step. Errors say what went wrong and how to fix it, without apology.
- Sentence case, no exclamation marks, no emoji. Numbers carry units: "2.4 GB", "18:42".

## Building with Kelvin

- **Android**: the `studio72f.kelvin` Compose library, on AndroidX Compose foundation and UI plus Backdrop (Kyant) as the glass renderer, all Apache 2.0. Kelvin's look comes from its own shaders and components running on Backdrop's pipeline; no Material and no third-party UI kit. Wrap the app in `KelvinTheme(dark, accent, glass, reduceMotion)`; read tokens from `Kelvin.colors`, `Kelvin.space`, `Kelvin.shape`, `Kelvin.type`, `Kelvin.motion`. Put `Modifier.kelvinBackdrop(backdrop)` on the page and pass the same backdrop to the glass components over it (`KelvinDock`, `KelvinCompactBar`, `KelvinSheet`, `KelvinDialog`, `KelvinToast`, `KelvinTip`).
- **Web**: load `tokens.css`, `bundle.css` and `bundle.js`, add `class="k-glass"` to glass surfaces and `data-k="press|switch|slider|dock|glass"` to interactive ones, then call `Kelvin.mount()`. `Kelvin.setGlass(level)` sets the level for the page.
- The Glass level and Reduce motion live in each app's Appearance settings and persist per device.
