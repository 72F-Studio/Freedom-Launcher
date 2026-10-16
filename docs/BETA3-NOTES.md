# 1.0.0-beta3

## Included

- Retro palettes, Wave console-inspired layout, and Liquid Retro presets. Favorites remains Home; existing category controls and two-axis navigation work across themes.
- Theme-specific launcher control icons: complete original Retro/Wave vector packs and an optional Google Material Icons pack. Categories, Favorites, arrows, close/search, menu, and media controls use drawable assets rather than characters. App icon packs remain a separate setting.
- Theme engine schema 2: colors, metrics, layout, backdrop, animation, icon set, custom control/app images, custom fonts, imported wallpaper, blur, glass, and refraction. JSON remains supported; ZIP packages embed assets. Export uses the currently selected photo and app icon mode.
- Actual local glass blur and refraction. Android 13+ uses an original AGSL rounded-rectangle lens shader with dispersion and rim lighting. Older Android uses bounded software blur and bitmap-mesh displacement. Imported photos are copied privately, so no wallpaper screenshots or broad storage permission are required.
- Backup/restore archives include favorites, category settings, assignments/merges, theme assets, saved photo, header/media preferences, and handedness. Validation completes before settings change; restore shows a replacement summary.
- Removed promotional captions and category counts beneath titles. Settings options use their titles alone; onboarding, empty states, and destructive actions retain instructions where needed.

## Rendering and portability limits

Glass samples the app-owned background rather than other apps or the system's live wallpaper. Choose a photo inside 72F for actual image refraction. System-wallpaper blur depends on Android compositor support. Glass capture is capped at 480px width; animated backdrop sampling is throttled to about 6fps. Whole-background native blur requires Android 12+; older versions blur imported photos in software. Foreground text and controls remain sharp. Rendering performance and battery use still need physical-device testing.

Third-party app icon packs support standard ADW/Nova-style appfilter mappings. Dynamic calendar icons and every proprietary extension are outside this beta. Missing packs fall back to original icons; pack APKs are not exported. System wallpaper, apps, Android permissions/default-home role, and active media sessions are not included in portable archives. Work-profile IDs may differ on another device. Arbitrary widgets and nested folders remain outside v1.

The Material set includes the Google Apache-2.0 license and attribution in APK assets and under Settings → about → open-source notices. Retro/Wave icons and waves are original. No GPL launcher code or assets were imported. Octopi's public repository currently contains an issue tracker and no source files ([public repository](https://github.com/octopiappfactory/octopi-launcher)); its code was not reused. AGSL behavior follows the [Android RuntimeShader API](https://developer.android.com/reference/android/graphics/RuntimeShader).

## Verification

- Final application, test runner, and icon-pack fixture build. Android lint: zero errors, ten warnings (SDK targets, third-party resource reflection, API annotation guidance, synchronous restore rollback, localization).
- 10 category/normalization/merge JVM checks pass.
- 90 Android integration checks pass: all 63 built-in launcher icons resolve, real AGSL shader compiles, standard icon-pack resources load, custom control/app assets import, snapshots embed photos, theme/backup archives round trip, invalid paths/schema/data are rejected, and invalid backup input leaves settings intact.
- Native document-picker checks pass: theme export/import embeds the photo, backup/restore restores settings and assets, effect controls work, and installed app/control icon selectors apply. Wave and Liquid Retro were visually inspected; caption-free category titles and settings sheets render correctly.
- APK signature verified (v2 signing).
- Core category/search/custom-category/two-category/palette/handedness UI checks passed during beta 3 development.

## Distribution

Android 9+. Version code 3. Development-signed APK installs over earlier betas signed with the same key and preserves launcher preferences. This is a sideload beta candidate; physical-device, OEM gestures, accessibility, and live-media coverage remain beta testing tasks.
