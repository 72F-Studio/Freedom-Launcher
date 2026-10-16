# 1.0.0-beta1

## Included

- Native Metro layout: large lowercase category titles, black/white/cyan palette, quiet typography.
- Horizontal list/title swipes, bottom category seekbar, side alphabetical scrubbing.
- Favorites home (20 maximum), All apps, automatic categories, persistent manual assignments.
- Custom flat categories/folders, merge, hide, rename, delete; categories-off fallback.
- App search, launch, app details, system-confirmed uninstall, favorites ordering.
- Four palettes, JSON theme import/export, title sizing, physical left/right scrubber preference.
- Clock/date and optional Android media-session controls in the adjustable header.

## Distribution

The APK is debug-signed, version 1.0.0-beta1, targeting Android API 35 with a minimum of API 28. It needs no network access. The source and Gradle wrapper are included in the workspace. This is a sideload beta candidate, not a store release.

## Remaining coverage

Real-device/OEM behavior, work profiles under enterprise policies, live third-party media players, and TalkBack require follow-up testing. Categorization uses local heuristics; corrections are supported. Settings are English-only. No arbitrary widgets, nested folders, wallpaper images, or imported font binaries in this beta.

## Verified in this workspace

- `:app:assembleDebug` and `:app:lintDebug` pass. Lint has zero errors and two SDK-version advisories (compile/target API 35).
- 10 standalone category, normalization, manual override, and merge-resolution checks pass.
- Emulator checks pass: initial home, list and title swipes, category slider, alphabet scrubber, app search/launch, favorites persistence across process restart, custom category creation/deletion/assignment/merge, categories-off fallback, palette switching, handedness.
- Screenshots inspected for home, all apps, search with keyboard, and light palette.
- APK signature verification passes (v2 signing).
- Theme import reaches Android's document picker, but the file selection/round-trip was not completed. Theme import/export and live media remain manual beta checks.
