# 1.0.0-rc2

Release candidate 2. Version code 5. Everything in [RC1](RC1-NOTES.md), plus onboarding and liquid-glass fixes. Same signing key, so it installs over the betas and RC1 and keeps launcher data.

## New

- **First-run tour.** Five skippable steps over the live launcher, drawn in the current theme:
  1. Welcome, with a "make 72F your home screen" action.
  2. Pick a look: palette swatches, Expressive / wallpaper colors / glass / liquid glass, app icon style. Changes apply live and the tour keeps its place.
  3. How it works: categories, scrolling and the alphabet, holding apps, the dock.
  4. The top zone: what everyday shows (clock, + media, + agenda, nothing) and optional starter contexts (morning, work, headphones, night).
  5. Optional access: media controls, calendar, default home.
- Shown once on first run; if the app is closed mid-tour it shows again. Replay it from **settings → replay the tour**. Back steps backward; on the first step it closes the tour. Existing installs that already saw the beta welcome don't see it automatically.

## Liquid glass fixes

The shared-backdrop rewrite (one scene texture, AndroidLiquidGlass lens) is kept. Fixed:

- **Frosted glass is frosted again.** Subtle and frosted glass now diffuse by strength; only liquid glass is a clear lens.
- **Glass matches the background's blur.** The lens texture includes the backdrop's RenderEffect blur on Android 12+, so panes no longer look sharper than what surrounds them. Android 9–11 already draws a pre-blurred wallpaper and isn't blurred twice.
- **No per-frame allocation while waves animate.** Recapture is about 6 fps (was 12.5) at up to 720 px for moving backdrops (1280 px for still ones), into two alternating, reused bitmaps and reused blur buffers. The render thread never samples a bitmap being rewritten.
- **Pill and circle chrome.** Non-liquid glass borders follow the dock's pill and the search circle instead of being clipped at a fixed 12 dp radius.
- Removed unused fields from the GPU lens class; README and code comments now describe the real Android 9–12 path (diffused texture, no refraction) instead of the removed mesh warp.

## Verification

Android 17 emulator, x86_64.

- Lint: 0 errors. JVM: 10 + 17 checks. On-device engine checks: 102.
- UI suites on the R8 `qa` build, each from cleared app data (each one sees and skips the first-run tour): `smoke.py`, `extended-smoke.py`, `wallpaper-smoke.py`, `a11y-smoke.py` and `theme-smoke.py` all pass.
- `a11y-smoke.py` now also audits all five tour steps (labels, 48 dp targets).
- Visual: Liquid Retro and frosted glass over a photo; the full tour; Expressive + liquid glass applied from inside the tour.

The release gate from RC1 (Android 9–16, physical devices, TalkBack in use, headphones, live media and Smartspacer) still applies.
