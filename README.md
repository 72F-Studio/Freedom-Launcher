# 72F Launcher — v1.0.0 RC2

An original, native Android launcher: Retro typography with horizontal categories and vertical app lists. Android 9+ (API 28). No internet permission, ads, or analytics. The only library is the optional Smartspacer Client SDK, loaded on Android 10+.

## Install

Install `dist/72F-Launcher-1.0.0-rc2.apk`. Open **72F Launcher**: a five-step tour sets it as Home, picks a look, explains the gestures, sets up the top zone and contexts, and offers optional media and calendar access. Skip it anytime; replay it from settings. Later, hold the round search button for quick settings. Android's Home app setting lets you switch back anytime.

This beta uses a development signing certificate. Keep that certificate for subsequent beta updates; a production certificate will require a separate migration/reinstall. It is not a Play Store release.

## Controls

- Swipe horizontally across the app list, tap a category title, or drag the bottom slider to change categories.
- Scroll vertically; use the side alphabet to jump within a category. Settings switches its side.
- Search at the bottom searches every launchable app, including accessible work-profile apps.
- Hold an app to add/remove a favorite, move it to a category, open app info, or uninstall. Favorites supports up to 20 apps, with move-up/down actions.
- Home returns to Favorites. Returning from another app preserves the active category.
- Settings supports category creation, visibility, merging, and custom category renaming/deletion. Disable categories to retain only Favorites and All apps.
- The dock is a category bar plus a round **search** button. Tap or scrub the bar to switch categories. Hold the search button, or swipe up on the dock, for **quick settings**: active context, theme swatches, Metro toggle tiles and wallpaper dimming. Swipe the sheet up for **all settings**; drag down or press Back to collapse.
- Top zone (header): off or 10–40%, filled by the active context. Media controls use Android media sessions and optional notification access.

## Contexts

The top zone and starting category follow what you're doing. A context is a rule (days, a time window, headphones, charging, Do Not Disturb, media playing) and a result (which category Home opens on, top-zone height, and an ordered list of items: clock, next alarm, agenda, media controls, or any installed app widget). The first matching context wins; **everyday** is the fallback. Contexts are checked only while Home is on screen, with no background service. Hold a context for 1 hour, 3 hours or until morning from quick settings. See [the design notes](docs/CONTEXTS.md).

App widgets use Android's widget bind prompt and the provider's configuration screen. Several widgets in one context share a swipeable stack with page dots; hold a widget to remove it, change the top-zone height, edit its context or reopen its settings. On Android 12–15, widgets that use system color resources follow the 72F palette. Android 16+ refused this color override on the test emulator, so there widgets keep their own colors. The agenda item asks for calendar permission only when added.

**Smartspacer** (Android 10+): add the Smartspacer item to a context to show its At a Glance targets and complications in the top zone, tinted to the theme. Install [Smartspacer](https://github.com/KieronQuinn/Smartspacer) separately; the first card asks for its permission. Without it, the item shows an install hint.

## Wallpapers

Open **Settings → wallpaper**. Choose a photo through Android’s document picker, follow the phone’s wallpaper, or return to a solid background. Photo imports are stored privately, centered to fill the screen, and restored after restarting. Dimming adjusts live from 0–90%, with extra shading toward the bottom. Images up to 32 MB are accepted and decoded at a maximum dimension of 2048 pixels. The original file is unchanged; no broad photo-library permission is required.

## Themes

Seven built-in looks: four Retro palettes, **Wave**, **Liquid Retro**, and **Material 3 Expressive**. Expressive takes its colors from the wallpaper's Material You palette on Android 12+ (light and dark follow the system), with an M3 baseline palette on older versions. It uses tonal containers, emphasized type, an M3 pill indicator in the dock, a rounded-square search button that morphs while pressed, and spring motion. The Wave look uses a horizontal icon/category strip, vertical app lists, glowing selection, original animated waves, and particles. Favorites remains Home in every theme.

**Settings → appearance & themes** controls presets, imports/exports, typography, icons, and effects. Themes use versioned JSON or portable `.72ftheme` ZIP packages with wallpaper, custom fonts, and per-app image icons. Third-party ADW/Nova-compatible icon packs use installed resources and fall back to system icons for unmapped apps. Theme exports embed the selected photo and custom assets; an installed icon pack is referenced by package name.

Blur, translucent glass surfaces, and liquid glass styling can be mixed with either layout. Android 12+ blurs the launcher backdrop with RenderEffect; Android 9–11 uses a bounded software fallback for imported photos. Android's live system-wallpaper blur depends on device support. Glass follows the effect stack of [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Kyant, Apache-2.0). On Android 12+ each panel replays the backdrop live into its own RenderNode and runs it through vibrancy (1.5× saturation for liquid, 1.25× frosted), a Gaussian blur matched to the background's own blur, and, on Android 13+, an AGSL lens that bends the edges with seven-tap chromatic dispersion on larger panels. A surface tint keeps text legible, an additive, angle-shaded rim catches light, and the elevation shadow is kept faint so it doesn't show through. Android 9–11 uses one shared, diffused bitmap with the same vibrancy, tint and rim, without refraction. **Settings → glass** (also in quick settings and effects) tunes blur, darker ↔ lighter tint and refraction strength live; values are saved in the theme. Live system wallpaper cannot be sampled; choose a photo inside the launcher for refraction. See [the theme creator guide](docs/THEMES.md) and `themes/` for ready-to-import packages.

All launcher controls use a complete vector icon set: original **Retro**, original **Wave**, or [Google Material Icons](https://github.com/google/material-design-icons). Switch independently under **launcher icon set**. Theme packages can override individual controls through `uiIcons`; app icons use separate settings.

## Backup & restore

**Settings → backup & restore** exports `.72fbackup.zip` files with favorites, category settings/assignments/merges, appearance, imported assets, saved photo, header/media preferences, and handedness. Restore validates the entire package, shows a replacement summary, then applies it. Apps, icon-pack applications, Android permissions, and the default-home role must be installed/granted on the destination phone separately. Keep exported backups in a safe location: they include your selected photo and app organization.

## Build

Use JDK 21 (newer JDKs such as Android Studio's bundled JDK 25 are rejected by this Gradle version) and Android SDK 35 / Build Tools 35.0.0. Set `ANDROID_HOME` or your local `sdk.dir` in untracked `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
./scripts/test.sh
```

`scripts/engine-test.sh` builds and installs the dependency-free Android checks and a local test icon pack. Run against a dedicated emulator; UI smoke scripts exercise native document pickers and modify launcher preferences.

`./scripts/test.sh` uses `JAVA_HOME` (or Android Studio's bundled Java).

UI suites drive an installed build over ADB: `smoke.py` (navigation, search, categories, settings, dock gestures), `extended-smoke.py` (scrubbers, launching, persistence, merges), `wallpaper-smoke.py`, `theme-smoke.py` (theme/backup round trips through the document picker) and `a11y-smoke.py` (labels and 48dp touch targets). **Each suite clears 72F's app data first** (`pm clear`), so run them only on a test emulator. Set `ANDROID_SERIAL` to pick the device.

Release APK (R8-shrunk), signed with the same development key as the betas so it installs over them:

```sh
./gradlew :app:assembleRelease
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out dist/72F-Launcher-1.0.0-rc2.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## Implementation

- `MainActivity`: lifecycle, native Metro views, gesture handling, app discovery through LauncherApps, profiles, settings and local persistence.
- `CategoryRules`: independent local categorization heuristics, normalization, and merge resolution. Manual assignments take priority; merged categories also apply to newly installed apps.
- `ThemeEngine`, `BackupStore`, `IconPacks`: bounded, data-only theme packages, portable settings snapshots, and installed icon-pack resource mapping.
- `ThemeEffects`, `Ps3Backdrop`: backdrop blur, glass surfaces, and lifecycle-aware procedural waves.
- `MetroArt`: original vector controls, navigation glyphs, and wallpaper rendering.
- `ContextRules` / `ContextStore`: pure first-match rule evaluation (JVM-tested), JSON persistence and permission-free signal readers.
- `SettingsSheet`: two-stage draggable bottom sheet (quick settings → full page) with a page stack for the context editor.
- `MediaAccessService`: exposes the notification-listener component required for Android active media-session discovery. No notification contents are processed or stored.

The launcher UI is plain Android Views; the Smartspacer SDK (and the AndroidX/Kotlin libraries it needs) is the only dependency. Release builds use R8 so the APK stays small (about 1.6 MB). App discovery runs off the UI thread. Package callbacks refresh app lists after install/removal. App identity includes launcher activity and user profile.

## Reference boundary

GraceLauncher and focus-launcher were reviewed through public documentation for behavior and architecture ideas. Neither repository was cloned; no source, assets, or classification datasets were imported. The implementation here was written independently.

## Known limits

Classification is heuristic and will need corrections for niche apps. Custom folders are flat categories. Category order is fixed (Favorites, built-ins, custom, All apps). The app list offers monochrome icons, original-color icons, and text-only layouts. Theme editing is through versioned JSON, asset packages, presets, and effect controls. Work profile availability depends on Android policy. RC2 was verified on an Android 17 emulator only: physical devices, OEM gesture navigation, Android 9–16, live TalkBack use, live media sessions, headphones and real Smartspacer cards still need testing. See `docs/RC2-NOTES.md` for what was checked and the release gate.
