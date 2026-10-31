# Ratio 7.2.0: interaction and motion teardown

Our own notes from using Ratio 7.2.0 on an Android 17 emulator (October 2026) and from screen recordings of it on a phone. This is a behavioural study: we used the app and measured what we saw. We did not decompile or copy its code or assets. The reference build stays outside git in `reference/ratio/`; screenshots are in the local-only `docs/ratio-reference/`.

## Home

| Interaction | What Ratio does | Eigen |
| --- | --- | --- |
| Rest | Drawer names, heavy weight, centred (or left with a glyph per drawer). Notification banner card at the top. Search pill at the bottom. | Same, plus our centred clock. |
| Tap a drawer | Opens in place into a 5-column grid of vertical cards; the drawers below slide down. Other drawers stay as they are. | Same. Several drawers can be open at once. |
| Pull down (anywhere, at the top) | Expands every drawer. Pull down again: collapses every drawer. | Same, with a rubber-band pull and a threshold. |
| Swipe up | An A–Z app drawer rises over a heavier wallpaper blur, with letter headers and a scroll thumb. | Same; "All apps" is no longer a drawer. A short line above the search pill bends into an up chevron as you pull. |
| Hold empty space | White pill actions appear at the finger: Add drawer, Settings, Wallpaper. Everything else dims. | Same, as Kelvin pills anchored at the touch point. |
| Hold an app | The card stays lit while everything else dims; a stack of white pills appears above it: Remove, Dock, Select, Usage goal, App dashboard. | Same pattern: App dashboard, Favourite, Move, More. |
| Unread | A short accent dash after the drawer name when a collapsed drawer has notifications; a dot on the card when open. | Dash by default; Dot, Colour (name in the accent) or Off in Settings. |
| Usage | With usage access, each card shows today's minutes under its name; list layout shows minutes per drawer. | Minutes under the card's name. |

## App dashboard

A full-screen page, not a sheet, laid out as a bento grid over the blurred wallpaper:

- App name (large) and version.
- Storage used | Data used.
- Usage time today as hourly bars, with the day's maximum. Before usage access is granted: "Start showing stats".
- Info and Uninstall tiles; Icons tile; a card-style tile that cycles **Classic → Marker → Bright highlight** and previews the card colour; Lock, Mute and Expand (premium).
- A full-width Close pill.

Card styles: Classic is the faint frosted fill; Marker fills the card with the accent (yellow in Ratio, Kesar in Eigen) with dark text; Bright highlight is a lighter grey fill.

## Conversations

Lists supported messaging apps with switches ("Supported apps"); only those feed the timeline. Eigen: an Apps button on Conversations opens the same choice.

## Motion (approximate)

The emulator uses software rendering and records only a few frames per second, so these are upper bounds:

| Motion | Duration |
| --- | --- |
| Open one drawer (cards fade and rise, drawers below slide) | ~300–450 ms |
| Expand all after a pull | ~400–600 ms |
| Pill menu appear | ~250 ms (scale and fade from the anchor) |
| App drawer rise | follows the finger, settles in ~300 ms |

These sit inside Kelvin's springs: `spatial` (0.76, 420) settles in about 400 ms, `gentle` (0.90, 260) for fades. Eigen uses those rather than fixed durations.
