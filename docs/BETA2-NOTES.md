# 1.0.0-beta2

## Visual polish

- Split clock/date header and subtle accent lighting on solid backgrounds.
- Refined Metro typography, quieter captions, consistent app-row spacing.
- Monochrome app icons by default; original-color and text-only modes available.
- Original vector category rail, circular search button, and playback controls.
- Typographic settings sheets with contextual descriptions.
- Gentle press feedback, category transitions, and list-edge fading.

## Wallpapers

Choose a photo, follow Android's wallpaper, or use a solid background. Imported images are copied into private app storage, downsampled with orientation handling, and centered to fill the screen. A live 0–90% dimming control adds a stronger gradient at the bottom. Saved images survive process restarts and can be selected again after switching to solid/system mode. No storage permission or network access is added.

## Distribution

Android 9+. Development-signed APK; version code 2. Installs over beta 1 signed with the same development key and preserves preferences. No GPL project code or assets were imported.

## Limits

Still a sideload beta. Live media players, enterprise work profiles, OEM launch animations, and TalkBack need physical-device coverage. Settings remain English-only. No arbitrary widgets, nested folders, custom font binaries, or manual wallpaper crop positioning.

## Verification

- Final APK builds and passes Android lint (zero errors; five non-blocking warnings).
- 10 category/normalization/merge logic checks pass.
- Core emulator smoke checks pass: category swipes, search, custom categories, two-category fallback, palettes, handedness.
- Wallpaper checks pass: actual document-picker import, saved private image, process-restart persistence, adjustable dimming, and system/solid/saved-photo modes. Home, search, light theme, and wallpaper rendering were visually inspected.
- Final APK signature verified.
- Optional theme-file import/export round-trip remains unverified; the test emulator was being used by another app during that check.
- Cold-start splash colors and full-color wallpaper decoding were refined after the visual pass and included in the final build/lint checks.
