# 1.0.0-rc1

Release candidate 1. Version code 4. Android 9+ (API 28). Development-signed with the same key as the betas, so it installs over beta 1–3 and keeps launcher data.

## New since beta 3

- **Contexts.** The top zone and starting category follow rules: days, a time window, headphones, charging, Do Not Disturb and media playing. First match wins, **everyday** is the fallback, and you can hold a context for a while. Rules are checked only while Home is visible; no background service and no new always-on permissions. See [CONTEXTS.md](CONTEXTS.md).
- **Top-zone items.** Clock, next alarm, agenda (asks for calendar access when added), media controls, Smartspacer (Android 10+), and any installed app widget. Several app widgets share a swipeable stack; hold a widget for options.
- **Settings sheet.** Quick settings (context, theme swatches, toggle tiles, dimming) that expands into the full settings page, with a visual context editor.
- **Dock.** A category bar that stretches to a round search button. Hold the button, or swipe up on the bar, for quick settings.
- **Material 3 Expressive.** A style like Liquid Glass that combines with any theme: tonal containers from the theme's accent, a pill indicator with spring motion, a shape-morphing search button, emphasized type. Optional wallpaper colors (Material You, Android 12+). An **Expressive** preset shows it off.
- **Smartspacer.** Client SDK 1.1.2 (Apache-2.0). This is the only library; release builds are R8-shrunk to about 1.6 MB.
- Home now closes any open settings dialog. The clock updates on the minute boundary. About shows the installed version.

## Verification

All on one Android 17 (API 37) emulator, x86_64.

- Android lint: 0 errors.
- JVM: 10 category and 17 context-rule checks.
- On-device engine checks (debug build): 102 pass, including context backup round trips, malformed-context rejection, legacy header/media migration and the Expressive preset.
- UI suites against the R8-shrunk `qa` build (release code, debuggable so the scripts can read prefs), each starting from cleared app data:
  - `smoke.py`: navigation, search, custom categories, two-category mode, palettes, handedness, dock gestures.
  - `extended-smoke.py`: title-strip and alphabet scrubbing, launching apps, favorites persistence, assignment and merge.
  - `wallpaper-smoke.py`: photo import through the document picker, persistence, dimming, system/solid/saved modes, JSON theme import.
  - `theme-smoke.py`: Wave/Liquid Retro, effect and icon controls, icon-pack picker, theme export/import and backup/restore through the document picker, shared package import.
  - `a11y-smoke.py`: every clickable control is labeled and at least 48dp on home, quick settings, all settings, the context editor and search.
- The signed `dist/72F-Launcher-1.0.0-rc1.apk` installs over the debug build and starts without errors.
- Fixed during RC hardening: Home now closes open dialogs; 48dp targets for chips, the date shortcut and the context title; clock minute alignment. `wallpaper-smoke.py` and `theme-smoke.py` last ran on the build just before the 48dp layout fixes; the other three suites and the engine checks ran on the final code.
- Visual checks during development: Retro, paper, Wave, Liquid Retro and Expressive (light and dark), settings sheet, context editor, widget stack, Smartspacer install hint.
- Signature: same certificate as beta 3 (certificate SHA-256 `8c5cdb0d…b34e81`; APK SHA-256 in `dist/SHA256SUMS`); installs over the debug build without data loss.

## Not verified (release gate)

These need a physical device or a different Android version before 1.0.0 ships:

1. Android 9–16. Only API 37 was run, so the API 29+ guards for Smartspacer and the Android 12+ color paths have not executed on older systems.
2. Physical devices and OEM skins: gesture navigation, battery management, launcher-role behavior.
3. Headphones, live media sessions and the agenda with a signed-in calendar.
4. Real Smartspacer cards (only the not-installed path and SDK inflation were exercised).
5. TalkBack in use. The a11y suite checks labels and touch targets only.
6. Widget theming: Android 16+ refused the color override on the emulator; widgets keep their own colors there.

## Known limits

Settings are English-only. Category order is fixed. Custom folders are flat categories. Work-profile availability depends on device policy. App widget IDs are device-local, so a restored backup skips widgets from another phone.
