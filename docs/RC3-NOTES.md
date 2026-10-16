# 1.0.0-rc3

Release candidate 3. Version code 6. Everything in [RC2](RC2-NOTES.md), plus a rebuilt glass pipeline and adaptive text legibility. Same signing key: installs over the betas, RC1 and RC2 and keeps launcher data.

## Liquid glass, rebuilt

Glass now follows the full effect stack of [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Kyant, Apache-2.0), not only its lens math.

- **Live GPU backdrop (Android 12+).** Each panel replays the wallpaper into its own RenderNode every frame. There is no bitmap readback, so animated backdrops stay smooth.
- **Effect chain:** vibrancy (1.5× saturation for liquid, 1.25× for frosted), then a Gaussian blur matched to the background's own blur, then (Android 13+) an AGSL edge lens. Larger panels add seven-tap chromatic dispersion.
- **Finish:** a surface tint for legibility, an additive rim lit at 45°, and an elevation shadow kept faint so it doesn't show through the glass.
- **Android 9–11:** one shared, diffused texture with the same vibrancy, tint and rim, without refraction.
- **New glass sliders** (Settings → glass, quick settings, effects): blur (auto or 0–40 dp), darker ↔ lighter tint, and refraction 0–100%. They apply live and are saved in the theme as `glassBlur`, `glassTint` and `refraction`, so exports and backups carry them.

## Text legibility

Measured on a busy illustrated wallpaper at 20% dimming, grey secondary text was at 1.4–1.6:1 contrast; WCAG AA asks for 4.5:1.

- **Auto (default):** measures the photo's bright and dark spots after dimming, or reads Android's wallpaper color hints for the phone wallpaper. It picks **Strong** when worst-case contrast would fall below 4.5:1, otherwise **Subtle**.
- **Subtle:** a soft halo (dark behind light text, light behind dark text), with grey text lifted toward the main text color.
- **Strong:** a stronger halo and 15% extra dimming. **Off** is also available.
- **Coverage:** category titles, app labels, clock, date, alarm, agenda, media, the empty state, the alphabet rail and search. It applies only over photos or the phone wallpaper.
- Light themes get an opaque-enough dock surface over photos.
- Setting: Settings → text legibility, or the wallpaper menu. It is included in backups and validated on restore.

## Verification

Android 17 emulator, x86_64.

- Lint: 0 errors. During this round lint caught two calls that would have crashed Android 9–12: `RecordingCanvas`/`RuntimeShader` use, and `WallpaperColors.getColorHints`. Both are fixed and guarded.
- JVM: 10 category, 17 context-rule and 10 legibility checks.
- On-device engine checks: 110 pass on the final code. These compile the new lens and highlight shaders on the device and validate the new glass and legibility fields.
- UI suites on the R8 `qa` build (each from cleared app data): `smoke.py`, `extended-smoke.py`, `wallpaper-smoke.py`, `a11y-smoke.py` and `theme-smoke.py` all pass.
- Visual checks over an illustrated forest wallpaper:
  - liquid and frosted glass, before and after the rebuild;
  - the sliders at blur 0 and 21 dp, 66% darker, 77% lighter and 100% refraction;
  - legibility off vs auto for Retro, Liquid and Paper.

The release gate from RC1 still applies: Android 9–16, physical devices, TalkBack in use, headphones, live media and Smartspacer. Glass refraction and the live backdrop in particular need checking on real GPUs.
