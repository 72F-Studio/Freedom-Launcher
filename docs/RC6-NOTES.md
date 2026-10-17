# 1.0.0-rc6: motion and layout

Release candidate 6, version code 9. Same features as [RC5](RC5-NOTES.md); this RC is about feel. Motion and layout behavior draw on [GraceLauncher](https://github.com/Galaxy-rio/GraceLauncher) (GPL-3.0), whose behavior and numbers were studied and reimplemented independently; no code was copied.

## Motion

A small physics engine (`Motion.java`) drives everything: damped springs on the display's frame clock that keep the finger's velocity when interrupted, shared presets, and graded haptics. All of it respects Android's "remove animations" setting and the theme's motion switch.

- **Category paging follows the finger.** It rubber-bands past the first and last category, and gives a threshold haptic when a release would commit. On release it carries the velocity: the old list slides out and hands over at a third of the width, the new list springs in from the other side, and its rows cascade in (18 ms stagger). A short release springs back.
- **Search button.** On press it squashes to 0.92 and rubber-bands toward the finger (0.42 gain with 72 dp of softness), stretching along the drag. It springs back on release (0.58 / 420 for position, 0.6 / 650 for scale). Expressive themes also morph its corners.
- **Alphabet rail.** Letters near the finger bow outward (Gaussian falloff with σ = 82 dp, on a 0.86 / 700 spring). A 46 dp accent bubble follows the finger and scales with the wave. There is a light tick per letter. The rail is anchored low, near the thumb.
- **App launch.** The app zooms out of a snapshot of the icon (`makeThumbnailScaleUpAnimation`), with an icon splash on Android 13+. Home pushes in toward the icon and dims while the app opens, and eases back in when you return. On the Android 17 emulator the system plays its own open transition for third-party launchers, so the push-in is what you see there; the icon zoom applies where the platform honors it.
- **Rows.** Pressing a row fades in a soft rounded highlight and sinks the row slightly on a bouncy spring. The old list selector, which could stay stuck after a swipe, is gone.
- **Sheets and dialogs.** The settings sheet settles on a spring seeded with the flick velocity. Menus and dialogs rise on a spring while their dim fades in, and slide away on exit.
- **Search and the tour.** The search field drops in and results cascade; tour pages spring between steps.
- **Dock indicator.** The selected-category indicator springs between categories and keeps its speed when you scrub quickly.

## Layout

- **Clock and date.** The clock is large and light (64 sp) with a single date line under it ("sat, 17 october · context"). The pair is anchored to the bottom of the top zone, just above the categories; the divider and the two-line date are gone.
- **One left edge.** Clock, date, category titles and app icons line up at about 28 dp.
- **Rows.** 56 dp tall, 40 dp icons, 20 sp regular-weight labels. Titles default to 48 sp, down from 60.
- **Icons.** Interface icons default to the Material set. Search uses one thin round magnifier everywhere, replacing the old square glyph.
- **Search results.** The first result is bold, because Enter opens it.
- **Existing installs.** Themes already saved keep their stored sizes and icon set; re-picking a preset applies the new defaults.

## Verification

All on the Android 17 emulator.

- Lint: no issues. JVM tests: 37. On-device engine checks: 115.
- All six UI suites pass on the R8 `qa` build. One test was updated: `extended-smoke.py` now scrubs the rail where it is actually drawn, near the thumb.
- Motion was checked from screen recordings, frame by frame:
  - paging, both drag and flick, with the handoff and row cascade;
  - the rail bubble;
  - the settings sheet rising;
  - row highlight and launch;
  - return to home.
